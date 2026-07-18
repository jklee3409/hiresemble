# P0 계약 결정 제안서

- 문서 상태: 제품 소유자 검토 준비(`READY_FOR_OWNER_REVIEW`)
- 기준일: 2026-07-18
- 기준 설계: [전체 시스템 설계](system-architecture.md), [구현 계획](implementation-plan.md)
- 기준 명세: [기능](../spec/functional.md), [API](../spec/api.md), [DB](../spec/db.md), [페이지](../spec/page.md), [기술 스택](../spec/tech_stack.md)

이 문서는 구현자가 추측하지 않도록 P0 계약의 권장 기준선을 제안한다. 제품 소유자 승인 전에는 아래 내용을 확정 계약으로 보지 않으며, `docs/spec/**`, 비즈니스 코드, migration, 설정, API와 UI에 반영하지 않는다. 승인된 결정은 기준 명세에 한 번만 반영하고 계약 검증을 다시 통과시킨다. 명세 동기화가 끝나면 이 문서는 구현 기준선이 아니라 결정 기록 또는 archived proposal로 관리한다.

## 1. 작업 범위와 판단 기준

### 1.1 현재 구현 상태

- Backend는 `HiresembleApplication`, 초기 의존성·설정과 pgvector 확장용 `V1__enable_extensions.sql`만 있다.
- Frontend는 `RouterView`, 빈 route 배열과 공통 plugin bootstrap만 있다.
- PostgreSQL/pgvector, MinIO, 선택적 Mailpit의 로컬 인프라가 구성돼 있다.
- 비즈니스 Controller, DTO, domain, repository, workflow, page, API client와 비즈니스 테스트는 아직 없다.
- 따라서 이번 작업은 기존 구현을 변경하는 작업이 아니라 구현 전 계약 충돌과 누락을 닫는 승인 자료 작성이다.

### 1.2 P0의 목적

P0는 공개 HTTP, DB 무결성·수명주기, AI runtime, route·projection의 의미를 하나로 맞춘다. 승인 후 Backend·AI workflow·Frontend가 같은 enum과 DTO를 선언하고, migration 작성자가 nullable·owner·cascade·retry 의미를 추측하지 않게 하는 것이 완료 기준이다.

### 1.3 적용한 의사결정 원칙

1. Spring Boot 단일 인스턴스, PostgreSQL, S3 호환 Object Storage의 가장 단순한 MVP 구조를 유지한다.
2. PostgreSQL과 REST snapshot을 상태 원천으로 두고 SSE는 best-effort 전달에만 사용한다.
3. 사용자 소유권은 API owner join과 DB 복합 FK로 이중 강제하고, provenance와 개인정보 최소화를 함께 지킨다.
4. 중복 resource와 유료 호출을 막기 위해 idempotency와 비용 예약을 영속화한다.
5. AI는 version이 있는 고정·유한 workflow만 실행한다.
6. 공개 `AiQualityMode`, 내부 `ModelTier`, 검색 `ResearchQuality`, 결과 `SourceCoverage`를 다른 타입으로 둔다.
7. 명세의 핵심 사용자 여정과 AC-01~AC-13을 축소하지 않고, 미래 확장을 위한 빈 구조는 추가하지 않는다.
8. 기술적으로 닫을 수 있는 항목은 `RECOMMENDED`로 하나의 안을 선택한다. 개인정보 보존, 비용, 사용자 경고·복구처럼 제품 정책이 필요한 항목만 `OWNER_DECISION_REQUIRED`로 남긴다.

### 1.4 승인 경계

이 문서의 `RECOMMENDED`도 승인 전에는 제안이다. 제품 소유자는 8장의 질문을 결정하고 나머지 권장안을 함께 승인하거나 수정해야 한다. 승인 후 별도 작업에서 다섯 기준 명세를 먼저 동기화하고, 그 다음 OpenAPI·migration·코드·UI를 구현한다. 이번 문서 작성만으로 P0를 완료 처리하지 않는다.

## 2. D-01~D-18 결정 표

| ID   | 상태                      | 현재 충돌                                                                                                                                                     | 최종 권장안                                                                                                                                                                                                                                                                                                                                                                                                             | 선택 이유                                                                                      | 제외한 대안                                                                                                     | 영향 문서                                                                                 | 구현 영향                                                                   | 검증 기준                                                                                            |
| ---- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| D-01 | `RECOMMENDED`             | URL 추출 실패를 공고 업무 상태처럼 표현하지만 업무 상태는 3개뿐이다.                                                                                          | `JobStatus=IN_PROGRESS\|SUBMITTED\|CLOSED`와 `JobExtractionStatus=QUEUED\|EXTRACTING\|EXTRACTED\|MANUAL_INPUT_PROVIDED\|NEEDS_MANUAL_INPUT\|FAILED`를 완전히 분리한다. 수동 본문 저장은 `MANUAL_INPUT_PROVIDED`, 원격 추출 성공은 `EXTRACTED`; 추출 실패가 업무 상태를 바꾸지 않는다.                                                                                                                                   | 목록 filter·Scheduler·지원 이력과 수동 보완 CTA를 독립적으로 유지한다.                         | 단일 enum, 수동 입력도 자동 추출 성공으로 표시                                                                  | functional JOB-001/002, api jobs, db 5.2, page 7, design                                  | DB CHECK, Java/TS enum, job DTO와 badge                                     | 두 축의 모든 전이, 추출 실패 중 `IN_PROGRESS` 유지, Scheduler가 업무 상태만 변경하는 테스트          |
| D-02 | `RECOMMENDED`             | 공개 품질, 모델 등급, 검색 품질이 혼용되고 version source·질문 유형의 집합과 생성 경로가 다르다.                                                              | 공개 `AiQualityMode`, 내부 `ModelTier`, `ResearchQuality`, `SourceCoverage`를 분리한다. 자기소개서용 `CoverLetterVersionSource`와 면접 답변용 `InterviewAnswerVersionSource`를 분리하고 모든 source 값은 서버 command가 지정한다. 면접 답변 MVP 값은 실제 생성 경로가 있는 `USER_EDITED` 하나뿐이다.                                                                                                                    | 공개 계약과 운영 모델 교체를 분리하고 provenance 위조·생성 불가능한 값을 막는다.               | 단일 `QualityMode`, 자기소개서 source 재사용, provider/model명을 공개 입력으로 사용                             | functional CL/INT/SYS, api cover/interview/settings, db 2/7/8/9, page 8/9/12/13, tech 8/9 | OpenAPI, DB CHECK, Java enum, TS/Zod, ModelRouter                           | 네 계층 enum parity, 허용하지 않은 품질·source 입력 거부, HIGH_QUALITY gate 테스트                   |
| D-03 | `RECOMMENDED`             | 대부분의 DTO·nullability·길이·version과 Dashboard·download·Agent Run projection이 빠졌다.                                                                     | 4장의 공통 규칙·element DTO·endpoint matrix를 완전한 OpenAPI 기준선으로 사용한다. `page=0`, `size=20`, `1..100`; 동시 편집되는 aggregate command는 `version`; delete는 `version` query; credential/session command는 예외다. 전용 `GET /dashboard`를 추가한다.                                                                                                                                                          | Backend와 Frontend의 추측, 잘못된 집계, 409 입력 유실을 없앤다.                                | JPA entity 직렬화, 화면별 fan-out 집계, 모든 mutation optimistic update                                         | api 전반, db version, page 4~13, design                                                   | HTTP DTO·validation, projection query, TS/Zod, 409 UX                       | OpenAPI snapshot, 경계값·unknown sort·version 충돌, 모든 page action↔API matrix                      |
| D-04 | `RECOMMENDED`             | 공통 `user_id NOT NULL` 규칙과 child 정의가 다르고 cross-user FK를 DB가 막지 못한다.                                                                          | 글로벌 table을 제외한 모든 사용자 콘텐츠 row에 `user_id NOT NULL`과 `UNIQUE(user_id,id)`를 둔다. child·교차 참조는 `(user_id,parent_id)` 복합 FK를 쓴다. polymorphic `source_entity_id`만 typed owner lookup 예외로 둔다. PostgreSQL RLS는 MVP에서 제외한다.                                                                                                                                                            | application 실수 하나가 tenant 침범으로 이어지지 않게 DB와 service가 함께 방어한다.            | application 검사만 사용, 전면 RLS                                                                               | db 전반, api 404, tech 6/7, design                                                        | 모든 child·link·run·SSE·Object owner 경로                                   | 두 사용자 Testcontainers fixture, cross-user FK 실패, child UUID·vector·SSE·download 404             |
| D-05 | `OWNER_DECISION_REQUIRED` | 문서 파생 근거를 삭제할지 비활성화할지, 보존 version의 `SOURCE_DELETED` provenance 범위가 불명확하고 Object 삭제 outbox가 없다.                               | 문서는 즉시 soft delete해 404·presign 차단, text/chunk/Object는 물리 삭제, Object task는 outbox로 재시도한다. 참조된 evidence는 최소 tombstone 또는 snapshot으로 `SOURCE_DELETED`, 미참조 evidence는 삭제하는 안을 권장한다. 질문은 soft delete하고 answer version·verification link는 보존한다.                                                                                                                        | 개인정보 원문을 지우면서 과거 산출물의 출처 상태와 복구 가능한 Object 삭제를 보존한다.         | 모든 child cascade, raw 원문 영구 보존, 참조 여부와 무관한 전체 evidence 보존                                   | functional PROF-007/DOC-004/CL-004, db 1/4/6/12/13, api delete, page history              | tombstone 범위, soft delete, outbox, 보존 FK                                | 삭제 즉시 404, text/chunk 제거, Object 장애 재시도, 승인된 tombstone 정책별 과거 link 테스트         |
| D-06 | `RECOMMENDED`             | Object key 예시에 filename이 포함되지만 같은 명세는 입력 filename을 key로 쓰지 말라고 하며 `displayName` column도 없다.                                       | key는 `users/{userId}/documents/{documentId}/content`; `original_filename`과 `display_name`을 분리한다. 표시명은 1..255자, 제어문자·경로 구분자를 거부한다. download 응답은 URL과 5분 만료시각을 반환한다.                                                                                                                                                                                                              | key·로그·URL의 파일명 개인정보를 제거하면서 UI 표시를 보존한다.                                | sanitized filename을 key에 포함, filename을 storage locator로 사용                                              | api documents, db 4.1, tech 6.3, design                                                   | storage adapter, document schema·DTO                                        | 악성 filename/key, owner presign, 만료 재발급, 로그 민감정보 검사                                    |
| D-07 | `RECOMMENDED`             | `Idempotency-Key` header만 있고 scope·hash·TTL·replay DB 계약이 없다.                                                                                         | 5장의 영속 record를 사용한다. scope는 사용자+method+route template+target aggregate, hash는 canonical request와 upload SHA-256, 완료 TTL은 24시간이다. 동일 hash는 같은 status/DTO를 replay하고 진행 중·다른 hash는 각각 안정적인 409를 반환한다.                                                                                                                                                                       | 재전송으로 인한 중복 row·run·유료 호출을 동시에 차단한다.                                      | JVM cache, business unique만 의존, 원문 request 저장                                                            | api 11과 모든 비용 command, db, tech 8~10                                                 | 공통 idempotency filter/service/table                                       | 동시 20요청에서 resource/run 1개, 재시작 replay, hash mismatch, TTL cleanup                          |
| D-08 | `RECOMMENDED`             | queue 유실, claim/lease/cancel, attempt, WAITING_USER, retry identity와 SSE가 불완전하다.                                                                     | DB claim·reconciliation, 15초 heartbeat·60초 lease·30초 reconciliation, cooperative cancel을 사용한다. 자동 재시도 2회는 최초 포함 최대 3 attempt이고 모델 승격도 차감한다. terminal retry는 lineage를 가진 새 run, WAITING_USER는 같은 run을 재개한다. cancel terminal 반영과 resource의 마지막 안정 상태 복원은 원자 처리한다. SSE는 snapshot-first이고 replay하지 않는다.                                            | at-least-once 실행에서도 중복 반영·영구 RUNNING·processing 상태 유실을 막는다.                 | terminal run 재활성화, in-memory queue/SSE를 원천으로 사용, 무제한 retry                                        | functional SYS, api Agent Run, db 2/9, tech 8/10, page 12                                 | run/step schema, dispatcher, retry API, resource compensation, SSE client   | restart/claim race, stale lease, cancel 후 stable resource, 새 run retry, hash reuse, SSE reconnect  |
| D-09 | `OWNER_DECISION_REQUIRED` | UI 필수 표시, 동시 예산, 가격 version과 embedding/search 비용 범위가 없다.                                                                                    | chat·embedding·search를 모두 versioned price로 계산하고 일일 ledger+reservation으로 원자 reserve/settle/release한다. run detail은 요청 품질, 사용 tier, 예상·예약·실제 비용, duration, retry/cancel/required action을 제공한다. 구체 금액과 일일 reset 기준은 제품 승인을 받는다.                                                                                                                                       | 동시 run이 한도를 넘거나 비용 일부가 누락되는 일을 막고 사용자가 비용·재시도를 판단하게 한다.  | 단순 합계 조회, chat token만 기록, mutable price                                                                | functional SYS-001/003, api Agent Run/settings, db 9, page 12/13, tech 9                  | price catalog, ledger/reservation/usage, run projection                     | 동시 reserve, 고정 price 재현, terminal release, 검색·embedding 합계, 429 비재시도                   |
| D-10 | `OWNER_DECISION_REQUIRED` | active cardinality, TipTap 형식·글자 수, partial success, 생성 FactCheck와 재검증, `WARNING` 최종화가 없다.                                                   | 공고당 active `DRAFT\|FINALIZED` 최대 1개, archived history는 허용한다. allowlist TipTap JSON+서버 파생 plain text/count를 저장한다. 문항별 atomic 결과를 보존하고 current version별 fresh verification을 요구한다. `PENDING\|FAILED`는 차단하고 `WARNING`은 명시 확인 후 허용하는 안을 권장한다.                                                                                                                       | XSS·글자 수 불일치·상태 우회·부분 실패 재과금을 막는다.                                        | raw HTML, client count 신뢰, 전체 결과 rollback, generic PUT status                                             | functional CL-001~006, api 6, db 6, page 8/11, design                                     | content JSONB/text, active unique, generation/verification/finalize command | XSS fixture, count parity, current 경쟁, partial retry, stale verification, finalization 상태 테스트 |
| D-11 | `RECOMMENDED`             | preparation은 research ID 하나지만 DB는 COMPANY/INTERVIEW를 나누고 source 부족·질문 provenance가 없다.                                                        | 준비 요청당 combined research run 1개와 question set 1개를 만든다. query/source에 topic을 두고 question↔source N:M link를 둔다. `SourceCoverage`는 rubric으로 계산하며 `LIMITED\|NONE`도 성공할 수 있다. standalone 회사 조사 API는 MVP에서 추가하지 않는다.                                                                                                                                                            | 기존 단일 ID 계약과 provenance를 맞추고 provider 장애와 출처 부족을 구분한다.                  | public research run 2개, JSON ID만 저장, 부족 결과를 실패 처리                                                  | functional INT-001~004, api 7, db 7/8, page 7/9/11, tech 8                                | research cardinality, source link·coverage projection                       | source 부족 성공, retry lineage, 같은 사용자/source run FK, 질문 sourceRefs 테스트                   |
| D-12 | `OWNER_DECISION_REQUIRED` | mock 생성 route·client request ID·동기 turn 경계·feedback pending·비용 연결이 없다.                                                                           | 생성 route는 `/jobs/:jobId/interview/mock/new`; create는 idempotency key를 쓴다. start/message는 `clientRequestId`+session `version`과 turn table로 replay한다. 동기 turn은 Agent Run 없이 usage를 session/turn에 연결하고, complete만 feedback Agent Run을 만든다. timeout·호출·비용 상한은 제품 승인을 받는다.                                                                                                        | timeout·double click·다중 tab 중복 메시지/비용을 막고 session 완료와 feedback 완료를 분리한다. | UI 잠금만 사용, turn마다 Agent Run, feedback까지 IN_PROGRESS 유지                                               | functional INT-005/006, api 8, db 8, page 7/10/11, tech 8/10                              | turn request unique/CAS, sync executor, feedback status/run                 | 동일 ID replay, 다른 body 409, multi-tab, timeout, usage, COMPLETED+pending/failed/success UI        |
| D-13 | `OWNER_DECISION_REQUIRED` | signup session/response, 익명 CSRF, profile completion, 탈퇴 삭제 추적과 draft 보존이 불완전하다.                                                             | 익명 `GET /auth/csrf`, signup/login CSRF와 session rotation, 공통 auth response를 사용한다. 미승인 profile completion은 hard gate로 쓰지 않고 8.7에서 항목·경고 강도를 승인받는다. 탈퇴는 idempotency 없이 즉시 `WITHDRAWN`+모든 session 폐기+Agent Run과 분리된 durable deletion task의 202 receipt를 권장한다. draft는 사용자/resource별 `sessionStorage`와 폐기 규칙을 적용하되 삭제 SLA·draft 보존 UX는 승인받는다. | 인증 흐름을 단일화하고 공용 browser 계정 간 원문 노출과 삭제 유실을 막는다.                    | signup/login CSRF 면제, replay 불가능한 탈퇴 idempotency, profile hard gate, 사용자 ID 없는 무기한 localStorage | functional AUTH/PROF, api auth/account, db user lifecycle, page 3/13/14/15                | Security/session, deletion task, route guard, draft namespace/cache purge   | 익명 CSRF→signup, rotation, 전 session 폐기, receipt 추적, 두 사용자 draft/cache E2E                 |
| D-14 | `OWNER_DECISION_REQUIRED` | nullable canonical URL, 수동 완료 상태, reopen timestamp, PATCH version, OUTDATED와 fit rubric이 없다.                                                        | canonical URL은 접수 전 NOT NULL로 계산하고 active unique를 둔다. 수동 완료는 `MANUAL_INPUT_PROVIDED`. 상태 timestamp와 history 규칙을 고정한다. 분석 stale은 `analysisOutdated`+reason projection이고 기존 결과를 보존한다. score는 0.00..100.00, eligibility와 별도이며 rubric 가중치는 승인받는다.                                                                                                                   | 중복 비용, Scheduler race, stale 오인과 합격 확률 오해를 막는다.                               | nullable canonical, OUTDATED를 JobStatus로 추가, stale 분석 삭제, eligibility로 score cap                       | functional JOB-001~005, api 5, db 5, page 7, design                                       | URL normalizer, status command, analysis hash·rubric                        | URL equivalence·SSRF, timestamp/race, stale reason, rubric fixture와 UI 문구                         |
| D-15 | `RECOMMENDED`             | `PARSED`가 parser 성공인지 전체 근거 추출 성공인지, manual resume가 같은 run인지 불명확하다.                                                                  | parser 상태와 `EvidenceExtractionStatus`를 분리한다. `PARSED`는 text/masking/chunk 완료이고 AI 실패에도 유지한다. 비공백 code point 100자 미만은 `NEEDS_MANUAL_TEXT`. WAITING run은 manual text로 같은 run 재개, terminal retry/reparse는 새 run이다.                                                                                                                                                                   | deterministic 부분 성공을 재사용하고 AI 실패를 파일 실패로 오인하지 않는다.                    | 전체 pipeline 단일 status, AI 실패 시 text/chunk 삭제, 모든 manual 입력 새 run                                  | functional DOC/SYS, api documents, db 4/9, tech 4.3/10                                    | document columns, pipeline/run mapping, UI projection                       | parser 성공+AI 실패, text 부족, same-run resume, terminal new-run, concurrent processing 409         |
| D-16 | `OWNER_DECISION_REQUIRED` | logical step·Agent class 목록, 미승인 chunk, vector index와 URL/search gateway 경계가 다르며 저장소에는 active embedding provider·model이 선택되어 있지 않다. | logical step은 class 이름과 분리하고 6장의 fixed workflow를 따른다. 미승인 masked chunk는 후보 탐색·모순 확인에만 쓰고 writer·score의 긍정 사실 근거로 쓰지 않는다. 공고 fetch와 search gateway를 분리한다. embedding provider·model·dimension은 한 승인 항목으로 선택하며 그 전에는 vector typmod·index migration을 확정하지 않는다.                                                                                   | 자유 loop, 미승인 사실 생성, 근거 없는 vector 차원 고정과 SSRF·비용 경계 혼동을 막는다.        | model 없이 1536만 고정, mixed dimension index, Tavily로 사용자 URL fetch                                        | functional CL/INT, db chunks/provenance, tech 4/6/8/9, design                             | workflow registry, ContextBuilder, 승인 후 vector/gateway migration         | context 격리·provenance, prompt injection, 선택 model의 dimension 검증, gateway SSRF                 |
| D-17 | `RECOMMENDED`             | `/`, job 기본 child, catch-all, mock 생성 위치와 목록 filter가 없다.                                                                                          | `/`은 비인증 사용자를 login, 인증 사용자를 dashboard로 보낸다. signup 직후만 onboarding으로 이동하며 미승인 profile 완료 공식을 route gate로 쓰지 않는다. job 기본 child는 overview, catch-all은 전용 404다. 안전한 `returnTo`와 canonical query를 사용하고 목록 filter는 7장 기준으로 추가한다.                                                                                                                        | open redirect·guard loop·deep-link 단절과 미승인 제품 정책의 hard gate를 막는다.               | referrer/localStorage return, profile 공식 기반 강제 redirect, tab local state만 사용                           | page 1/4/7/10/11/15, api list/dashboard, design                                           | router/meta/query schema, list DTO/filter                                   | root 인증 2상태·signup onboarding, 악성 return URL, direct child, 404, filter URL E2E                |
| D-18 | `OWNER_DECISION_REQUIRED` | archive 가능한 상태, read-only 범위, 복귀와 `finalizedAt` 의미가 없다.                                                                                        | `DRAFT\|FINALIZED→ARCHIVED`, archived는 조회만 허용한다. `/unarchive`는 active가 없을 때 `ARCHIVED→DRAFT`; `archivedAt`은 지우고 `finalizedAt`은 마지막 최종화 이력으로 보존하는 안을 권장한다.                                                                                                                                                                                                                         | 보관을 삭제와 구분하고 실수 복구·과거 provenance와 active unique를 함께 지킨다.                | 영구 archive만, archived 직접 편집, FINALIZED로 즉시 복귀                                                       | functional CL-001, api cover, db 6.1, page 8/11, design                                   | archived timestamp, partial unique, command/action booleans                 | 모든 허용·금지 전이, active 충돌, timestamp, archived mutation 409                                   |

### 2.1 Gate A~C 연결

| Gate | 항목                                   | 연결된 결정                                                 | 승인 후 고정할 자동 검증                   |
| ---- | -------------------------------------- | ----------------------------------------------------------- | ------------------------------------------ |
| A    | 품질·질문·version source enum          | 공개·내부·검색·출처 타입 분리                               | OpenAPI↔Java↔DB CHECK↔TypeScript parity    |
| A    | endpoint DTO·validation·version·filter | 4장의 endpoint 기준선                                       | OpenAPI snapshot, validation·409 contract  |
| A    | CSRF/signup/login·탈퇴                 | 익명 CSRF, session rotation, deletion receipt               | MockMvc Security와 session lifecycle       |
| A    | cover letter editor·finalization       | TipTap JSON, current verification, warning 승인안           | XSS·count·finalization domain/API test     |
| A    | ARCHIVED 진입·복귀                     | archive read-only와 unarchive command                       | transition·active unique·UI action test    |
| A    | mock idempotency·Agent detail·SSE      | turn request, run projection, snapshot-first event          | replay·multi-tab·SSE reconnect test        |
| B    | tenant composite integrity             | 모든 user row와 owner composite FK                          | 두 사용자 cross-FK negative test           |
| B    | idempotency·outbox·lease·cancel        | 영속 record, storage outbox, run claim fields               | concurrency·restart·cleanup test           |
| B    | 삭제·SOURCE_DELETED·version 보존       | tombstone 제안과 soft-deleted question                      | privacy purge·provenance link test         |
| B    | research/source provenance cardinality | preparation당 combined run과 N:M link                       | FK·lineage·source coverage test            |
| B    | embedding model·dimension              | provider·model·dimension 동시 승인 전 vector migration 차단 | 선택 model metadata·dimension·reindex test |
| C    | retry·WAITING_USER·run identity        | 최대 3 attempt, terminal 새 run, waiting same run           | Fake workflow retry/resume/reuse test      |
| C    | model mapping·가격·reserve             | policy matrix와 immutable price/ledger                      | mapping·동시 reserve/settle test           |
| C    | 미승인 chunk                           | 탐색·경고 전용, writer/score 제외                           | Context fixture·provenance test            |
| C    | score rubric·source coverage           | 0..100 rubric 제안과 3단계 coverage                         | 고정 fixture·출처 부족 test                |
| C    | 동기 mock turn                         | request replay, bounded sync, feedback async                | timeout·비용·usage·feedback test           |

## 3. Canonical enum 및 상태 계약

### 3.1 핵심 타입

`terminal`은 해당 row/state instance가 더 전이하지 않는다는 뜻이다. terminal run의 사용자 retry는 기존 row를 변경하지 않고 새 run을 만든다.

| 타입                           | 전체 값과 의미                                                                                                                                             | 공개 API                                            | DB                                     | 허용 전이                                                                                                                                                                                                                                      | terminal                                                         | 재시도·취소                                                                           |
| ------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `JobStatus`                    | `IN_PROGRESS` 지원 준비, `SUBMITTED` 제출 이력, `CLOSED` 종료                                                                                              | 읽기·command 입력                                   | `job_postings.status`, history         | IN_PROGRESS→SUBMITTED/CLOSED; SUBMITTED→CLOSED; CLOSED→IN_PROGRESS/SUBMITTED                                                                                                                                                                   | 없음                                                             | retry/cancel 대상 아님; version 충돌은 409                                            |
| `JobExtractionStatus`          | `QUEUED` 대기, `EXTRACTING` 처리, `EXTRACTED` 원격 성공, `MANUAL_INPUT_PROVIDED` 수동 본문 준비, `NEEDS_MANUAL_INPUT` 사용자 입력 필요, `FAILED` 기술 실패 | 읽기; client 입력 금지                              | `job_postings.extraction_status`       | QUEUED→EXTRACTING→EXTRACTED/NEEDS_MANUAL_INPUT/FAILED; NEEDS_MANUAL_INPUT→MANUAL_INPUT_PROVIDED 또는 EXTRACTING; FAILED→QUEUED는 명시 retry; EXTRACTING cancel→기존 usable source의 EXTRACTED/MANUAL_INPUT_PROVIDED, 없으면 NEEDS_MANUAL_INPUT | EXTRACTED, MANUAL_INPUT_PROVIDED는 현재 source revision terminal | NEEDS/FAILED와 cancel 후 stable 상태에서 retry 가능; cancel은 run에 기록              |
| `DocumentParseStatus`          | `UPLOADED`, `PARSING`, `PARSED`(text/mask/chunk 완료), `NEEDS_MANUAL_TEXT`, `FAILED`                                                                       | 읽기; client 입력 금지                              | `documents.parse_status`               | UPLOADED→PARSING→PARSED/NEEDS_MANUAL_TEXT/FAILED; NEEDS/FAILED/PARSED→PARSING은 explicit manual/reparse; PARSING cancel→같은 revision의 committed text/chunk가 있으면 PARSED, 없으면 UPLOADED                                                  | PARSED는 revision terminal                                       | NEEDS는 same waiting run resume; FAILED/PARSED/UPLOADED는 새 run; cancel은 run에 기록 |
| `EvidenceExtractionStatus`     | `NOT_STARTED`, `QUEUED`, `EXTRACTING`, `SUCCEEDED`, `FAILED`                                                                                               | document 읽기                                       | `documents.evidence_extraction_status` | NOT_STARTED→QUEUED→EXTRACTING→SUCCEEDED/FAILED; FAILED/SUCCEEDED→QUEUED는 explicit rerun; QUEUED/EXTRACTING cancel→같은 revision의 prior 성공 snapshot이 있으면 SUCCEEDED, 없으면 NOT_STARTED                                                  | SUCCEEDED는 revision terminal                                    | FAILED/NOT_STARTED retry 가능; cancel은 run에 기록                                    |
| `EvidenceVerificationStatus`   | `PENDING` 검토 전, `VERIFIED` 승인, `REJECTED` 거절, `SOURCE_DELETED` 원천 삭제                                                                            | 읽기; PATCH는 VERIFIED/REJECTED만                   | `profile_evidence.verification_status` | PENDING↔VERIFIED/REJECTED; VERIFIED↔REJECTED; 어느 활성 상태→SOURCE_DELETED                                                                                                                                                                    | SOURCE_DELETED                                                   | retry/cancel 없음; 원천 삭제 후 새 manual evidence만 생성                             |
| `CoverLetterStatus`            | `DRAFT`, `FINALIZED`, `ARCHIVED`                                                                                                                           | 읽기; 전용 command만 입력                           | `cover_letters.status`                 | DRAFT→FINALIZED/ARCHIVED; FINALIZED→DRAFT/ARCHIVED; ARCHIVED→DRAFT                                                                                                                                                                             | 없음                                                             | archive/unarchive는 새 command; optimistic version 필수                               |
| `CoverLetterVersionSource`     | `AI_GENERATED` 최초 AI, `USER_EDITED` 사용자 저장, `AI_REVISED` current가 있는 AI 재생성, `RESTORED` 과거 복원                                             | 읽기 전용                                           | answer version                         | immutable row                                                                                                                                                                                                                                  | 모두                                                             | 재시도는 새 version; client가 source를 지정하지 않음                                  |
| `InterviewAnswerVersionSource` | `USER_EDITED` 사용자 저장                                                                                                                                  | 읽기 전용                                           | interview answer version               | immutable row                                                                                                                                                                                                                                  | 모두                                                             | MVP에는 AI 생성·복원 경로가 없으며 client가 source를 지정하지 않음                    |
| `VerificationStatus`           | `PENDING`, `PASSED`, `WARNING`, `FAILED`                                                                                                                   | 읽기                                                | cover verification                     | 한 verification은 PENDING→PASSED/WARNING/FAILED; run 실패·취소도 같은 transaction에서 FAILED로 종결                                                                                                                                            | PASSED/WARNING/FAILED                                            | 재검증은 새 verification/run; PENDING 고착 금지                                       |
| `ResearchQuality`              | `BASIC` 검색 최대 2×5, `ADVANCED` 최대 4×8                                                                                                                 | preparation 입력·읽기                               | research run                           | 상태 전이 없음                                                                                                                                                                                                                                 | 해당 없음                                                        | 새 research retry에서 다시 선택 가능                                                  |
| `SourceCoverage`               | `SUFFICIENT` 기준 충족, `LIMITED` usable하지만 부족, `NONE` usable source 없음                                                                             | research/question set 읽기·filter                   | successful research run                | 실행 중 null, 성공 시 한 값                                                                                                                                                                                                                    | 모두                                                             | 부족은 실패가 아님; 재조사는 새 run                                                   |
| `InterviewQuestionType`        | `COVER_LETTER`, `RESUME`, `PORTFOLIO`, `TECHNICAL`, `PROJECT_DEEP_DIVE`, `BEHAVIORAL`, `COMPANY_MOTIVATION`, `FOLLOW_UP`                                   | 요청·읽기·filter; FOLLOW_UP은 출력 전용             | interview question                     | immutable                                                                                                                                                                                                                                      | 모두                                                             | retry/cancel 없음                                                                     |
| `MockInterviewStatus`          | `READY`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`                                                                                                           | 읽기; 전용 command                                  | mock session                           | READY→IN_PROGRESS/CANCELLED; IN_PROGRESS→COMPLETED/CANCELLED                                                                                                                                                                                   | COMPLETED, CANCELLED                                             | turn retry는 clientRequest replay; session cancel은 비terminal에서 가능               |
| `MockFeedbackStatus`           | `NOT_REQUESTED`, `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`                                                                                   | session/feedback 읽기                               | mock session                           | NOT_REQUESTED→QUEUED→RUNNING→SUCCEEDED/FAILED/CANCELLED; FAILED→QUEUED는 새 feedback run projection                                                                                                                                            | SUCCEEDED, FAILED, CANCELLED attempt                             | FAILED retry는 새 run; queued/running cancel 가능                                     |
| `AgentRunStatus`               | `QUEUED`, `RUNNING`, `WAITING_USER`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `INTERRUPTED`                                                                     | 읽기·filter                                         | agent run                              | QUEUED→RUNNING/CANCELLED; RUNNING→WAITING_USER/SUCCEEDED/FAILED/CANCELLED/INTERRUPTED; WAITING_USER→QUEUED/CANCELLED                                                                                                                           | SUCCEEDED, FAILED, CANCELLED, INTERRUPTED                        | FAILED/INTERRUPTED 중 retryable만 새 run; 비terminal cancel 가능                      |
| `AgentStepStatus`              | `PENDING`, `RUNNING`, `WAITING_USER`, `SUCCEEDED`, `FAILED`, `SKIPPED`, `REUSED`, `CANCELLED`, `INTERRUPTED`                                               | run detail/SSE 읽기                                 | agent step attempt                     | PENDING→RUNNING/SKIPPED/REUSED/CANCELLED; RUNNING→WAITING_USER/SUCCEEDED/FAILED/CANCELLED/INTERRUPTED; WAITING_USER→PENDING/CANCELLED                                                                                                          | SUCCEEDED/FAILED/SKIPPED/REUSED/CANCELLED/INTERRUPTED            | retry는 새 attempt, step 직접 cancel API 없음                                         |
| `AiQualityMode`                | `ECONOMY`, `BALANCED`, `HIGH_QUALITY` 사용자 품질 의도                                                                                                     | 요청·설정·run 읽기                                  | preference와 run snapshot              | 상태 전이 없음                                                                                                                                                                                                                                 | 해당 없음                                                        | HIGH_QUALITY는 설정+요청+예산 필요; 자동 승격 대상 아님                               |
| `ModelTier`                    | `LOW_COST`, `BALANCED`, `HIGH_QUALITY` 내부 routing 결과                                                                                                   | run의 `highestModelTierUsed`만 읽기 전용, 입력 금지 | policy·step·usage                      | 호출마다 policy가 선택                                                                                                                                                                                                                         | 해당 없음                                                        | LOW_COST→BALANCED 승격 1회 가능, attempt에 포함                                       |
| `WorkflowType`                 | 아래 8개 고정 AI workflow                                                                                                                                  | Agent Run 읽기·filter                               | agent run                              | registry version에 따라 fixed step                                                                                                                                                                                                             | run 상태를 따름                                                  | workflow별 retry policy를 따름                                                        |

`WorkflowType` 전체 값:

```text
DOCUMENT_INGESTION
JOB_POSTING_EXTRACTION
JOB_ANALYSIS
COVER_LETTER_GENERATION
COVER_LETTER_VERIFICATION
INTERVIEW_PREPARATION
INTERVIEW_ANSWER_FEEDBACK
MOCK_INTERVIEW_FEEDBACK
```

회원 탈퇴는 AI workflow가 아니며 Agent Run을 재사용하지 않는 `account_deletion_tasks`로 실행한다. session 폐기 뒤 조회할 수 없는 run ID를 공개하지 않으며, 202 receipt의 `deletionRequestId`가 지원·운영 추적 키다. 동기 mock turn은 workflow가 아니라 `mock_interview_turns`에 기록하고, 종합 feedback만 `MOCK_INTERVIEW_FEEDBACK`이다.

### 3.2 보조 enum

| 타입                     | 값                                                                                                                                            | 의미·저장·노출                                                           |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| `ResearchRunStatus`      | QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED                                                                                                 | 공개/DB. 검색 부족은 SUCCEEDED+LIMITED/NONE, provider 전면 장애만 FAILED |
| `DeadlineSource`         | USER_ENTERED, AUTO_EXTRACTED, UNKNOWN                                                                                                         | job 공개/DB                                                              |
| `ClosedReason`           | DEADLINE_PASSED, USER_CLOSED, URL_INACTIVE                                                                                                    | job 공개/DB; reopen 때 현재 값은 null, history는 유지                    |
| `OutdatedReason`         | JOB_CONTENT_CHANGED, PROFILE_CHANGED, EVIDENCE_CHANGED                                                                                        | 계산 projection, DB enum으로 저장하지 않음                               |
| `RequiredUserActionType` | PROVIDE_DOCUMENT_TEXT, PROVIDE_JOB_TEXT, ENABLE_HIGH_QUALITY, INCREASE_BUDGET                                                                 | Agent Run 읽기 projection; 안전한 deep-link payload만 제공               |
| `MockInterviewType`      | TECHNICAL, BEHAVIORAL, TECHNICAL_AND_BEHAVIORAL                                                                                               | mock 생성 입력·읽기/DB; 자유 문자열 금지                                 |
| `MockDifficulty`         | EASY, NORMAL, HARD                                                                                                                            | mock 생성 입력·읽기/DB                                                   |
| `MockFeedbackTiming`     | AFTER_EACH, END_ONLY                                                                                                                          | mock 생성 입력·읽기/DB                                                   |
| `MockMessageRole`        | USER, INTERVIEWER                                                                                                                             | mock message 읽기/DB; client는 message endpoint에서 role 지정 금지       |
| `DeletionTaskStatus`     | QUEUED, RUNNING, RETRY_WAIT, SUCCEEDED, DEAD                                                                                                  | 202에는 QUEUED만 공개; 나머지는 내부 운영/DB                             |
| `Eligibility`            | ELIGIBLE, CONDITIONAL, INELIGIBLE, UNKNOWN                                                                                                    | job analysis 공개/DB                                                     |
| `FitCriterionCategory`   | REQUIRED_QUALIFICATION, CORE_RESPONSIBILITY_OR_SKILL, PREFERRED_QUALIFICATION, RELATED_EXPERIENCE_OR_DOMAIN, EDUCATION_CERTIFICATION_LANGUAGE | job analysis 공개/DB rubric item                                         |
| `MatchLevel`             | MATCHED, PARTIAL, MISSING, UNKNOWN                                                                                                            | job analysis 공개/DB; 계수는 1.0/0.5/0/0                                 |
| `VerificationIssueCode`  | UNVERIFIED_CLAIM, CONTRADICTION, REQUIREMENT_MISSING, LENGTH_VIOLATION, SOURCE_DELETED, OTHER                                                 | verification 공개/DB                                                     |
| `IssueSeverity`          | WARNING, ERROR                                                                                                                                | verification 공개/DB                                                     |
| `MockFeedbackCategory`   | CONTENT, STRUCTURE, EVIDENCE_USE, COMMUNICATION, OVERALL                                                                                      | mock feedback 공개/DB                                                    |
| `ProfileCompletionItem`  | LEGAL_NAME, DESIRED_ROLE, DESIRED_INDUSTRY, DESIRED_LOCATION, PRIMARY_EDUCATION                                                               | 8.7 승인 시에만 profile/dashboard 공개 계산 projection으로 채택하는 후보 |
| `DocumentType`           | RESUME, PORTFOLIO, CAREER_DESCRIPTION, CERTIFICATE, TRANSCRIPT, OTHER                                                                         | document 입력·읽기/DB                                                    |
| `EvidenceSourceType`     | EDUCATION, CERTIFICATION, LANGUAGE_SCORE, AWARD, CAREER, DOCUMENT_CHUNK, MANUAL                                                               | evidence 읽기/DB                                                         |
| `JobDescriptionSource`   | AUTO_EXTRACTED, USER_ENTERED                                                                                                                  | job 읽기/DB                                                              |
| `ResearchTopic`          | COMPANY, INTERVIEW_PROCESS, ROLE_TECHNICAL                                                                                                    | research source 읽기·filter/DB                                           |
| `ResearchSourceType`     | OFFICIAL, TECH_BLOG, NEWS, INTERVIEW_REVIEW, COMMUNITY, OTHER                                                                                 | functional INT-002와 동일한 research source 읽기·filter/DB               |
| `EducationStatus`        | ENROLLED, LEAVE_OF_ABSENCE, EXPECTED_GRADUATION, GRADUATED, WITHDRAWN                                                                         | education 입력·읽기/DB                                                   |
| `AnswerCreatedBy`        | USER, AI                                                                                                                                      | answer version 읽기/DB; server 지정                                      |

## 4. API 계약 기준선

### 4.1 공통 HTTP·validation·오류

- Base URL은 `/api/v1`, ID는 UUID, 시간은 UTC ISO-8601이다.
- 모든 mutation은 CSRF가 필요하다. `GET /auth/csrf`는 익명 접근을 허용하고 anonymous session을 초기화한다. signup/login도 CSRF를 요구한다.
- 단일 성공 DTO는 직접 반환하고 목록은 `PageResponse<T>{items,page,size,totalElements,totalPages}`다.
- `page` 기본 0·최소 0, `size` 기본 20·범위 1..100이다. sort와 filter는 endpoint allowlist 밖 값을 400으로 거부한다.
- 이름·제목 계열은 trim하고 NUL·제어문자를 거부한다. 본문은 의미 있는 공백을 보존한다.
- 이미 존재하는 동시 편집 aggregate를 바꾸는 command의 `version`은 0 이상 필수다. delete는 `?version=n`, append-only answer 저장은 `parentVersionId`가 current CAS다. signup/login/logout, display-name/password, 탈퇴처럼 session·credential 또는 단일 사용자 command이고 endpoint matrix가 `version 없음`으로 선언한 요청은 예외이며 자체 transaction과 현재 credential 검증으로 보호한다.
- 다른 사용자의 root·child·SSE·Object는 모두 `404 RESOURCE_NOT_FOUND`다.
- `Idempotency-Key`는 ASCII `[A-Za-z0-9._:-]{8,128}`이며 endpoint 표에서 `I`로 표시한 요청에 필수다.

주요 오류 code:

| HTTP | code                                                                                                                                                                                                                                                                                                       | 사용 조건                                     |
| ---: | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
|  400 | `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `QUALITY_MODE_NOT_SUPPORTED`, `DOCUMENT_TEXT_TOO_SHORT`                                                                                                                                                                                                           | 입력·JSON·endpoint별 품질 오류                |
|  401 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS`                                                                                                                                                                                                                                                           | 세션 없음, 로그인 실패(이메일 존재 여부 은닉) |
|  403 | `CSRF_INVALID`, `ACCESS_DENIED`                                                                                                                                                                                                                                                                            | CSRF·권한                                     |
|  404 | `RESOURCE_NOT_FOUND`, `JOB_ANALYSIS_NOT_FOUND`                                                                                                                                                                                                                                                             | 미존재·타 사용자·latest 분석 없음             |
|  409 | `RESOURCE_VERSION_CONFLICT`, `RESOURCE_STATE_CONFLICT`, `DUPLICATE_RESOURCE`, `EMAIL_ALREADY_REGISTERED`, `DUPLICATE_JOB_URL`, `IDEMPOTENCY_REQUEST_IN_PROGRESS`, `IDEMPOTENCY_KEY_REUSED`, `ACTIVE_COVER_LETTER_EXISTS`, `COVER_LETTER_NOT_FINALIZABLE`, `COVER_LETTER_ARCHIVED`, `MOCK_TURN_IN_PROGRESS` | version·상태·중복·idempotency                 |
|  413 | `PAYLOAD_TOO_LARGE`                                                                                                                                                                                                                                                                                        | 파일/요청 크기 초과                           |
|  415 | `UNSUPPORTED_MEDIA_TYPE`                                                                                                                                                                                                                                                                                   | 확장자·탐지 MIME 불일치 포함                  |
|  429 | `RATE_OR_BUDGET_LIMIT_EXCEEDED`                                                                                                                                                                                                                                                                            | 비용 예약 실패·rate limit                     |
|  503 | `EXTERNAL_SERVICE_UNAVAILABLE`                                                                                                                                                                                                                                                                             | LLM/Search/Object/URL 일시 장애               |
|  500 | `INTERNAL_ERROR`                                                                                                                                                                                                                                                                                           | 내부 상세를 숨긴 예상 밖 오류                 |

모든 오류는 `timestamp,status,code,message,fieldErrors[{field,reason}],requestId`를 반환한다.

### 4.2 Idempotency header 적용 범위

`I` 적용 endpoint는 document upload/manual/reparse, job create/extraction retry/analysis, cover letter create/generate/verify, interview preparation/research retry/answer feedback, mock session create/complete, Agent Run retry다. 같은 key+hash 완료 응답은 원래 HTTP status와 DTO를 재생하고 `Idempotency-Replayed: true`를 붙인다. validation·owner 실패는 record 생성 전에 반환한다. 회원 탈퇴는 성공 transaction에서 모든 session을 폐기하므로 인증된 replay가 불가능해 `Idempotency-Key`를 지원하지 않는다.

### 4.3 DTO catalog

아래 field set은 완전한 공개 projection 기준선이다. `?`만 nullable이고 나머지는 required다. `SummaryDto + fields`는 이름이 적힌 Summary DTO를 OpenAPI `allOf`로 포함한다는 뜻이며 임의 필드를 생략한다는 뜻이 아니다. 모든 배열은 명시한 element DTO 또는 scalar 타입을 사용한다.

공개 DTO는 화면 표시·사용자 command·안전한 충돌 복구에 필요한 값만 포함한다. 파일 checksum, parser 이름/version, snapshot·content hash, prompt version, provider/model ID, per-step model tier, 재사용 원본 step ID와 provider rank는 5~6장의 persistence·workflow 내부 필드이며 일반 사용자 API에 노출하지 않는다. 모델 정보는 Agent Run의 안전한 `highestModelTierUsed`만 공개한다.

공통 element DTO:

| DTO                     | 필드                                                                                                                                                                                                                    |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SafeErrorDto`          | `code:string 1..100`, `message:string 1..500`; 내부 예외·prompt·원문 금지                                                                                                                                               |
| `ResourceRefDto`        | `resourceType:string 1..50`, `resourceId:UUID`, `displayLabel:string? <=200`                                                                                                                                            |
| `JobRefDto`             | `id:UUID`, `companyName:string? <=200`, `positionName:string? <=300`, `title:string? <=300`                                                                                                                             |
| `CoverLetterRefDto`     | `id:UUID`, `title:string 1..300`, `status:CoverLetterStatus`                                                                                                                                                            |
| `QuestionSetRefDto`     | `id:UUID`, `title:string 1..300`                                                                                                                                                                                        |
| `AgentRunRefDto`        | `id:UUID`, `status:AgentRunStatus`, `currentStep:string? <=100`, `progressPercent:int 0..100`                                                                                                                           |
| `EvidenceRefDto`        | `id:UUID`, `title:string 1..250`, `evidenceCategory:string 1..80`, `verificationStatus:EvidenceVerificationStatus`, `sourceType:EvidenceSourceType`, `sourceDeleted:boolean`                                            |
| `ResearchSourceRefDto`  | `id:UUID`, `topic:ResearchTopic`, `title:string? <=500`, `sourceUrl:string 1..2000`, `sourceType:ResearchSourceType`, `retrievedAt:Instant`                                                                             |
| `RequirementItemDto`    | `category:FitCriterionCategory`, `text:string 1..2000`, `required:boolean`, `sourceLocation:string? <=500`                                                                                                              |
| `ScoreCriterionDto`     | `category:FitCriterionCategory`, `criterion:string 1..2000`, `weight:decimal 0..100`, `matchLevel:MatchLevel`, `score:decimal 0..weight`, `evidenceRefs:EvidenceRefDto[0..20]`, `explanation:string 1..2000`            |
| `VerificationIssueDto`  | `code:VerificationIssueCode`, `severity:IssueSeverity`, `message:string 1..1000`, `relatedText:string? <=1000`, `evidenceRefs:EvidenceRefDto[0..20]`                                                                    |
| `VerifiedClaimDto`      | `claim:string 1..2000`, `supported:boolean`, `evidenceRefs:EvidenceRefDto[0..20]`                                                                                                                                       |
| `FeedbackScoreDto`      | `criterion:string 1..100`, `score:decimal 0..100`, `explanation:string? <=1000`                                                                                                                                         |
| `ImmediateFeedbackDto`  | `score:decimal? 0..100`, `strengths:string[0..10]`(항목 1..500), `improvements:string[0..10]`(항목 1..500), `suggestedAnswer:string? <=5000`                                                                            |
| `MockFeedbackItemDto`   | `category:MockFeedbackCategory`, `relatedMessageSequenceNo:long? >=1`, `score:decimal? 0..100`, `strengths:string[0..10]`(항목 1..500), `improvements:string[0..10]`(항목 1..500), `recommendation:string? <=2000`      |
| `RequiredUserActionDto` | `type:RequiredUserActionType`, `resource:ResourceRefDto?`, `route:string? 1..500`(server allowlist의 same-origin path), `message:string 1..500`                                                                         |
| `PartialResultDto`      | `succeededScopeKeys:string[0..100]`(항목 1..100), `failedScopeKeys:string[0..100]`(항목 1..100), `resultRefs:ResourceRefDto[0..200]`                                                                                    |
| `TipTapMarkDto`         | `type:bold\|italic`                                                                                                                                                                                                     |
| `TipTapNodeDto`         | `type:paragraph\|text\|hardBreak\|bulletList\|orderedList\|listItem`, `text:string? 1..20000`, `marks:TipTapMarkDto[0..2]`, `content:TipTapNodeDto[0..1000]`; text/marks는 text node만, content는 container node만 허용 |
| `TipTapDocumentDto`     | `type:doc`, `content:TipTapNodeDto[0..1000]`; 전체 파생 plain text 최대 20000 code point                                                                                                                                |

| DTO                           | 필드                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `CsrfDto`                     | `headerName:string(1..100)`, `parameterName:string(1..100)`, `token:string(nonblank)`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `CurrentUserDto`              | `id:UUID`, `email:string 3..320`, `displayName:string 1..100`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `AuthSessionDto`              | `user:CurrentUserDto`, `csrf:CsrfDto`; 응답과 함께 rotated Session Cookie 발급                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `AccountDeletionAcceptedDto`  | `deletionRequestId:UUID`, `status:QUEUED`, `requestedAt:Instant`, `purgeBy:Instant`; Agent Run ID 없음                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `RunAcceptedDto`              | `agentRunId:UUID`, `status:QUEUED\|WAITING_USER`, `resourceType:string 1..50`, `resourceId:UUID`, `replayed:boolean`                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `DocumentUploadAcceptedDto`   | `documentId:UUID`, `parseStatus:UPLOADED`, `evidenceExtractionStatus:NOT_STARTED`, `agentRunId:UUID`, `status:QUEUED`                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `ProfileDto`                  | `legalName:string? 1..100`, `introduction:string? <=2000`, `desiredRoles:string[0..10]`, `desiredIndustries:string[0..10]`, `desiredLocations:string[0..10]`(각 항목 1..100), `expectedGraduationDate:LocalDate?`, `profileCompleted:boolean`, `missingCompletionItems:ProfileCompletionItem[0..5]`, `version:long`, `createdAt:Instant`, `updatedAt:Instant`; 완료 관련 두 필드는 8.7 승인 전 미구현                                                                                                                                                                                      |
| `EducationDto`                | `id:UUID,schoolName:string 1..200,major:string? <=200,degree:string? <=100,educationStatus:EducationStatus,admissionDate:LocalDate?,graduationDate:LocalDate?,gpa:decimal? 0..10,gpaScale:decimal? 0.01..10,isPrimary:boolean,description:string? <=5000,version:long,createdAt:Instant,updatedAt:Instant`                                                                                                                                                                                                                                                                                 |
| `CertificationDto`            | `id:UUID,name:string 1..200,issuer:string? <=200,credentialNumber:string? <=200,acquiredDate:LocalDate?,expiresAt:LocalDate?,description:string? <=5000,evidenceDocumentId:UUID?,version:long,createdAt:Instant,updatedAt:Instant`                                                                                                                                                                                                                                                                                                                                                         |
| `LanguageScoreDto`            | `id:UUID,testName:string 1..100,score:string 1..100,grade:string? <=100,testedAt:LocalDate?,expiresAt:LocalDate?,evidenceDocumentId:UUID?,version:long,createdAt:Instant,updatedAt:Instant`                                                                                                                                                                                                                                                                                                                                                                                                |
| `AwardDto`                    | `id:UUID,name:string 1..200,organizer:string? <=200,awardedAt:LocalDate?,description:string? <=5000,evidenceDocumentId:UUID?,version:long,createdAt:Instant,updatedAt:Instant`                                                                                                                                                                                                                                                                                                                                                                                                             |
| `CareerDto`                   | `id:UUID,organization:string 1..200,position:string? <=200,employmentType:string? <=50,startedAt:LocalDate?,endedAt:LocalDate?,isCurrent:boolean,responsibilities:string? <=20000,achievements:string? <=20000,version:long,createdAt:Instant,updatedAt:Instant`                                                                                                                                                                                                                                                                                                                           |
| `DocumentSummaryDto`          | `id:UUID,documentType:DocumentType,displayName:string 1..255,mimeType:string 1..100,fileSizeBytes:long 1..20MiB,parseStatus:DocumentParseStatus,evidenceExtractionStatus:EvidenceExtractionStatus,manualTextProvided:boolean,safeError:SafeErrorDto?,latestAgentRunId:UUID?,version:long,uploadedAt:Instant,updatedAt:Instant`                                                                                                                                                                                                                                                             |
| `DocumentDetailDto`           | `DocumentSummaryDto` + `pageCount:int? >=1,characterCount:int? >=0,parsedAt:Instant?`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `DocumentTextDto`             | `documentId:UUID,text:string 0..500000,characterCount:int >=0,manualTextProvided:boolean,version:long,updatedAt:Instant`                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `DownloadUrlDto`              | `url:string 1..4096`, `expiresAt:Instant`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `EvidenceDto`                 | `id:UUID,sourceType:EvidenceSourceType,sourceEntityId:UUID?,documentId:UUID?,sourceDeletedAt:Instant?,evidenceCategory:string 1..80,title:string 1..250,content:string 1..20000,metadata:Record<string,string\|number\|boolean\|null>,confidence:decimal? 0..1,verificationStatus:EvidenceVerificationStatus,verifiedAt:Instant?,version:long,createdAt:Instant,updatedAt:Instant`                                                                                                                                                                                                         |
| `JobSummaryDto`               | `id:UUID,companyName:string? <=200,title:string? <=300,positionName:string? <=300,status:JobStatus,extractionStatus:JobExtractionStatus,submittedAt:Instant?,deadlineAt:Instant?,deadlineSource:DeadlineSource,latestFitScore:decimal? 0..100,analysisOutdated:boolean,outdatedReasons:OutdatedReason[0..3],coverLetterStatus:CoverLetterStatus?,interviewPreparationCount:int >=0,version:long,createdAt:Instant,updatedAt:Instant`                                                                                                                                                       |
| `JobDetailDto`                | `JobSummaryDto` + `sourceUrl:string 1..2000,canonicalUrl:string 1..2000,roleCategory:string? <=100,employmentType:string? <=100,location:string? <=200,descriptionText:string? <=200000,descriptionSource:JobDescriptionSource?,extractionError:SafeErrorDto?,closedAt:Instant?,closedReason:ClosedReason?,latestAnalysis:JobAnalysisSummaryDto?,coverLetterId:UUID?,latestQuestionSetId:UUID?,latestMockSessionId:UUID?`                                                                                                                                                                  |
| `JobAnalysisSummaryDto`       | `id:UUID,analysisVersion:int >=1,eligibility:Eligibility,fitScore:decimal? 0..100,analysisOutdated:boolean,outdatedReasons:OutdatedReason[0..3],createdAt:Instant,agentRunId:UUID`                                                                                                                                                                                                                                                                                                                                                                                                         |
| `JobAnalysisDetailDto`        | `JobAnalysisSummaryDto` + `scoreBreakdown:ScoreCriterionDto[0..100],requiredQualifications:RequirementItemDto[0..100],preferredQualifications:RequirementItemDto[0..100],responsibilities:RequirementItemDto[0..100],strengths:string[0..20]`(항목 1..1000), `gaps:string[0..20]`(항목 1..1000), `matchedEvidenceRefs:EvidenceRefDto[0..100],analysisSummary:string? <=10000`                                                                                                                                                                                                              |
| `CoverLetterSummaryDto`       | `id:UUID,job:JobRefDto,title:string 1..300,status:CoverLetterStatus,questionCount:int 0..20,answeredQuestionCount:int 0..20,latestVerificationStatus:VerificationStatus?,warningCount:int >=0,canEdit:boolean,canArchive:boolean,canUnarchive:boolean,canFinalize:boolean,version:long,finalizedAt:Instant?,archivedAt:Instant?,createdAt:Instant,updatedAt:Instant`                                                                                                                                                                                                                       |
| `CoverLetterQuestionDto`      | `id:UUID,questionOrder:int 1..20,questionText:string 1..2000,maxLength:int? 1..10000,memo:string? <=2000,currentAnswer:CoverLetterAnswerVersionDto?,latestVerification:VerificationDto?,version:long,deletedAt:Instant?`                                                                                                                                                                                                                                                                                                                                                                   |
| `CoverLetterAnswerVersionDto` | `id:UUID,questionId:UUID,parentVersionId:UUID?,restoredFromVersionId:UUID?,versionNo:int >=1,contentJson:TipTapDocumentDto,plainText:string 0..20000,characterCount:int 0..20000,sourceType:CoverLetterVersionSource,isCurrent:boolean,createdBy:AnswerCreatedBy,createdAt:Instant`                                                                                                                                                                                                                                                                                                        |
| `VerificationDto`             | `id:UUID,answerVersionId:UUID,status:VerificationStatus,issues:VerificationIssueDto[0..100],suggestions:string[0..20]`(항목 1..1000), `verifiedClaims:VerifiedClaimDto[0..100],evidenceRefs:EvidenceRefDto[0..100],agentRunId:UUID?,createdAt:Instant`                                                                                                                                                                                                                                                                                                                                     |
| `CoverLetterDetailDto`        | `CoverLetterSummaryDto` + `questions:CoverLetterQuestionDto[0..20]`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `ResearchRunDto`              | `id:UUID,retryOfResearchRunId:UUID?,researchQuality:ResearchQuality,status:ResearchRunStatus,sourceCoverage:SourceCoverage?,missingCoverageTopics:string[0..20]`(항목 1..200), `summary:string? <=10000,agentRunId:UUID,retryable:boolean,safeError:SafeErrorDto?,createdAt:Instant,startedAt:Instant?,completedAt:Instant?`                                                                                                                                                                                                                                                               |
| `ResearchSourceDto`           | `id:UUID,topic:ResearchTopic,sourceUrl:string 1..2000,title:string? <=500,sourceType:ResearchSourceType,publishedAt:Instant?,retrievedAt:Instant,snippet:string? <=2000,reliabilityNotice:string 1..500`                                                                                                                                                                                                                                                                                                                                                                                   |
| `QuestionSetSummaryDto`       | `id:UUID,job:JobRefDto,coverLetter:CoverLetterRefDto,title:string 1..300,questionCount:int 0..20,researchRunId:UUID,sourceCoverage:SourceCoverage?,agentRun:AgentRunRefDto,createdAt:Instant,updatedAt:Instant`                                                                                                                                                                                                                                                                                                                                                                            |
| `InterviewQuestionDto`        | `id:UUID,questionOrder:int 1..20,questionType:InterviewQuestionType,questionText:string 1..2000,intent:string? <=2000,evaluationPoints:string[0..20]`(항목 1..500), `answerGuide:string? <=10000,followUpQuestions:string[0..10]`(항목 1..2000), `relatedEvidenceRefs:EvidenceRefDto[0..20],sourceRefs:ResearchSourceRefDto[0..50],sourceBased:boolean,currentAnswer:InterviewAnswerVersionDto?,latestFeedback:InterviewFeedbackDto?`                                                                                                                                                      |
| `QuestionSetDetailDto`        | `QuestionSetSummaryDto` + `research:ResearchRunDto,questions:InterviewQuestionDto[0..20]`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `InterviewAnswerVersionDto`   | `id:UUID,questionId:UUID,parentVersionId:UUID?,versionNo:int >=1,content:string 1..20000,sourceType:InterviewAnswerVersionSource,isCurrent:boolean,createdAt:Instant`                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `InterviewFeedbackDto`        | `id:UUID,answerVersionId:UUID,scores:FeedbackScoreDto[1..20],strengths:string[0..20]`(항목 1..1000), `weaknesses:string[0..20]`(항목 1..1000), `suggestions:string[0..20]`(항목 1..1000), `revisedExample:string? <=10000,agentRunId:UUID,createdAt:Instant`; 성공한 feedback만 존재                                                                                                                                                                                                                                                                                                       |
| `MockSessionSummaryDto`       | `id:UUID,job:JobRefDto,coverLetter:CoverLetterRefDto,questionSet:QuestionSetRefDto?,status:MockInterviewStatus,feedbackStatus:MockFeedbackStatus,interviewType:MockInterviewType,difficulty:MockDifficulty,targetQuestionCount:int 1..20,currentQuestionCount:int 0..20,feedbackTiming:MockFeedbackTiming,pressureMode:boolean,preferredEvidenceIds:UUID[0..5],version:long,actualCostUsd:decimal >=0,feedbackAgentRunId:UUID?,canStart:boolean,canSend:boolean,canComplete:boolean,canCancel:boolean,feedbackRetryable:boolean,startedAt:Instant?,completedAt:Instant?,createdAt:Instant` |
| `MockMessageDto`              | `id:UUID,sequenceNo:long >=1,role:MockMessageRole,content:string 1..5000,relatedQuestionId:UUID?,createdAt:Instant`                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `MockTurnResponseDto`         | `clientRequestId:UUID,userMessageId:UUID?,interviewerMessage:MockMessageDto,immediateFeedback:ImmediateFeedbackDto?,sessionStatus:MockInterviewStatus,feedbackStatus:MockFeedbackStatus,sessionVersion:long,actualCostUsd:decimal >=0`                                                                                                                                                                                                                                                                                                                                                     |
| `MockFeedbackDto`             | `status:MockFeedbackStatus,agentRunId:UUID?,items:MockFeedbackItemDto[0..100],sessionSummary:string? <=10000,safeError:SafeErrorDto?`                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `AgentStepDto`                | `id:UUID,stepKey:string 1..100,scopeKey:string? <=100,stepOrder:int >=1,status:AgentStepStatus,attempt:int 1..maxAttempts,maxAttempts:int 1..3,startedAt:Instant?,completedAt:Instant?,safeError:SafeErrorDto?`                                                                                                                                                                                                                                                                                                                                                                            |
| `AgentRunSummaryDto`          | `id:UUID,workflowType:WorkflowType,resourceType:string?,resourceId:UUID?,status:AgentRunStatus,currentStep:string?,progressPercent:int 0..100,requestedQualityMode:AiQualityMode?,highestModelTierUsed:ModelTier?,estimatedCostUsd:decimal >=0,reservedCostUsd:decimal >=0,actualCostUsd:decimal >=0,retryable:boolean,cancellable:boolean,requiredUserAction:RequiredUserActionDto?,stateVersion:long,queuedAt:Instant,updatedAt:Instant`                                                                                                                                                 |
| `AgentRunDetailDto`           | `AgentRunSummaryDto` + `retryOfRunId:UUID?,rootRunId:UUID,runAttemptNo:int >=1,durationMs:long? >=0,startedAt:Instant?,completedAt:Instant?,safeError:SafeErrorDto?,partialResult:PartialResultDto?,steps:AgentStepDto[0..200]`                                                                                                                                                                                                                                                                                                                                                            |
| `DashboardDto`                | `generatedAt:Instant`, `profile:{completed:boolean,completionPercent:int 0..100,missingItems:ProfileCompletionItem[0..5]}`(8.7 승인 뒤 포함), `documents:{processingCount:int >=0,needsActionCount:int >=0}`, `jobs:{inProgressCount:int >=0,submittedCount:int >=0,closingSoon:JobSummaryDto[0..5]}`, `coverLetters:{warningCount:int >=0,recent:CoverLetterSummaryDto[0..5]}`, `mockInterviews:{recent:MockSessionSummaryDto[0..5]}`, `agentRuns:{activeCount:int >=0,recent:AgentRunSummaryDto[0..5]}`                                                                                  |
| `AiSettingsDto`               | `defaultQualityMode:ECONOMY\|BALANCED`, `highQualityEnabled:boolean`, `allowedQualityModes:AiQualityMode[1..3]`, `highQualityDisabledReason:string? <=500`, `dailyBudgetUsd:decimal >=0`, `systemMaxDailyBudgetUsd:decimal >=0`, `budgetResetZone:string 1..50`, `version:long`                                                                                                                                                                                                                                                                                                            |
| `PrivacySettingsDto`          | `termsAgreedAt:Instant,aiConsentAt:Instant,storedDocumentCount:int >=0,storedDocumentBytes:long >=0,promptBodyLoggingEnabled:false,documentDeletionPolicy:string 1..500,accountDeletionPolicy:string 1..500`                                                                                                                                                                                                                                                                                                                                                                               |

`educationStatus`는 `ENROLLED|LEAVE_OF_ABSENCE|EXPECTED_GRADUATION|GRADUATED|WITHDRAWN`, 문서 유형은 기존 6개 값을 그대로 사용한다. profile의 희망 직무·산업·지역은 MVP에서 별도 taxonomy service 없이 중복 없는 자유 text code(항목 1..100자, 배열별 최대 10개)로 저장한다.

### 4.4 인증·계정·Dashboard endpoint

| Method·path                   | 인증/CSRF        | request·validation                                                                                                                                     | version/I | 성공                                                                  | 주요 오류                                  | 처리·Agent Run                      | Frontend                           |
| ----------------------------- | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | --------- | --------------------------------------------------------------------- | ------------------------------------------ | ----------------------------------- | ---------------------------------- |
| `GET /auth/csrf`              | 익명 허용/불필요 | 없음                                                                                                                                                   | 없음      | 200 `CsrfDto`, anonymous session cookie                               | 500                                        | 동기/없음                           | signup·login bootstrap             |
| `POST /auth/signup`           | 익명/필수        | `email:string 3..320` 소문자 정규화, `password:string 10..72 UTF-8 bytes`, `displayName:string 1..100`, `termsAgreed=true`, `aiConsent=true` 모두 필수 | 없음      | 201 `AuthSessionDto`; user+profile+인증 session 한 transaction        | 400, 409 `EMAIL_ALREADY_REGISTERED`        | 동기/없음                           | `/signup`→`/onboarding`            |
| `POST /auth/login`            | 익명/필수        | `email:string 3..320`, `password:string 1..72 bytes` 필수                                                                                              | 없음      | 200 `AuthSessionDto`; session rotation                                | 400, 401 `INVALID_CREDENTIALS`             | 동기/없음                           | `/login`, `returnTo`               |
| `POST /auth/logout`           | Session/필수     | body 없음                                                                                                                                              | 없음      | 204                                                                   | 401/403                                    | 동기/없음                           | header·settings; cache/draft purge |
| `GET /auth/me`                | Session/불필요   | 없음                                                                                                                                                   | 없음      | 200 `CurrentUserDto`                                                  | 401                                        | 동기/없음                           | guard·header                       |
| `PATCH /account/display-name` | Session/필수     | `displayName:string 1..100` 필수                                                                                                                       | 없음      | 200 `CurrentUserDto`                                                  | 400/401/403                                | 동기/없음                           | `/settings/account`                |
| `PATCH /account/password`     | Session/필수     | `currentPassword:string 1..72 bytes`, `newPassword:string 10..72 bytes`, 서로 다름                                                                     | 없음      | 204; 다른 session 전부 폐기하고 현재 session만 rotate                 | 400/401/403, 409 `RESOURCE_STATE_CONFLICT` | 동기/없음                           | `/settings/account`                |
| `DELETE /account`             | Session/필수     | JSON `currentPassword:string 1..72 bytes`                                                                                                              | 없음      | 202 `AccountDeletionAcceptedDto`; 즉시 `WITHDRAWN`, 모든 session 폐기 | 400/401/403/409                            | 비동기 deletion task/Agent Run 없음 | `/settings/account`, 접수 receipt  |
| `GET /dashboard`              | Session/불필요   | 없음                                                                                                                                                   | 없음      | 200 `DashboardDto`                                                    | 401                                        | 동기 read projection/없음           | `/dashboard`                       |

signup/login 성공 뒤 응답의 새 CSRF 값을 client가 교체한다. 별도 재조회도 허용하지만 이전 anonymous token을 재사용하지 않는다. signup 성공은 인증 session을 만든 뒤 `/onboarding`으로 이동한다. 로그인과 이후 기능 진입은 미승인 `profileCompleted` 공식으로 차단하지 않으며, 완료 항목·경고 강도는 8.7 승인 뒤 `ProfileDto`·Dashboard projection에 함께 반영한다.

### 4.5 프로필 endpoint

공통 write schema:

- `ProfileWrite`: `legalName:string? 1..100`, `introduction:string? <=2000`, `desiredRoles/desiredIndustries/desiredLocations:string[] 0..10`(항목 1..100, 중복 금지), `expectedGraduationDate:LocalDate?`, PUT에는 `version:long` 필수.
- `EducationWrite`: `schoolName 1..200`, `major? <=200`, `degree? <=100`, `educationStatus` 필수, `admissionDate?`, `graduationDate? >= admissionDate`, `gpa? 0..10`, `gpaScale? 0.01..10`, 둘 다 있으면 `gpa<=gpaScale`, `isPrimary:boolean`, `description? <=5000`.
- `CertificationWrite`: `name 1..200`, `issuer? <=200`, `credentialNumber? <=200`, `acquiredDate?`, `expiresAt? >= acquiredDate`, `description? <=5000`, `evidenceDocumentId:UUID?`.
- `LanguageScoreWrite`: `testName 1..100`, `score 1..100`, `grade? <=100`, `testedAt?`, `expiresAt? >= testedAt`, `evidenceDocumentId:UUID?`.
- `AwardWrite`: `name 1..200`, `organizer? <=200`, `awardedAt?`, `description? <=5000`, `evidenceDocumentId:UUID?`.
- `CareerWrite`: `organization 1..200`, `position? <=200`, `employmentType? <=50`, `startedAt?`, `endedAt?`, `isCurrent`; current면 endedAt null, 아니면 두 날짜가 있을 때 endedAt>=startedAt; `responsibilities/achievements? <=20000`.
- POST에는 version이 없고 PUT에는 `version:long`을 추가한다. 모든 연결 document는 같은 사용자여야 한다.

| Method·path                                         | 인증/CSRF      | request                                              | version/I     | 성공                                 | 주요 오류                 | 처리                              | Frontend                     |
| --------------------------------------------------- | -------------- | ---------------------------------------------------- | ------------- | ------------------------------------ | ------------------------- | --------------------------------- | ---------------------------- |
| `GET /profile`                                      | Session/아니요 | 없음                                                 | 없음          | 200 `ProfileDto`                     | 401                       | 동기                              | onboarding, `/profile/basic` |
| `PUT /profile`                                      | Session/예     | `ProfileWrite`                                       | body version  | 200 `ProfileDto`                     | 400/401/403/409 version   | 동기                              | onboarding, basic            |
| `GET /profile/educations`                           | Session/아니요 | `page,size,sort=createdAt,desc\|graduationDate,desc` | 없음          | 200 `PageResponse<EducationDto>`     | 400/401                   | 동기                              | education                    |
| `POST /profile/educations`                          | Session/예     | `EducationWrite`                                     | 없음          | 201 `EducationDto`                   | 400/401/403/409 대표 학력 | 동기                              | education                    |
| `PUT /profile/educations/{educationId}`             | Session/예     | `EducationWrite+version`                             | body version  | 200 `EducationDto`                   | 400/404/409               | 동기                              | education                    |
| `DELETE /profile/educations/{educationId}`          | Session/예     | `version:long` query                                 | query version | 204                                  | 404/409                   | 동기; 연결 direct evidence 동기화 | education                    |
| `GET /profile/certifications`                       | Session/아니요 | `page,size,sort=acquiredDate,desc\|createdAt,desc`   | 없음          | 200 `PageResponse<CertificationDto>` | 400/401                   | 동기                              | certifications               |
| `POST /profile/certifications`                      | Session/예     | `CertificationWrite`                                 | 없음          | 201 `CertificationDto`               | 400/404 document          | 동기                              | certifications               |
| `PUT /profile/certifications/{certificationId}`     | Session/예     | `CertificationWrite+version`                         | body version  | 200 `CertificationDto`               | 400/404/409               | 동기                              | certifications               |
| `DELETE /profile/certifications/{certificationId}`  | Session/예     | version query                                        | query version | 204                                  | 404/409                   | 동기                              | certifications               |
| `GET /profile/language-scores`                      | Session/아니요 | `page,size,sort=testedAt,desc\|createdAt,desc`       | 없음          | 200 `PageResponse<LanguageScoreDto>` | 400/401                   | 동기                              | languages                    |
| `POST /profile/language-scores`                     | Session/예     | `LanguageScoreWrite`                                 | 없음          | 201 `LanguageScoreDto`               | 400/404 document          | 동기                              | languages                    |
| `PUT /profile/language-scores/{languageScoreId}`    | Session/예     | `LanguageScoreWrite+version`                         | body version  | 200 `LanguageScoreDto`               | 400/404/409               | 동기                              | languages                    |
| `DELETE /profile/language-scores/{languageScoreId}` | Session/예     | version query                                        | query version | 204                                  | 404/409                   | 동기                              | languages                    |
| `GET /profile/awards`                               | Session/아니요 | `page,size,sort=awardedAt,desc\|createdAt,desc`      | 없음          | 200 `PageResponse<AwardDto>`         | 400/401                   | 동기                              | awards                       |
| `POST /profile/awards`                              | Session/예     | `AwardWrite`                                         | 없음          | 201 `AwardDto`                       | 400/404 document          | 동기                              | awards                       |
| `PUT /profile/awards/{awardId}`                     | Session/예     | `AwardWrite+version`                                 | body version  | 200 `AwardDto`                       | 400/404/409               | 동기                              | awards                       |
| `DELETE /profile/awards/{awardId}`                  | Session/예     | version query                                        | query version | 204                                  | 404/409                   | 동기                              | awards                       |
| `GET /profile/careers`                              | Session/아니요 | `page,size,sort=startedAt,desc\|createdAt,desc`      | 없음          | 200 `PageResponse<CareerDto>`        | 400/401                   | 동기                              | careers                      |
| `POST /profile/careers`                             | Session/예     | `CareerWrite`                                        | 없음          | 201 `CareerDto`                      | 400                       | 동기                              | careers                      |
| `PUT /profile/careers/{careerId}`                   | Session/예     | `CareerWrite+version`                                | body version  | 200 `CareerDto`                      | 400/404/409               | 동기                              | careers                      |
| `DELETE /profile/careers/{careerId}`                | Session/예     | version query                                        | query version | 204                                  | 404/409                   | 동기                              | careers                      |

구조화 profile row 생성·수정·삭제와 연결 `profile_evidence` 동기화는 한 transaction이다. 직접 입력 evidence는 `VERIFIED`; 원본 row 삭제 시 과거 생성물이 참조하지 않으면 evidence 삭제, 참조하면 승인된 삭제 정책에 따라 tombstone 처리한다.

### 4.6 문서·근거 endpoint

| Method·path                                         | 인증/CSRF      | request·validation                                                                                                 | version/I        | 성공                                             | 주요 오류                     | 처리·Agent Run                              | Frontend                      |
| --------------------------------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------ | ---------------- | ------------------------------------------------ | ----------------------------- | ------------------------------------------- | ----------------------------- |
| `POST /documents`                                   | Session/예     | multipart `file` 1 byte..20 MiB PDF/DOCX/TXT, `documentType` 필수, `displayName? 1..255`                           | I                | 202 `DocumentUploadAcceptedDto`                  | 400/413/415/429/503           | 비동기 `DOCUMENT_INGESTION`                 | `/documents`, onboarding      |
| `GET /documents`                                    | Session/아니요 | `documentType?`, `parseStatus?`, `evidenceExtractionStatus?`, page,size, `sort=uploadedAt,desc\|updatedAt,desc`    | 없음             | 200 `PageResponse<DocumentSummaryDto>`           | 400/401                       | 동기                                        | `/documents`, dashboard       |
| `GET /documents/{documentId}`                       | Session/아니요 | 없음                                                                                                               | 없음             | 200 `DocumentDetailDto`                          | 404                           | 동기                                        | document detail               |
| `GET /documents/{documentId}/text`                  | Session/아니요 | 없음                                                                                                               | 없음             | 200 `DocumentTextDto`; 원문 파일은 반환하지 않음 | 404/409 text unavailable      | 동기                                        | manual text/preview           |
| `PUT /documents/{documentId}/manual-text`           | Session/예     | `text:string` 정규화 후 비공백 code point 100..500000, `version:long`                                              | body version + I | 202 `RunAcceptedDto`; waiting이면 같은 run ID    | 400/404/409/429               | 비동기 resume/`DOCUMENT_INGESTION`          | detail manual editor          |
| `POST /documents/{documentId}/reparse`              | Session/예     | `{version:long}`                                                                                                   | body version + I | 202 새 `RunAcceptedDto`                          | 404/409 active processing/429 | 비동기 새 `DOCUMENT_INGESTION`              | document action               |
| `POST /documents/{documentId}/download-url`         | Session/예     | body 없음                                                                                                          | 없음             | 200 `DownloadUrlDto`, TTL 5분                    | 404/409 deleted/503           | 동기 Object presign/없음                    | document action               |
| `DELETE /documents/{documentId}`                    | Session/예     | `version` query                                                                                                    | query version    | 204                                              | 404/409                       | 동기 logical delete+outbox; Object는 비동기 | document action               |
| `GET /profile/evidence`                             | Session/아니요 | `verificationStatus?`, `evidenceCategory? 1..80`, `documentId?`, page,size, `sort=updatedAt,desc\|confidence,desc` | 없음             | 200 `PageResponse<EvidenceDto>`                  | 400/401/404 document          | 동기                                        | evidence page/document detail |
| `PUT /profile/evidence/{evidenceId}`                | Session/예     | `title 1..250`, `content 1..20000`, `metadata:object <=16KiB`, `version`                                           | body version     | 200 `EvidenceDto`; status는 유지                 | 400/404/409                   | 동기                                        | evidence edit                 |
| `PATCH /profile/evidence/{evidenceId}/verification` | Session/예     | `status=VERIFIED\|REJECTED`, `version`                                                                             | body version     | 200 `EvidenceDto`                                | 400/404/409/SOURCE_DELETED    | 동기                                        | approve/reject                |

`PARSED + evidenceExtractionStatus=FAILED`에서는 text preview와 evidence retry를 제공하고 reparse를 강요하지 않는다. 별도 evidence-only 공개 endpoint는 MVP에 추가하지 않고 `POST /documents/{documentId}/reparse`가 같은 source revision이면 성공 parser step을 hash로 재사용한다.

### 4.7 공고 endpoint

| Method·path                           | 인증/CSRF      | request·validation                                                                                                                    | version/I        | 성공                                                                | 주요 오류                           | 처리·Agent Run                               | Frontend                |
| ------------------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ---------------- | ------------------------------------------------------------------- | ----------------------------------- | -------------------------------------------- | ----------------------- |
| `POST /jobs`                          | Session/예     | `sourceUrl` absolute HTTP(S) 1..2000, `companyName? <=200`, `positionName? <=300`, `descriptionText? <=200000`, `deadlineAt:Instant?` | I                | 202 `{jobId,status:IN_PROGRESS,extractionStatus:QUEUED,agentRunId}` | 400/409 `DUPLICATE_JOB_URL`/429/503 | 비동기 `JOB_POSTING_EXTRACTION`              | `/jobs/new`             |
| `GET /jobs`                           | Session/아니요 | `status?`, `extractionStatus?`, `query? <=200`, `deadlineFrom?`, `deadlineTo?`, `deadlineWithinDays? 1..30`, page,size, 기존 3 sort   | 없음             | 200 `PageResponse<JobSummaryDto>`                                   | 400/401                             | 동기                                         | `/jobs`, dashboard      |
| `GET /jobs/{jobId}`                   | Session/아니요 | 없음                                                                                                                                  | 없음             | 200 `JobDetailDto`                                                  | 404                                 | 동기                                         | job child routes        |
| `PUT /jobs/{jobId}`                   | Session/예     | `companyName? <=200`, `title? <=300`, `positionName? <=300`, `descriptionText? <=200000`, `deadlineAt?`, `version`                    | body version     | 200 `JobDetailDto`; 수동 본문은 MANUAL_INPUT_PROVIDED               | 400/404/409                         | 동기; waiting extraction이면 same run resume | overview edit           |
| `PATCH /jobs/{jobId}/status`          | Session/예     | `status:JobStatus`, `version`                                                                                                         | body version     | 200 `JobDetailDto` summary fields                                   | 400/404/409 state/version           | 동기 status+history                          | jobs/overview           |
| `POST /jobs/{jobId}/retry-extraction` | Session/예     | `{version}`                                                                                                                           | body version + I | 202 waiting이면 same run, terminal이면 새 `RunAcceptedDto`          | 404/409/429/503                     | 비동기 `JOB_POSTING_EXTRACTION`              | overview failure CTA    |
| `POST /jobs/{jobId}/analysis`         | Session/예     | `qualityMode=ECONOMY\|BALANCED`, `forceReanalyze:boolean`, `jobVersion:long`                                                          | body version + I | 202 `RunAcceptedDto`                                                | 400/404/409 not ready/429/503       | 비동기 `JOB_ANALYSIS`                        | analysis tab            |
| `GET /jobs/{jobId}/analyses`          | Session/아니요 | page,size, `sort=analysisVersion,desc\|createdAt,desc`                                                                                | 없음             | 200 `PageResponse<JobAnalysisSummaryDto>`                           | 404                                 | 동기                                         | analysis history        |
| `GET /jobs/{jobId}/analyses/latest`   | Session/아니요 | 없음                                                                                                                                  | 없음             | 200 `JobAnalysisDetailDto`                                          | 404 job/`JOB_ANALYSIS_NOT_FOUND`    | 동기                                         | analysis tab/downstream |
| `DELETE /jobs/{jobId}`                | Session/예     | version query                                                                                                                         | query version    | 204 soft delete                                                     | 404/409 active commands             | 동기                                         | overview                |

canonicalization은 scheme·host 소문자/IDNA, fragment·default port 제거, path 정규화, 알려진 tracking query 제거와 나머지 query 정렬을 포함한다. fetch는 DNS와 redirect마다 private/link-local/loopback을 차단한다. `submittedAt`은 최초 SUBMITTED에서만 설정하고 영구 보존한다. reopen은 현재 `closedAt/closedReason`만 null로 만들고 history를 보존한다.

### 4.8 자기소개서 endpoint

TipTap `contentJson` allowlist는 `doc,paragraph,text,hardBreak,bulletList,orderedList,listItem` node와 `bold,italic` mark뿐이며 4.3의 recursive DTO 규칙을 적용한다. HTML·image·embed·script·임의 link를 거부한다. 서버는 CRLF→LF, NBSP→space, Unicode NFC 후 plain text의 Unicode code point를 세며 공백·줄바꿈은 포함하고 markup·zero-width 문자는 제외한다. 서버 count가 authoritative다.

| Method·path                                                              | 인증/CSRF      | request·validation                                                                                                                                                              | version/I              | 성공                                                                   | 주요 오류                     | 처리·Agent Run                     | Frontend                  |
| ------------------------------------------------------------------------ | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------- | ---------------------------------------------------------------------- | ----------------------------- | ---------------------------------- | ------------------------- |
| `POST /jobs/{jobId}/cover-letter`                                        | Session/예     | `title:string 1..300`                                                                                                                                                           | I                      | 201 `CoverLetterDetailDto` DRAFT                                       | 404/409 active                | 동기                               | job cover tab             |
| `GET /cover-letters`                                                     | Session/아니요 | `jobId?`, `status?`, `query? <=200`, page,size, `sort=updatedAt,desc\|createdAt,desc\|title,asc`                                                                                | 없음                   | 200 `PageResponse<CoverLetterSummaryDto>`                              | 400/401                       | 동기                               | `/cover-letters`, job tab |
| `GET /cover-letters/{coverLetterId}`                                     | Session/아니요 | 없음                                                                                                                                                                            | 없음                   | 200 `CoverLetterDetailDto`                                             | 404                           | 동기                               | editor/read-only archive  |
| `PUT /cover-letters/{coverLetterId}`                                     | Session/예     | `title 1..300`, `version`; status 입력 금지                                                                                                                                     | body version           | 200 `CoverLetterDetailDto`                                             | 400/404/409                   | 동기                               | editor title              |
| `POST /cover-letters/{coverLetterId}/questions`                          | Session/예     | `questionOrder 1..20`, `questionText 1..2000`, `maxLength? 1..10000`, `memo? <=2000`, `coverLetterVersion`                                                                      | aggregate version      | 201 `CoverLetterQuestionDto`; FINALIZED면 DRAFT                        | 400/404/409/archive           | 동기                               | question navigator        |
| `PUT /cover-letters/{coverLetterId}/questions/{questionId}`              | Session/예     | 위 필드 + `version`(question)                                                                                                                                                   | body version           | 200 `CoverLetterQuestionDto`; FINALIZED면 DRAFT                        | 400/404/409/archive           | 동기                               | question edit             |
| `DELETE /cover-letters/{coverLetterId}/questions/{questionId}`           | Session/예     | `version` query                                                                                                                                                                 | query question version | 204 soft delete; FINALIZED면 DRAFT                                     | 404/409/archive               | 동기                               | question delete           |
| `PATCH /cover-letters/{coverLetterId}/questions/order`                   | Session/예     | `questionIds:UUID[] 1..20` 전체 active set, `version`(cover letter)                                                                                                             | aggregate version      | 200 `CoverLetterDetailDto`                                             | 400/404/409                   | 동기                               | drag reorder              |
| `POST /cover-letters/{coverLetterId}/generate`                           | Session/예     | `questionIds:UUID[] 1..20 unique`, `preferredEvidenceIds:UUID[] 0..50 unique VERIFIED`, `qualityMode:AiQualityMode`, `avoidExperienceDuplication:boolean`, `coverLetterVersion` | body version + I       | 202 `RunAcceptedDto`; current가 없으면 AI_GENERATED, 있으면 AI_REVISED | 400/404/409/archive/429/503   | 비동기 `COVER_LETTER_GENERATION`   | editor generation         |
| `GET /cover-letter-questions/{questionId}/versions`                      | Session/아니요 | page,size, `sort=versionNo,desc\|createdAt,desc`                                                                                                                                | 없음                   | 200 `PageResponse<CoverLetterAnswerVersionDto>`                        | 404                           | 동기                               | version drawer            |
| `POST /cover-letter-questions/{questionId}/versions`                     | Session/예     | `contentJson:TipTapDocumentDto`, `parentVersionId:UUID?`(현재와 일치, 최초만 null)                                                                                              | current CAS            | 201 `CoverLetterAnswerVersionDto` USER_EDITED; FINALIZED면 DRAFT       | 400/404/409/archive/maxLength | 동기                               | explicit save             |
| `POST /cover-letter-questions/{questionId}/versions/{versionId}/restore` | Session/예     | `expectedCurrentVersionId:UUID?`                                                                                                                                                | current CAS            | 201 새 RESTORED version; `restoredFromVersionId=path version`          | 404/409/archive               | 동기                               | version restore           |
| `POST /cover-letter-answer-versions/{versionId}/verify`                  | Session/예     | `qualityMode:AiQualityMode`                                                                                                                                                     | I                      | 202 `RunAcceptedDto`                                                   | 404/429/503                   | 비동기 `COVER_LETTER_VERIFICATION` | verification panel        |
| `GET /cover-letter-answer-versions/{versionId}/verifications`            | Session/아니요 | page,size, `sort=createdAt,desc`                                                                                                                                                | 없음                   | 200 `PageResponse<VerificationDto>`                                    | 404                           | 동기                               | verification history      |
| `POST /cover-letters/{coverLetterId}/finalize`                           | Session/예     | `version`, `acknowledgedWarningVerificationIds:UUID[] 0..20`                                                                                                                    | body version           | 200 `CoverLetterDetailDto` FINALIZED                                   | 404/409 not finalizable       | 동기                               | editor finalize           |
| `POST /cover-letters/{coverLetterId}/archive`                            | Session/예     | `version`                                                                                                                                                                       | body version           | 200 `CoverLetterDetailDto` ARCHIVED                                    | 404/409                       | 동기                               | list/editor               |
| `POST /cover-letters/{coverLetterId}/unarchive`                          | Session/예     | `version`                                                                                                                                                                       | body version           | 200 `CoverLetterDetailDto` DRAFT                                       | 404/409 active                | 동기                               | archived list/detail      |

질문별 generation 결과는 각각 짧은 transaction으로 반영한다. 일부 실패하면 성공 version은 보존하고 run은 `FAILED`, 성공·실패 question ID 문자열은 `AgentRunDetailDto.partialResult.succeededScopeKeys/failedScopeKeys`에 두며 생성된 version은 `resultRefs`로 반환한다. retry 새 run은 성공 scope를 `REUSED`하고 실패 scope만 실행한다. finalization은 모든 active 질문의 current answer 존재·길이 준수·해당 current version의 최신 verification이 PASSED 또는 명시 승인된 WARNING일 때만 가능하다.

### 4.9 면접 준비·조사·예상 질문 endpoint

| Method·path                                              | 인증/CSRF      | request·validation                                                                                                                                                            | version/I   | 성공                                                                   | 주요 오류                                    | 처리·Agent Run                     | Frontend                |
| -------------------------------------------------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ---------------------------------------------------------------------- | -------------------------------------------- | ---------------------------------- | ----------------------- |
| `POST /jobs/{jobId}/interview-preparations`              | Session/예     | `coverLetterId:UUID`, `researchQuality:BASIC\|ADVANCED`, `qualityMode:ECONOMY\|BALANCED`, `questionTypes:InterviewQuestionType[] 1..7`(FOLLOW_UP 금지), `questionCount 1..20` | I           | 202 `{questionSetId,researchRunId,agentRunId,status:QUEUED}`           | 400/404 cross-owner/409 prerequisite/429/503 | 비동기 `INTERVIEW_PREPARATION`     | job interview tab       |
| `GET /interview-question-sets`                           | Session/아니요 | `jobId?`, `coverLetterId?`, `query? <=200`, `sourceCoverage?`, `researchStatus?`, page,size, `sort=updatedAt,desc\|createdAt,desc`                                            | 없음        | 200 `PageResponse<QuestionSetSummaryDto>`                              | 400/401/404 parent                           | 동기                               | `/interviews`, job tab  |
| `GET /interview-question-sets/{questionSetId}`           | Session/아니요 | 없음                                                                                                                                                                          | 없음        | 200 `QuestionSetDetailDto`                                             | 404                                          | 동기                               | question set detail     |
| `GET /research-runs/{researchRunId}`                     | Session/아니요 | 없음                                                                                                                                                                          | 없음        | 200 `ResearchRunDto`                                                   | 404                                          | 동기                               | research status/summary |
| `GET /research-runs/{researchRunId}/sources`             | Session/아니요 | `topic?`, `sourceType?`, page,size, `sort=providerRank,asc\|retrievedAt,desc`                                                                                                 | 없음        | 200 `PageResponse<ResearchSourceDto>`                                  | 400/404                                      | 동기                               | source list             |
| `POST /research-runs/{researchRunId}/retry`              | Session/예     | `researchQuality?:BASIC\|ADVANCED`(없으면 기존), `qualityMode?:ECONOMY\|BALANCED`(없으면 기존)                                                                                | I           | 202 새 `{questionSetId,researchRunId,agentRunId,retryOfResearchRunId}` | 400 quality/404/409 not retryable/429/503    | 비동기 새 `INTERVIEW_PREPARATION`  | failed research CTA     |
| `GET /interview-questions/{questionId}`                  | Session/아니요 | 없음                                                                                                                                                                          | 없음        | 200 `InterviewQuestionDto`                                             | 404                                          | 동기                               | question card/detail    |
| `GET /interview-questions/{questionId}/answer-versions`  | Session/아니요 | page,size, `sort=versionNo,desc\|createdAt,desc`                                                                                                                              | 없음        | 200 `PageResponse<InterviewAnswerVersionDto>`                          | 404                                          | 동기                               | answer history          |
| `POST /interview-questions/{questionId}/answer-versions` | Session/예     | `content:string 1..20000`, `parentVersionId:UUID?`(current CAS)                                                                                                               | current CAS | 201 `InterviewAnswerVersionDto` USER_EDITED                            | 400/404/409                                  | 동기                               | answer editor           |
| `POST /interview-answer-versions/{versionId}/feedback`   | Session/예     | `qualityMode:AiQualityMode`                                                                                                                                                   | I           | 202 `RunAcceptedDto`                                                   | 404/429/503                                  | 비동기 `INTERVIEW_ANSWER_FEEDBACK` | feedback request        |
| `GET /interview-answer-versions/{versionId}/feedbacks`   | Session/아니요 | page,size, `sort=createdAt,desc`                                                                                                                                              | 없음        | 200 `PageResponse<InterviewFeedbackDto>`                               | 404                                          | 동기                               | feedback history        |

준비 command의 선행 조건은 최신 공고 분석 존재와 선택 cover letter의 active 질문·current answer 최소 1개다. research retry는 기존 source/question을 덮어쓰지 않고 새 research run·question set·Agent Run을 만들며 lineage를 남긴다. source 목록의 URL은 2000자, title 500자, snippet 2000자 상한이다.

`INTERVIEW_ANSWER_FEEDBACK`은 성공 결과를 적용하는 transaction에서만 immutable feedback row를 만든다. run이 실패·취소되면 feedback row를 만들지 않고 조회 목록에는 과거 성공 row만 반환한다. 진행·실패·취소 상태와 재시도 가능 여부는 접수 응답의 Agent Run에서 조회하므로 DB 명세에 없는 `InterviewFeedbackStatus`와 영구 `PENDING` projection을 만들지 않는다.

### 4.10 모의 면접 endpoint

canonical 생성 route는 `/jobs/:jobId/interview/mock/new`다. `/interviews`의 빠른 생성도 먼저 공고를 고른 뒤 이 route로 이동한다. 주요 프로젝트는 새 aggregate나 자유 text가 아니라 같은 사용자의 `VERIFIED` evidence ID 최대 5개다.

| Method·path                                          | 인증/CSRF      | request·validation                                                                                                                                                                                                                                                            | version/I             | 성공                                                                       | 주요 오류                                 | 처리·Agent Run                   | Frontend               |
| ---------------------------------------------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------- | -------------------------------------------------------------------------- | ----------------------------------------- | -------------------------------- | ---------------------- |
| `POST /mock-interview-sessions`                      | Session/예     | `jobId:UUID,coverLetterId:UUID` 필수, `questionSetId:UUID?`, `interviewType:MockInterviewType`, `difficulty:MockDifficulty`, `targetQuestionCount:int 1..20`, `feedbackTiming:MockFeedbackTiming`, `pressureMode:boolean`, `preferredEvidenceIds:UUID[] 0..5 unique VERIFIED` | I                     | 201 `MockSessionSummaryDto` READY                                          | 400/404 cross-owner/409 prerequisite      | 동기 resource create/없음        | mock create route      |
| `POST /mock-interview-sessions/{sessionId}/start`    | Session/예     | `clientRequestId:UUID`, `version:long`                                                                                                                                                                                                                                        | session CAS+client ID | 200 `MockTurnResponseDto`, IN_PROGRESS                                     | 404/409 state/version/in-progress/429/503 | bounded 동기 turn/Agent Run 없음 | READY page             |
| `POST /mock-interview-sessions/{sessionId}/messages` | Session/예     | `clientRequestId:UUID`, `content:string 1..5000`, `version:long`                                                                                                                                                                                                              | session CAS+client ID | 200 `MockTurnResponseDto`; same ID/hash replay                             | 400/404/409/429/503                       | bounded 동기 turn/Agent Run 없음 | IN_PROGRESS chat       |
| `POST /mock-interview-sessions/{sessionId}/complete` | Session/예     | `version:long`                                                                                                                                                                                                                                                                | session version + I   | 202 `{sessionId,sessionStatus:COMPLETED,feedbackStatus:QUEUED,agentRunId}` | 404/409                                   | 비동기 `MOCK_INTERVIEW_FEEDBACK` | end action             |
| `POST /mock-interview-sessions/{sessionId}/cancel`   | Session/예     | `version:long`                                                                                                                                                                                                                                                                | session version       | 200 `MockSessionSummaryDto` CANCELLED                                      | 404/409                                   | 동기; pending turn result 폐기   | cancel action          |
| `GET /mock-interview-sessions`                       | Session/아니요 | `jobId?`, `query? <=200`, `status?`, `feedbackStatus?`, page,size, `sort=createdAt,desc\|completedAt,desc\|updatedAt,desc`                                                                                                                                                    | 없음                  | 200 `PageResponse<MockSessionSummaryDto>`                                  | 400/401                                   | 동기                             | `/interviews`, job tab |
| `GET /mock-interview-sessions/{sessionId}`           | Session/아니요 | 없음                                                                                                                                                                                                                                                                          | 없음                  | 200 `MockSessionSummaryDto`                                                | 404                                       | 동기                             | mock page              |
| `GET /mock-interview-sessions/{sessionId}/messages`  | Session/아니요 | page,size(기본 100, 최대 100), `sort=sequenceNo,asc`                                                                                                                                                                                                                          | 없음                  | 200 `PageResponse<MockMessageDto>`                                         | 404                                       | 동기                             | transcript             |
| `GET /mock-interview-sessions/{sessionId}/feedbacks` | Session/아니요 | 없음                                                                                                                                                                                                                                                                          | 없음                  | 200 `MockFeedbackDto`; pending 전에도 200                                  | 404                                       | 동기                             | completed feedback     |

turn request는 unique `(user_id,session_id,client_request_id)`와 5.2의 HMAC request hash를 갖는다. 동일 ID·동일 hash가 완료됐으면 저장 응답을 replay하고, 처리 중이면 `409 MOCK_TURN_IN_PROGRESS`, 다른 hash면 `409 IDEMPOTENCY_KEY_REUSED`다. user message와 pending turn을 먼저 commit하고 외부 호출은 transaction 밖에서 수행한 뒤 session version CAS로 interviewer message를 반영한다.

### 4.11 Agent Run·SSE endpoint

| Method·path                            | 인증/CSRF      | request·validation                                                                                                                              | version/I | 성공                                                      | 주요 오류                | 처리               | Frontend              |
| -------------------------------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | --------- | --------------------------------------------------------- | ------------------------ | ------------------ | --------------------- |
| `GET /agent-runs`                      | Session/아니요 | repeatable `workflowType?`, repeatable `status?`, `resourceType?`, `resourceId?`, `retryable?`, page,size, `sort=queuedAt,desc\|updatedAt,desc` | 없음      | 200 `PageResponse<AgentRunSummaryDto>`                    | 400/401/404 resource     | 동기               | `/agent-runs`, drawer |
| `GET /agent-runs/{agentRunId}`         | Session/아니요 | 없음                                                                                                                                            | 없음      | 200 `AgentRunDetailDto`                                   | 404                      | 동기 snapshot      | run detail            |
| `GET /agent-runs/{agentRunId}/events`  | Session/아니요 | `Accept:text/event-stream`; `Last-Event-ID`가 와도 replay하지 않음                                                                              | 없음      | 200 SSE, 연결 직후 snapshot                               | 401/404                  | best-effort stream | drawer/detail         |
| `POST /agent-runs/{agentRunId}/retry`  | Session/예     | body 없음; FAILED/INTERRUPTED+retryable만                                                                                                       | I         | 202 새 `RunAcceptedDto`, `retryOfRunId`는 detail에서 확인 | 404/409/429              | 새 run enqueue     | failed run action     |
| `POST /agent-runs/{agentRunId}/cancel` | Session/예     | `stateVersion:long`                                                                                                                             | state CAS | 202 `AgentRunDetailDto` cancel requested                  | 404/409 terminal/version | cooperative cancel | active run action     |

resource별 retry endpoint는 품질 등 retry option을 바꾸는 command이고, 범용 Agent Run retry는 원 요청 snapshot을 그대로 반복하는 command다. 둘은 같은 retry service와 unique predecessor claim을 사용해 같은 실패 attempt에 successor run을 하나만 만든다. research retry처럼 workflow가 새 domain output을 요구하면 어느 진입점을 사용해도 같은 새 resource cardinality와 lineage 규칙을 적용한다.

SSE event 계약:

| event          | payload                                                                                                                |
| -------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `snapshot`     | `{agentRunId,stateVersion,occurredAt,run:AgentRunDetailDto}`                                                           |
| `progress`     | `{agentRunId,stateVersion,occurredAt,status,currentStep,progressPercent,actualCostUsd}`                                |
| `step`         | `{agentRunId,stateVersion,occurredAt,step:AgentStepDto}`                                                               |
| `waiting_user` | `{agentRunId,stateVersion,occurredAt,requiredUserAction}`                                                              |
| `heartbeat`    | `{agentRunId,stateVersion,occurredAt,serverTime,status}`                                                               |
| `terminal`     | `{agentRunId,stateVersion,occurredAt,status,completedAt,actualCostUsd,retryable,safeError?,resourceType?,resourceId?}` |

commit된 상태마다 `stateVersion`이 증가하며 event `id`로도 사용한다. heartbeat는 15초다. durable event log와 replay는 만들지 않는다. client는 재연결 시 새 snapshot을 받고 낮거나 같은 stateVersion event를 무시하며, 1/2/5/10/30초 backoff와 3회 실패 뒤 5초 REST polling을 사용한다. terminal snapshot을 확인하면 stream을 닫고 연관 resource query를 invalidate한다.

### 4.12 설정 endpoint

| Method·path             | 인증/CSRF      | request·validation                                                                                                         | version/I    | 성공                     | 주요 오류 | 처리            | Frontend            |
| ----------------------- | -------------- | -------------------------------------------------------------------------------------------------------------------------- | ------------ | ------------------------ | --------- | --------------- | ------------------- |
| `GET /settings/ai`      | Session/아니요 | 없음                                                                                                                       | 없음         | 200 `AiSettingsDto`      | 401       | 동기            | `/settings/ai`      |
| `PUT /settings/ai`      | Session/예     | `defaultQualityMode=ECONOMY\|BALANCED`, `highQualityEnabled:boolean`, `dailyBudgetUsd decimal 0.00..system max`, `version` | body version | 200 `AiSettingsDto`      | 400/409   | 동기            | `/settings/ai`      |
| `GET /settings/privacy` | Session/아니요 | 없음                                                                                                                       | 없음         | 200 `PrivacySettingsDto` | 401       | 동기 projection | `/settings/privacy` |

## 5. 데이터 및 수명주기 기준선

### 5.1 사용자 소유권과 FK

- 예외: `companies`, `ai_model_policies`·가격 catalog, Spring Session framework table처럼 전역 또는 framework 소유인 table과 5.7의 독립 `account_deletion_tasks`.
- 그 외 모든 사용자 콘텐츠 row는 `user_id uuid NOT NULL`과 `UNIQUE(user_id,id)`를 가진다.
- aggregate child도 user ID를 중복 보유한다. document text/chunk, cover question/version/link/verification, research source, interview child, mock message/feedback/turn, agent step/usage가 포함된다.
- child FK는 `(user_id,parent_id) → parent(user_id,id)`다. 중요 교차 참조인 profile↔document, cover↔job, answer↔evidence, research↔job/cover, question set↔job/cover/research, mock↔job/cover/question set, usage↔run/step도 동일하다.
- `profile_evidence(source_type,source_entity_id)`만 polymorphic FK 예외다. application transaction에서 source type별 `(id,user_id,deleted_at)` lookup을 하고, 실패는 404로 숨긴다.
- JSON의 evidence/source ID 배열은 표시 snapshot일 뿐 authoritative link가 아니다. `job_analysis_evidence_links`, `interview_question_evidence_links`, `interview_question_source_links`가 실제 provenance FK다.
- 일반 API delete는 soft delete 또는 explicit lifecycle command이고, account 최종 purge에서만 owner FK cascade를 사용한다.

### 5.2 Idempotency record

`idempotency_records` 필드:

```text
id, user_id
http_method, route_scope, resource_scope_id
idempotency_key, request_hash, hash_key_version, state(IN_PROGRESS|COMPLETED)
response_status, response_json
resource_type, resource_id, agent_run_id
created_at, completed_at, expires_at
```

- unique는 `(user_id,http_method,route_scope,resource_scope_id,idempotency_key)`이며 root operation은 nil UUID를 scope ID로 사용한다.
- request hash는 `HMAC-SHA-256(server idempotency key version, method|route scope|canonical JSON)`이다. upload canonical 값에는 file SHA-256와 documentType/displayName을 포함한다. 비밀번호·본문·파일 원문은 저장하지 않으며 HMAC key는 DB에 저장하지 않는다. 24시간 TTL 동안 이전 `hash_key_version` 검증 key를 유지한다.
- 완료 뒤 24시간 보존한다. IN_PROGRESS는 linked run이 terminal이 되기 전에 cleanup하지 않는다.
- 202 접수 자체를 완료 응답으로 저장하므로 재전송은 같은 run ID를 반환한다.

### 5.3 Object 삭제 Outbox

`object_deletion_outbox`는 `id,user_id,document_id,storage_key,reason,status,attempt_count,next_attempt_at,claim_token,lease_expires_at,last_error_code,created_at,completed_at`을 가진다. `(document_id,storage_key,reason)` active unique로 중복 삭제를 막는다.

- 상태: `PENDING→PROCESSING→SUCCEEDED|DEAD`; lease 만료 PROCESSING은 PENDING으로 회수한다.
- retry 간격: 1분, 5분, 30분, 2시간, 12시간, 이후 24시간; 최대 10회 뒤 DEAD+운영 경보.
- storage key와 provider 상세 오류는 client·일반 log에 노출하지 않는다.

### 5.4 Agent Run claim·lease·cancel·retry

`agent_runs`에는 `retry_of_run_id,root_run_id,run_attempt_no,requested_quality_mode,highest_model_tier_used,reserved_cost_usd,claim_token,claimed_by,lease_expires_at,heartbeat_at,cancel_requested_at,waiting_reason,state_version`을 추가한다. `agent_steps`에는 `user_id,scope_key,max_attempts,reused_step_id,input_hash,output_hash,output_schema_version`을 둔다.

- claim은 조건부 update 또는 `FOR UPDATE SKIP LOCKED`로 하나의 worker만 얻는다.
- heartbeat 15초, lease 60초, reconciliation 30초다. lease 만료 RUNNING은 immutable `INTERRUPTED`가 된다.
- cancel은 timestamp를 원자 기록하고 외부 호출 전후·domain apply 직전에 검사한다. 이미 발생한 provider 비용은 정산하되 결과는 적용하지 않는다.
- cancel 완료 transaction은 run/step을 `CANCELLED`로 만들면서 processing resource를 마지막 안정 상태로 함께 복원한다. Job extraction은 기존 usable source가 있으면 `EXTRACTED|MANUAL_INPUT_PROVIDED`, 없으면 `NEEDS_MANUAL_INPUT`; document parse는 같은 revision의 committed text/chunk가 있으면 `PARSED`, 없으면 `UPLOADED`; evidence extraction은 같은 revision의 prior 성공 snapshot이 있으면 `SUCCEEDED`, 없으면 `NOT_STARTED`다. reconciliation은 terminal run과 processing resource 불일치를 같은 규칙으로 복구한다.
- interview preparation 취소는 research run을 `CANCELLED`로 종결하고 preallocated question set은 질문·source link가 없는 read-only cancelled 결과로 남긴다. retry는 새 research run·question set을 만든다. interview answer feedback 취소는 feedback row를 만들지 않는다. mock feedback 취소는 session의 `feedbackStatus=CANCELLED`로 함께 바꾼다. cover verification 취소는 연결된 PENDING verification을 `FAILED`로 종결해 domain PENDING을 남기지 않는다.
- 자동 attempt는 같은 run 안에서 1..3이다. user retry는 새 run의 attempt를 1부터 시작하고 lineage를 보존한다.
- WAITING_USER는 같은 run을 QUEUED로 되돌린다. terminal run을 다시 열지 않는다.

### 5.5 문서·evidence·과거 provenance

- document metadata는 soft delete되어 API에서 즉시 404다.
- Object, `document_texts`, `document_chunks`, embedding은 물리 삭제한다.
- 과거 answer/interview 산출물이 참조한 evidence는 승인 정책에 따라 최소 snapshot 또는 tombstone으로 남기고 `SOURCE_DELETED`, `source_deleted_at`을 설정한다. 원문 page/chunk는 남기지 않는다.
- 미참조 document evidence는 삭제한다. 직접 입력 evidence는 document 삭제의 영향을 받지 않는다.
- 과거 link와 verification은 삭제된 source marker를 반환하고 raw excerpt를 반환하지 않는다.

### 5.6 자기소개서 질문·answer version

- 공고당 non-deleted `DRAFT|FINALIZED` active cover letter는 최대 1개다. archived history는 여러 개 허용한다.
- question은 삭제 시 `deleted_at`을 설정하고 answer version·verification·provenance는 보존한다. DB `ON DELETE CASCADE`로 지우지 않는다.
- current answer 교체는 기존 false+새 true+cover status DRAFT를 한 transaction으로 처리한다.
- partial unique는 `(user_id,question_id) WHERE is_current=true`다.
- restore row는 `parent_version_id`로 restore 직전 current를, `restored_from_version_id`로 선택 과거 version을 기록한다.

### 5.7 회원 탈퇴

`account_deletion_tasks`는 `id(deletionRequestId),subject_user_id?,status,policy_version,attempt_count,next_attempt_at,claim_token,lease_expires_at,purge_by,last_error_code?,requested_at,completed_at?`을 가진다. `subject_user_id`는 purge 대상 식별용 UUID지만 FK를 두지 않고 성공 transaction에서 null로 지운다. task에는 email·이름·원문을 복사하지 않는다.

1. 현재 session에서 password를 검증한다. 이 command는 session 폐기 뒤 replay할 수 없으므로 idempotency record를 만들지 않는다.
2. 한 transaction에서 user를 `WITHDRAWN`, 모든 session을 폐기하고 `QUEUED` deletion task를 만든 뒤 `AccountDeletionAcceptedDto`를 반환한다. Agent Run은 만들지 않는다.
3. 로그인·모든 domain API와 presign을 즉시 차단한다. 이후 사용자가 조회할 endpoint는 없으며 202 receipt의 `deletionRequestId`가 지원 문의·운영 추적 키다.
4. 독립 worker가 active Agent Run cancel과 안정 상태 반영을 기다리고, Object outbox 완료, domain child purge를 수행한다. 실패는 `RETRY_WAIT`, 정책상 재시도 소진은 `DEAD`+운영 경보다.
5. 마지막 한 transaction에서 task를 `SUCCEEDED`, `completed_at` 설정, `subject_user_id=null`로 먼저 전환하고 user row를 purge한다. task finalize와 user purge 중 하나만 반영되는 상태를 허용하지 않는다.

유예기간·물리 삭제 SLA·task metadata 보존기간·동일 email 재가입 시점은 8장의 승인 항목이다.

### 5.8 Research와 source provenance

- interview preparation 하나는 combined `research_run` 하나, question set 하나를 만든다.
- source topic은 `COMPANY`, `INTERVIEW_PROCESS`, `ROLE_TECHNICAL`이다.
- `research_run 1:N source`, `question N:M source`, material report claim N:M source다.
- retry는 기존 row를 변경하지 않고 새 research run·question set·Agent Run을 만들며 `retry_of_research_run_id`를 저장한다.
- `sourceBased`는 link 존재 여부로 계산한다.

### 5.9 Mock request ID

`mock_interview_turns`는 `id,user_id,session_id,client_request_id,request_hash,status,user_message_id,interviewer_message_id,response_json,safe_error_code,started_at,completed_at`을 가진다. unique `(user_id,session_id,client_request_id)`와 session당 active PENDING 최대 1개 partial unique를 둔다. message sequence와 session version CAS는 같은 transaction에서 증가한다.

### 5.10 Embedding model·dimension·index

- 저장소에는 active embedding provider·model이 선택되어 있지 않다. 8.8에서 provider·정확한 model ID·그 model의 output dimension을 한 묶음으로 승인하기 전에는 `vector(n)` column이나 vector index migration을 작성하지 않는다.
- 승인 후 모든 chunk에 `embedding_provider`, `embedding_model`, `embedding_dimension`, `embedding_generation`을 저장하고 boot 시 설정 model의 실제 dimension과 schema typmod가 다르면 fail fast한다.
- 모든 query는 `user_id`, active document와 승인된 model·dimension·active generation을 조건으로 한다.
- 초기에는 exact cosine search다. live chunk 50,000개 이상 또는 대표 query p95 200ms 초과가 측정되면 별도 forward migration으로 HNSW를 만든다.
- 모델·dimension 변경은 같은 indexed column에 섞지 않는다. 새 typed vector column/index와 embedding generation 추가→backfill→정합성 검증→active generation switch→후속 cleanup 순서다.

### 5.11 TipTap 저장·글자 수

- answer version은 `content_json jsonb`, `content_text text`, `character_count integer`를 함께 저장한다.
- server가 allowlist schema를 검증하고 canonical JSON을 만든다. raw HTML은 저장하지 않는다.
- plain text는 paragraph/list item/hardBreak 경계를 단일 `\n`으로 만들고 CRLF→LF, NBSP→space, Unicode NFC를 적용한다.
- Unicode code point를 세며 공백과 줄바꿈은 1자, markup과 zero-width 문자는 0자다. Frontend는 같은 algorithm으로 미리 보이되 서버 count가 최종이다.

### 5.12 향후 migration 책임과 순서

기존 `V1__enable_extensions.sql`은 수정하지 않는다. 실제 파일명은 작성 시 repository 순번을 확인하되 책임과 적용 순서는 다음을 고정한다.

| 순서 | 예정 책임                    | 주요 table·제약                                                                                                |
| ---: | ---------------------------- | -------------------------------------------------------------------------------------------------------------- |
|    2 | identity·session·idempotency | users, profile 기본 row, Spring Session schema ownership, idempotency, user FK 없는 account deletion task      |
|    3 | structured profile           | education/certification/language/award/career, direct evidence, version·soft delete·대표 학력 partial unique   |
|    4 | Agent runtime·budget         | run/step, retry/claim/lease/cancel, model policy/preference, immutable price, ledger/reservation, usage        |
|    5 | documents·evidence           | document/text/chunk, two status axes, owner composite FK, Object outbox; vector column은 8.8 승인 뒤 책임 확정 |
|    6 | jobs                         | companies, posting/history, canonical active unique, 업무·추출 CHECK                                           |
|    7 | job analysis provenance      | immutable analysis, rubric/hash, analysis↔evidence link                                                        |
|    8 | cover letter                 | active partial unique, soft question, immutable version/content/link/verification                              |
|    9 | research·interview           | combined research/source/link, question/answer/feedback, mock session/turn/message/feedback                    |
|   10 | vector index 조건부          | 측정 기준을 넘을 때만 HNSW; 초기 migration에는 포함하지 않음                                                   |

각 migration은 자신의 owner composite FK·unique·CHECK를 같은 단계에서 만들고, 빈 DB와 직전 production-like schema upgrade를 모두 검증한다. 실제 SQL은 이 제안서에 포함하지 않는다.

## 6. AI runtime 기준선

### 6.1 WorkflowType별 고정 step

`[*]`는 모델이 결정하는 자유 loop가 아니라 요청의 검증된 bounded ID 목록을 registry 순서대로 실행하는 fan-out이다. `scopeKey`는 question ID 등 반복 대상을 식별한다.

| WorkflowType                | 고정 step 순서                                                                                                                                                                                                                                                               | workflow input                                                                                      | 최종 output·domain apply                                                           |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `DOCUMENT_INGESTION`        | `LOAD_DOCUMENT_SOURCE → EXTRACT_OR_ACCEPT_TEXT → MASK_TEXT → CHUNK_TEXT → EMBED_CHUNKS → EXTRACT_EVIDENCE_CANDIDATES → APPLY_EVIDENCE_CANDIDATES → FINALIZE_DOCUMENT`                                                                                                        | document ID, source revision, `UPLOAD\|REPARSE\|MANUAL_TEXT`, expected version                      | parse/evidence 상태, chunk count, evidence IDs, warning; document/evidence command |
| `JOB_POSTING_EXTRACTION`    | `FETCH_JOB_PAGE → SANITIZE_PAGE_TEXT → EXTRACT_JOB_FIELDS → MERGE_USER_OVERRIDES → APPLY_JOB_EXTRACTION`                                                                                                                                                                     | job ID/version, canonical URL, user field hash                                                      | extraction 상태, content hash, field별 source; job command                         |
| `JOB_ANALYSIS`              | `BUILD_JOB_SNAPSHOT → EXTRACT_REQUIREMENTS → ASSESS_ELIGIBILITY → RETRIEVE_VERIFIED_EVIDENCE → MATCH_EVIDENCE → SCORE_FIT → VALIDATE_ANALYSIS → PERSIST_ANALYSIS`                                                                                                            | job content hash, profile/evidence snapshot hash, quality mode                                      | immutable analysis ID/version, eligibility, rubric score와 evidence refs           |
| `COVER_LETTER_GENERATION`   | `BUILD_GENERATION_CONTEXT → PLAN_QUESTIONS → ANALYZE_QUESTION[*] → RETRIEVE_EVIDENCE[*] → ALLOCATE_EXPERIENCES → WRITE_ANSWER[*] → FACT_CHECK_ANSWER[*] → APPLY_ANSWER_VERSION[*]`                                                                                           | cover ID/version, question IDs 1..20, preferred VERIFIED evidence, quality mode, current refs       | 질문별 immutable answer version+생성 verification; partial success 보존            |
| `COVER_LETTER_VERIFICATION` | `LOAD_ANSWER_VERSION → BUILD_PROVENANCE_CONTEXT → CHECK_FACTS → CHECK_REQUIREMENTS_AND_LENGTH → AGGREGATE_VERIFICATION → PERSIST_VERIFICATION`                                                                                                                               | immutable answer version/content hash, quality mode                                                 | immutable verification ID/status/issues/source refs                                |
| `INTERVIEW_PREPARATION`     | `VALIDATE_PREREQUISITES → BUILD_PUBLIC_SEARCH_PLAN → SEARCH_OFFICIAL_SOURCES → SEARCH_INTERVIEW_SOURCES → DEDUPE_CLASSIFY_SOURCES → ASSESS_SOURCE_COVERAGE → BUILD_QUESTION_CONTEXT → GENERATE_QUESTIONS → VALIDATE_QUESTION_PROVENANCE → PERSIST_RESEARCH_AND_QUESTION_SET` | analysis hash, cover current refs, `ResearchQuality`, `AiQualityMode=ECONOMY\|BALANCED`, type/count | research run, question set, coverage, source/question links                        |
| `INTERVIEW_ANSWER_FEEDBACK` | `LOAD_ANSWER_VERSION → BUILD_FEEDBACK_CONTEXT → ANALYZE_ANSWER → VALIDATE_FEEDBACK → PERSIST_FEEDBACK`                                                                                                                                                                       | immutable answer version/content hash, quality mode                                                 | feedback ID, scores·strengths·weaknesses·suggestions                               |
| `MOCK_INTERVIEW_FEEDBACK`   | `LOAD_SESSION_SNAPSHOT → ANALYZE_TURNS → SYNTHESIZE_SESSION_FEEDBACK → VALIDATE_FEEDBACK → PERSIST_FEEDBACK`                                                                                                                                                                 | session/message IDs, transcript hash, `AiQualityMode=BALANCED`                                      | session feedback ID/status                                                         |

### 6.2 step input/output와 성공 재사용 hash

공통 input envelope:

```text
schemaVersion
workflowType, workflowVersion
runId, stepKey, scopeKey
resourceRefs(type, id, version, contentHash)
upstreamOutputRefs
contextRefs, contextHash, truncationSummary
requestedQualityMode
promptVersion, outputSchemaVersion
modelPolicyVersion
deterministicPolicyVersions(masking, chunking, rubric 등)
```

출력은 `JSON Schema → Java record → workflow validator → domain command validator`를 모두 통과한 뒤 반영한다. `agent_steps.output_json`에는 원문·전체 prompt/response가 아니라 결과 ID/hash, provenance ref와 validation summary만 둔다.

재사용 SHA-256 hash 구성:

```text
user scope
+ workflow/step/scope/version
+ resource ID/version/content hash
+ upstream output hash
+ context hash와 truncation 결과
+ prompt/output schema version
+ model policy/tier version와 requested AiQualityMode
+ tool/research quality와 source freshness bucket
+ deterministic policy version
```

run ID와 attempt 번호는 hash에서 제외한다. 같은 사용자·workflow·step·scope·hash이고 원본 결과가 아직 존재할 때만 `REUSED`한다. HIGH_QUALITY 요청은 낮은 품질 결과를 재사용하지 않는다.

### 6.3 자동 재시도·승격·resume

- 자동 재시도 2회는 최초 호출 1회+retry 2회, 총 attempt 최대 3회다.
- 대상: 429/5xx, 일시 network, 비동기 provider timeout, structured output validation 실패.
- 비대상: owner/input/domain validation, safety block, configuration, 비용 한도.
- 모델 승격 호출도 attempt 하나다. 자동 승격은 `LOW_COST→BALANCED` 1회뿐이고 HIGH_QUALITY 자동 승격은 없다.
- terminal user retry는 새 run을 만들고 `retryOfRunId/rootRunId/runAttemptNo`를 저장한다.
- `WAITING_USER`는 모델 호출 전에 필요한 사용자 입력이 없을 때만 진입한다. unused reserve를 해제하고 manual input command가 같은 run을 QUEUED로 재개하며 재개 시 다시 예약한다.
- WAITING_USER는 자동 만료로 사용자 데이터를 삭제하지 않는다. 사용자 cancel 또는 resource delete까지 유지한다.

### 6.4 공개 품질과 내부 모델 정책

| 공개 모드      | 기본 내부 policy                                              | 승격·적용 범위                                               |
| -------------- | ------------------------------------------------------------- | ------------------------------------------------------------ |
| `ECONOMY`      | 생성·분석 포함 LOW_COST 우선                                  | retryable structured failure 때 BALANCED 1회 가능            |
| `BALANCED`     | 추출·분류 LOW_COST, 분석·생성 BALANCED                        | HIGH_QUALITY 자동 승격 금지                                  |
| `HIGH_QUALITY` | 전처리·검색·추출은 저비용 유지, 최종 생성·검토만 HIGH_QUALITY | `highQualityEnabled=true`+요청별 명시 선택+reserve 성공 필수 |

HIGH_QUALITY 허용 workflow는 요청에 `qualityMode`가 있는 cover letter 생성·검증과 interview answer feedback이다. mock session feedback은 complete 요청에 품질 선택이 없으므로 MVP에서 `BALANCED`로 고정하고 HIGH_QUALITY를 허용하지 않는다. job 분석, document/job extraction, interview preparation도 ECONOMY/BALANCED만 허용한다. endpoint·OpenAPI·ModelRouter가 같은 allowlist를 사용하며 provider/model 실명은 일반 API에 노출하지 않고 run의 `highestModelTierUsed`만 표시한다.

### 6.5 비용 가격 version과 reserve/settle

비용에는 chat input/cached input/output token, embedding input unit, BASIC/ADVANCED search request를 모두 포함한다. cache hit와 무료 호출도 cost 0 usage row를 남긴다.

필요 schema:

- immutable `ai_price_versions`, `ai_price_items(provider,product,unit,unit_price,effective_range)`
- 일일 `ai_budget_ledgers(subject,date,spent,reserved)`
- `ai_budget_reservations(operation_type,run_or_turn_id,reserved,settled,status,expires_at)`
- usage의 operation/resource ref, price version, tier, CHAT/EMBEDDING/SEARCH unit

원자 처리:

1. policy 최대 token/call로 worst-case 비용을 계산한다.
2. user ledger를 row lock 또는 조건부 update하고 예상액을 예약한다.
3. 부족하면 resource/run을 만들지 않고 429를 반환한다.
4. 실제 usage를 접수 당시 고정한 price version으로 정산한다.
5. 예상 초과는 top-up 예약이 성공할 때만 다음 호출을 허용한다.
6. terminal·WAITING_USER에서 미사용 예약을 해제한다.
7. `actualCostUsd`는 provider invoice가 아니라 고정 catalog로 계산한 billable estimate임을 API 설명에 명시한다.

구체 금액과 reset zone은 8장의 승인 항목이다. 구조는 금액 선택과 무관하게 동일하다.

### 6.6 미승인 문서 chunk 정책

Context 우선순위:

1. 요청 문항·사용자 지시
2. 최신 공고와 immutable analysis
3. 사용자가 선택한 `VERIFIED` evidence
4. 관련 `VERIFIED` evidence
5. current cover/answer version
6. provenance가 있는 research snippet
7. 해당 step에서만 허용된 미승인 masked chunk

미승인 chunk는 evidence 후보 추출·semantic 탐색과 FactCheck 모순 확인에만 쓴다. 그 chunk만으로 PASSED 처리하지 않고 `WARNING/UNVERIFIED_SOURCE`를 낸다. job score, cover writer, interview question·interviewer의 긍정 사실 근거에는 사용하지 않는다. 외부에는 masked content만 전송한다. snapshot provenance는 document/revision/chunk/page/content hash와 당시 verification 상태를 가진다.

### 6.7 fit score rubric

- 범위: 0.00..100.00, 소수 둘째 자리 반올림.
- 합격 확률이 아니고 eligibility와 별도다. `INELIGIBLE`이어도 score를 인위적으로 cap하지 않는다.
- 권장 가중치: 필수 자격 40, 핵심 업무·기술 30, 우대 15, 관련 경험·도메인 10, 학력·자격·어학 5.
- criterion은 `MATCHED=1.0`, `PARTIAL=0.5`, `MISSING|UNKNOWN=0`이다.
- 공고에 없는 category 가중치는 존재 category에 비례 재분배한다.
- 점수 근거는 structured profile과 VERIFIED evidence뿐이다. criterion이 하나도 없으면 `INSUFFICIENT_JOB_DATA`로 분석을 실패시킨다.
- rubric version과 criterion별 공고 source·evidence IDs를 저장한다.

가중치와 eligibility 사용자 표현은 8장의 승인 항목이다.

### 6.8 source coverage

- `SUFFICIENT`: usable source 3개 이상, OFFICIAL 또는 TECH_BLOG 1개 이상, 서로 다른 category/domain 2개 이상, 주요 claim에 source link가 있음.
- `LIMITED`: usable source가 있지만 수량·범주·주요 claim 기준 일부가 부족함.
- `NONE`: 정상 검색 결과에서 usable source가 0개.
- 모든 provider 호출이 장애면 ResearchRun `FAILED`; 정상 결과 부족은 `SUCCEEDED+LIMITED|NONE`이다.
- LIMITED/NONE에서도 공고·cover·VERIFIED evidence 기반 질문을 만들 수 있지만 회사/process 사실을 단정하지 않고 `sourceBased=false`다.

### 6.9 동기 mock turn

- start/message HTTP deadline, 최대 호출 수와 비용은 승인 항목이다. 권장안은 20초, request당 chat 1회, search/embedding 0회다.
- timeout·invalid output에 서버 자동 재호출을 하지 않는다. 사용자가 같은 clientRequestId로 재조회하면 pending/완료 상태를 반환하고, 명시 새 ID만 새 시도다.
- immediate feedback도 같은 structured response에서 만들며 별도 모델 호출을 추가하지 않는다.
- 사용량은 `MOCK_INTERVIEW_TURN` operation으로 session/turn/message와 연결하고 `agent_run_id/agent_step_id`는 null이다.
- session detail에 누적 `actualCostUsd`를 제공한다.
- complete만 feedback Agent Run을 생성한다. feedback 실패여도 session은 COMPLETED를 유지한다.

### 6.10 provider 경계

- domain/application은 Spring AI concrete API를 참조하지 않는다.
- AI 모듈의 `ChatGateway`, `EmbeddingGateway`, `WebSearchGateway`를 provider adapter가 구현한다.
- 공고 URL은 별도 `JobPageFetchGateway`가 SSRF·redirect·byte/time limit을 담당한다. Tavily extract를 사용자 URL fetch에 쓰지 않는다.
- `PromptRegistry`는 input/output record, JSON schema, prompt version, 허용 tool과 token/call 상한을 함께 등록한다.
- Tool Calling은 step별 allowlist와 호출 상한만 사용하고 모델이 임의 tool·loop를 선택할 수 없다.
- 외부 page/search text는 instruction이 아닌 untrusted data로 delimiter 처리한다.

## 7. Route 및 API projection 기준선

### 7.1 canonical route·redirect

| 입력 route                   | canonical 결과                                       |
| ---------------------------- | ---------------------------------------------------- |
| `/` anonymous                | `/login`                                             |
| `/` authenticated            | `/dashboard`                                         |
| `/signup` authenticated      | `/onboarding`(방금 가입) 또는 `/dashboard`로 replace |
| `/login` authenticated       | 안전한 `returnTo` 또는 `/dashboard`로 replace        |
| `/profile`                   | `/profile/basic`                                     |
| `/jobs/:jobId`               | `/jobs/:jobId/overview`                              |
| `/settings`                  | `/settings/account`                                  |
| mock 생성                    | `/jobs/:jobId/interview/mock/new`                    |
| unmatched `/:pathMatch(.*)*` | App/Public shell에서 접근 가능한 전용 404 page       |

job child route는 `overview|analysis|cover-letter|interview`만 허용한다. 404는 인증 여부를 확인한 뒤 적절한 shell에서 보이며 타 사용자 UUID도 같은 not-found 표현을 쓴다.

### 7.2 return URL

- query 이름은 `returnTo`다.
- 한 번 decode한 값이 `/`로 시작하고 `//`, scheme, host, backslash, CR/LF가 없어야 한다.
- `router.resolve()` 결과가 등록된 auth-required route일 때만 허용한다.
- login/signup/404와 외부 URL은 거부한다.
- 401 때 현재 `fullPath`를 memory에 두고 로그인한 사용자 ID가 같은 context에서만 복귀한다. localStorage/referrer를 쓰지 않는다.

### 7.3 Dashboard·목록 projection과 filter

- Dashboard는 전용 `GET /dashboard` 단일 read projection을 사용한다. 여러 paginated API의 첫 page로 count를 추정하지 않는다.
- `/cover-letters`: `jobId,status,query,sort,page,size`; DRAFT/FINALIZED/ARCHIVED와 archive/unarchive action을 표시한다.
- `/interviews` question set 영역: `jobId,coverLetterId,query,sourceCoverage,researchStatus,sort,page,size`.
- `/interviews` mock 영역: `jobId,query,status,feedbackStatus,sort,page,size`. 두 영역은 별도 pagination query key를 가진다.
- `/agent-runs`: repeatable `workflowType,status`, `resourceType,resourceId,retryable,sort,page,size`.
- filter는 URL query를 공유 가능한 원천으로 삼고 변경 시 page=0으로 reset한다. Zod parsing 실패 값은 제거하고 canonical URL로 replace한다.

### 7.4 상태별 UI

| 상태                            | 표시·사용자 동작·disabled 조건                                                                |
| ------------------------------- | --------------------------------------------------------------------------------------------- |
| document NEEDS_MANUAL_TEXT      | manual editor 활성, evidence approve 비활성, waiting run link                                 |
| document PARSED+evidence FAILED | text 유지, evidence retry/reparse CTA와 safe error; upload 실패로 표시하지 않음               |
| job NEEDS_MANUAL_INPUT          | 본문·마감 입력 강조, 분석 버튼 비활성                                                         |
| job FAILED                      | safe error, retry와 manual input 둘 다 제공                                                   |
| analysis OUTDATED               | 기존 분석 유지, 노란 badge·reason·재분석 CTA; downstream hard block 없음                      |
| 409 version conflict            | mutation 자동 재시도 금지, 최신 snapshot과 사용자 미저장 draft를 나란히 보이고 재적용 선택    |
| Agent WAITING_USER              | `requiredUserAction` deep link, 일반 retry 비활성, cancel은 server boolean 사용               |
| Agent FAILED/INTERRUPTED        | `retryable=true`일 때만 retry 활성                                                            |
| SSE disconnect                  | 마지막 snapshot 유지, reconnect/polling 안내; 연결 실패만으로 run 실패 표시 금지              |
| mock COMPLETED+QUEUED/RUNNING   | transcript 조회 가능, feedback skeleton과 Agent Run link, session command 비활성              |
| mock feedback FAILED            | transcript 보존, safe error와 retryable feedback run action                                   |
| cover ARCHIVED                  | 조회·version·verification만 가능, edit/generate/verify/finalize 비활성, unarchive 조건부 활성 |
| draft base mismatch             | 자동 덮어쓰기 금지, 비교 후 새 초안으로 복사                                                  |

### 7.5 cache·draft 폐기

- Query key와 draft key는 user ID namespace를 포함한다.
- logout·탈퇴·401 auth reset·인증 사용자 ID 변경 시 EventSource를 닫고 in-flight query를 취소한 뒤 `queryClient.clear()`, Pinia reset, 해당 user draft purge 순서로 처리한다.
- draft key는 `schemaVersion/userId/resourceType/resourceId/questionId/baseVersionId`; 값은 content JSON, baseVersionId, savedAt만 둔다.
- server save, question delete, archive, logout, account delete 때 해당 draft를 지운다.
- 세션 만료 중 이전 draft를 render하지 않고 같은 user 재인증 때만 복구 후보로 연다.

## 8. 제품 소유자 승인 항목

아래 8개만 제품 소유자 결정이 필요하다. 선택 전에도 구현자는 권장안의 구조와 대안 영향을 평가할 수 있지만 관련 공개 계약·migration은 만들지 않는다.

### 8.1 삭제·보존 정책

```text
결정 주제: 문서 삭제 provenance와 회원 탈퇴 유예·물리 삭제 SLA·운영 metadata 보존
권장안: 문서 원문/text/chunk/Object는 즉시 삭제하고 참조된 evidence 최소 snapshot만 SOURCE_DELETED로 보존한다. 탈퇴는 즉시 비가역 WITHDRAWN·전 session 폐기, 24시간 내 물리 삭제 목표, PII 없는 성공 task metadata 30일 보존, purge 후 같은 email 재가입 허용으로 한다.
대안: 1) evidence도 전부 즉시 삭제해 과거 provenance를 끊는다. 2) 탈퇴 후 7일 복구 유예를 두고 그 뒤 purge한다.
권장 이유: 원문 개인정보 최소화와 과거 산출물의 출처 상태, Object 장애 재시도를 함께 만족한다.
선택에 따른 사용자 영향: 권장안은 탈퇴 즉시 로그인·복구가 불가능하지만 과거 보존 산출물에는 “원본 삭제됨”을 확인할 수 있다.
선택에 따른 구현 영향: evidence tombstone 범위, deletion task/outbox, purge 순서, cleanup retention과 email unique 재사용 시점이 달라진다.
```

### 8.2 AI 비용·고품질 정책

```text
결정 주제: 사용자 일일·실행별 비용 상한, HIGH_QUALITY 사용 범위와 일일 reset 기준
권장안: 사용자 기본 일일 USD 1.00, 시스템 사용자별 최대 USD 2.00, 비동기 run 최대 USD 0.30으로 한다. HIGH_QUALITY는 설정 활성화+요청별 선택+예약 성공 시 생성·최종 검토 step에만 쓴다. 일일 ledger는 UTC 날짜로 계산한다.
대안: 1) 보수안 USD 0.50/1.00/0.15. 2) 확장안 USD 2.00/5.00/0.50과 Asia/Seoul 날짜 reset.
권장 이유: 현재 명세·환경 예시의 상한과 일치하고 단일 timezone ledger로 동시 reserve를 단순·재현 가능하게 유지한다.
선택에 따른 사용자 영향: 낮은 상한은 중간 비용 차단이 늘고 높은 상한은 성공률·품질과 지출이 함께 증가한다. reset 시각도 사용자가 인지하는 “오늘”에 영향을 준다.
선택에 따른 구현 영향: policy 값·표시 문구·fixture가 달라지며 price version과 reserve/settle 구조는 동일하다.
```

### 8.3 자기소개서 최종화·보관 lifecycle

```text
결정 주제: WARNING 최종화, 공고당 active cardinality와 ARCHIVED 복귀
권장안: active DRAFT|FINALIZED는 공고당 1개, archived history는 여러 개 허용한다. PENDING/FAILED는 최종화 차단, WARNING은 해당 verification ID를 명시 확인한 경우만 허용한다. DRAFT/FINALIZED 모두 archive 가능하고 read-only 보관 뒤 active가 없을 때 DRAFT로 unarchive한다.
대안: 1) PASSED만 최종화하고 archive는 영구 복귀 불가. 2) 공고당 cover letter row 자체를 하나만 허용하고 archive 뒤 새 작성도 금지.
권장 이유: 사실 안전성, 사용자의 최종 통제권, 새 지원서 작성과 실수 복구를 균형 있게 제공한다.
선택에 따른 사용자 영향: 경고 확인 단계, 한 공고에서 새 지원서를 시작하는 방식과 보관 복구 여부가 달라진다.
선택에 따른 구현 영향: finalize DTO, active partial unique, archived timestamp, unarchive API·UI와 transition test가 달라진다.
```

### 8.4 동기 모의 면접 상한

```text
결정 주제: 동기 start/message의 latency·호출·turn/session 비용 상한
권장안: HTTP deadline 20초, request당 chat 1회, search/embedding 0회, turn 최대 USD 0.03, session 동기 turn 합계 USD 0.30으로 한다. timeout/invalid output은 자동 재호출하지 않고 clientRequestId로 상태를 복구한다.
대안: 1) 보수안 15초, USD 0.015/0.15. 2) 확장안 30초, USD 0.05/0.50과 사용자 명시 retry 1회.
권장 이유: 대화 응답성을 유지하면서 timeout 중복 과금과 무제한 호출을 막는다.
선택에 따른 사용자 영향: 낮은 값은 실패·재시도 안내가 늘고 높은 값은 응답 대기와 비용이 증가하지만 복잡한 답변 성공률이 높아질 수 있다.
선택에 따른 구현 영향: executor timeout, BudgetGuard policy, 429/503 UX와 부하·비용 fixture가 달라진다.
```

### 8.5 브라우저 임시 draft 보존

```text
결정 주제: 자기소개서·면접 답변의 미저장 browser draft 복구 범위와 기간
권장안: 사용자/resource/base version으로 격리한 sessionStorage를 사용하고 savedAt 기준 최대 24시간으로 제한한다. browser session 종료, server save, archive, logout, 탈퇴, 사용자 변경 때 폐기한다.
대안: 1) tab/session 종료 즉시 무조건 폐기한다. 2) user-scoped localStorage에 7일 보존해 browser 재시작 후에도 복구한다.
권장 이유: 새로고침·단기 오류 복구는 제공하면서 공용 PC와 계정 전환 시 원문 노출을 줄인다.
선택에 따른 사용자 영향: 보존 기간이 길수록 장기 미저장 작업은 복구되지만 같은 device의 잔존 위험이 커진다.
선택에 따른 구현 영향: storage 종류, TTL cleanup, base version 충돌 UI와 두 사용자 E2E 범위가 달라진다.
```

### 8.6 적합도 점수 rubric

```text
결정 주제: 0~100 적합도 가중치와 eligibility 관계
권장안: 필수 자격 40, 핵심 업무·기술 30, 우대 15, 관련 경험·도메인 10, 학력·자격·어학 5로 계산한다. eligibility는 별도 표시하고 INELIGIBLE도 score를 cap하지 않으며 합격 threshold를 두지 않는다.
대안: 1) 정성 등급만 제공한다. 2) INELIGIBLE일 때 score 상한을 두거나 전체 점수를 0으로 만든다.
권장 이유: “합격 확률이 아닌 등록 정보 일치도”를 지키면서 결과를 재현하고 부족 항목을 설명할 수 있다.
선택에 따른 사용자 영향: 같은 profile·공고의 점수와 사용자가 집중할 보완 항목, 부적격 결과의 해석이 달라진다.
선택에 따른 구현 영향: rubric version, structured output validator, analysis fixture와 UI 설명·content test가 달라진다.
```

### 8.7 프로필 완료 조건과 진입 경고

```text
결정 주제: 프로필 완료 계산 항목과 온보딩·분석 진입에 미치는 영향
권장안: legalName, 희망 직무·산업·지역 각 1개, 대표 학력 1개를 완료 항목으로 표시하되 미완료 사용자를 강제 redirect하거나 분석·자기소개서에서 hard block하지 않고 경고와 온보딩 링크만 제공한다.
대안: 1) legalName과 희망 직무만 최소 완료 조건으로 둔다. 2) 다섯 항목을 모두 채우기 전 관련 AI 기능을 차단한다.
권장 이유: 명세의 프로필 보완 경고를 유지하면서 제품 승인 전 정한 적 없는 필드를 필수 입력으로 만들지 않고, 근거가 부족한 결과는 기존 workflow prerequisite가 별도로 통제한다.
선택에 따른 사용자 영향: Dashboard 완료율, 첫 로그인 이동, 분석 전 경고 강도와 입력 부담이 달라진다.
선택에 따른 구현 영향: ProfileCompletionItem, ProfileDto·Dashboard projection, route guard와 content/E2E test가 달라진다.
```

### 8.8 Embedding provider·model·dimension

```text
결정 주제: MVP embedding provider·정확한 model ID·vector dimension
권장안: 기술 스택의 OpenAI 우선 원칙에 따라 provider `OpenAI`, model ID `text-embedding-3-small`, output dimension `1536`을 한 묶음으로 승인한다. 이 선택이 승인되기 전에는 vector column·index migration을 만들지 않는다.
대안: 1) 동일 provider의 고품질 embedding model을 선택해 비용과 검색 품질을 높인다. 2) 운영 Object/AI provider 결정과 함께 다른 Spring AI 지원 embedding provider를 선택한다.
권장 이유: 현재 저장소에는 검증 가능한 model 선택이 없으므로 숫자만 1536으로 고정할 수 없고, provider·model·dimension을 함께 승인해야 schema와 재색인 절차를 재현할 수 있다.
선택에 따른 사용자 영향: 검색 정확도와 문서 처리 비용·시간이 달라진다.
선택에 따른 구현 영향: provider 설정, model policy, vector typmod, embedding generation/backfill과 index migration이 달라진다.
```

## 9. 승인 후 적용 순서와 구현 차단

1. 제품 소유자가 8장의 8개 항목과 나머지 권장안을 승인·수정한다.
2. 한 작업에서 `functional/api/db/page/tech_stack`을 같은 의미로 갱신하고 canonical matrix를 다시 검증한다.
3. OpenAPI·DB migration 목록을 확정한 뒤 P1부터 구현한다.
4. 승인 전에는 이 문서의 enum·DTO·table을 코드나 migration으로 선행 생성하지 않는다.

현재 구현 차단 항목:

- Gate A: owner 선택이 필요한 cover finalization/archive, account response policy, mock 비용 경계.
- Gate B: 삭제 tombstone·탈퇴 보존 선택.
- Gate C: 비용 상한과 fit rubric, 동기 mock 상한.
- 나머지 Gate 항목은 이 제안서에서 단일 권장안으로 닫혔지만 전체 제안 승인 전에는 구현하지 않는다.
