# Hiresemble 구현 계획

이 계획은 [전체 시스템 설계](system-architecture.md)를 AC-01~AC-17의 검증 가능한 수직 단계로 구현하기 위한 순서와 완료 조건을 정의한다. 공개 계약과 데이터 수명주기를 먼저 확정하고, 승인 근거→공고→자기소개서→면접의 도메인 선행 관계와 P9 전 운영 기반을 유지한다.

P0의 결정 과정과 승인 근거는 [P0 계약 결정 기록](p0-contract-decision-proposal.md)에 보존한다. 현재 활성 계약은 `docs/spec/**`이며 P0 계약 기준선은 2026-07-18 완료됐다. P1 공통 HTTP·인증부터 P7 자기소개서 생성·검증·버전 관리까지 2026-07-30 final-source actual 검증과 독립 validator `PASS`로 완료됐다. P8은 2026-07-31 구현과 final-source 검증, 한 번의 제한 보정 뒤 두 번째 single-agent read-only self-audit `PASS`로 완료됐다. P8.5는 일반 local의 OpenAI Chat·Embedding/Tavily 연결과 offline/test 격리를 구현했다. 2026-08-01 strict schema·semantic 계약 보정 뒤 실제 문서 run `bf26f44e-4512-414d-af1e-863076941535`는 Chat strict output, Java/workflow validation, trusted ref mapping, evidence persistence와 finalize까지 성공했다. candidate 6건 중 4건 적용·2건 정상 filtering이었지만 이를 가짜 failed scope로 만든 projection과 공용 자기소개서 partial error 하드코딩으로 Run terminal만 잘못 실패했다. terminal policy 보정은 offline 검증했고 이후 Provider를 재호출하지 않았으므로 전체 상태는 `IMPLEMENTED_NOT_LIVE_VERIFIED`다. P8.5 이후 결정 근거는 [운영 기반 계약 결정](post-p8-5-operations-contract-decision.md)에 보존한다.

## 범위

- 포함:
  - 명세 결정, Backend·AI workflow·Frontend 구현 순서
  - package·directory 목표 구조
  - DB/API/page 연결과 단계별 완료 조건
  - 테스트, 검증, 파일 소유권과 역할 간 handoff
- 제외:
  - 이번 문서 작업에서 실제 비즈니스 코드·dependency·migration·API·UI를 변경하는 일
  - [기술 명세의 MVP 제외 범위](../spec/tech_stack.md#14-mvp-제외-범위)
  - 승인된 명세 밖의 계약을 임의 기본값으로 추가하는 일

## 실행 체크리스트

- [x] P0에서 상태·enum·DTO·수명주기·AI 정책 결정 게이트를 닫는다.
- [x] 공통 HTTP 오류, Security/Session/CSRF, request ID와 테스트 기반을 구현한다.
- [x] 사용자·프로필·직접 입력 근거를 구현해 AC-01~02를 고정한다.
- [x] Agent Run, 고정 workflow, Model Router, Context Builder, Budget Guard와 SSE 기반을 Fake로 검증한다.
- [x] 문서 업로드·파싱·근거 검토를 구현해 AC-03을 고정한다.
- [x] 공고 등록·수동 보완·상태·Scheduler를 구현해 AC-04~06을 고정한다.
- [x] 공고 분석·RAG를 구현해 AC-07을 고정한다.
- [x] 자기소개서 생성·검증·version·최종화를 구현해 AC-08~09를 고정한다.
- [x] 면접 조사·출처·예상 질문·답변 피드백을 구현해 AC-10~11을 고정한다.
- [x] P8.5 실제 Provider adapter와 local/offline/test 활성화 경계를 구현한다.
- [ ] P8.5-V 사용자 local capability·P4~P8 수직 흐름을 실제 Provider로 검증한다.
- [ ] P8.6 제품 기능 한도·metering으로 AC-14를 고정한다.
- [ ] P8.7 사용자 사용량·내부 원가·과금 가능 unit 집계로 AC-15를 고정한다.
- [ ] P8.8 공통 AI 실패 UX·복구로 AC-16을 고정한다.
- [ ] P8.9-A ADMIN 읽기 전용 Backoffice로 AC-17을 고정한다.
- [ ] 모의 면접과 비동기 종합 피드백을 구현해 AC-12를 고정한다.
- [ ] P10-A 사용자 Dashboard·설정, P10-B 운영 안정성·동시성, P10-C 출시 준비로 전체 AC와 MVP 회귀를 완료한다.

현재 단계: P0–P8 `DONE`, P8.5 `IMPLEMENTED_NOT_LIVE_VERIFIED`, P8.5-V `USER_LOCAL_VALIDATION_PENDING`, P8.6–P8.9-A `PLANNED`, P8.9-B `PLANNED_LATER`, P9 `BLOCKED_BY_P8_5V_TO_P8_9A`, P10-A–C `PLANNED`다. Backend 기준선은 70 suites/491 tests이며 Frontend 61 files/243 tests와 OpenAPI 63 paths/84 operations는 변경하지 않았다. Embedding과 Chat strict output부터 document finalize까지 실제 run으로 검증됐고 terminal classification 보정은 offline 검증됐지만 live 재검증 전이다. 이미지형 공고 v3 보정의 실제 Provider 호출은 0회다.

## 1. 전체 선행 관계

```text
P0 계약 기준선
 └─ P1 공통 HTTP·인증 기반
     ├─ P2 프로필·직접 근거
     └─ P3 Agent Run·AI runtime
          ├─ P4 문서·추출 근거 ─────┐
          └─ P5 공고 등록·상태 ─────┤
                                    ▼
                              P6 공고 분석
                                    ▼
                              P7 자기소개서
                                    ▼
                              P8 면접 준비
                                    ▼
                        P8.5 Provider 연결
                                    ▼
                    P8.5-V 사용자 local 실제 검증
                                    ▼
                     P8.6 제품 기능 한도·metering
                                    ▼
          P8.7 사용량·내부 원가·과금 가능 usage 집계
                                    ▼
                       P8.8 AI 실패 UX·복구
                                    ▼
                   P8.9-A 읽기 전용 Backoffice
                                    ▼
                              P9 모의 면접
                                    ▼
                    P10-A Dashboard·사용자 설정
                                    ▼
                    P10-B 운영 안정성·동시성
                                    ▼
                    P10-C 출시 준비·전체 회귀
```

- P2와 P3은 P1 이후 파일 소유권이 겹치지 않으면 병렬화할 수 있다.
- P4와 P5도 Agent Run 공개 계약이 고정된 뒤 병렬화할 수 있다.
- P6은 최신 공고 본문, profile과 승인 근거가 필요하다.
- P7은 공고 분석과 version domain이 필요하다.
- P8은 공고 분석과 자기소개서가 필요하다.
- P8.5-V는 P8.5를 재구현하지 않고 사용자가 일반 local에서 capability와 P4~P8 수직 흐름을 검증하는 gate다.
- P8.6~P8.9-A는 비용 예산과 별개인 기능 한도, 사용량·원가 집계, 실패 복구, 운영 관찰 경계를 순서대로 고정한다.
- P9는 P3, P8.5-V, P8.6, P8.7, P8.8, P8.9-A가 모두 필요하다.
- P8.9-B 운영 mutation은 별도 후속이며 P9의 필수 선행이 아니다.
- Frontend는 각 phase의 OpenAPI/DTO와 상태 계약이 backend에서 먼저 고정된 뒤 같은 수직 단계로 진행한다.

## 2. 전 단계 공통 완료 조건

각 단계는 다음을 모두 만족해야 완료다.

1. 해당 AC와 기능·DB·API·페이지 연결이 추적 가능하다.
2. 공개 DTO, 상태, 오류 code, ownership과 transaction 경계가 테스트로 고정됐다.
3. 모든 사용자 소유 조회·mutation·SSE·Object 접근에 owner scope가 있다.
4. 외부 호출과 장시간 처리는 DB transaction 밖에 있다.
5. migration은 빈 DB와 기존 DB upgrade에서 검증됐다.
6. Fake/WireMock 검증이 통과하고 실제 Provider 검증이 필요한 phase는 별도 bounded UAT 결과를 기록했다.
7. Backend 변경은 `.\gradlew.bat check`, Frontend 변경은 `corepack pnpm check`를 통과했다.
8. 주요 사용자 흐름 단계는 Playwright 또는 그 단계의 계약 test로 검증됐다.
9. 영향받은 `index.md`와 `progress.md`가 실제 상태를 반영한다.
10. 미검증·실패·후속 작업이 숨겨지지 않았다.
11. 미래 API·route·migration은 실제 구현 전까지 phase와 `PLANNED` 또는 `TENTATIVE` 상태가 표시됐다.
12. 기능 한도, Provider 비용 예산, 과금 가능 usage, 실제 결제의 경계가 섞이지 않았다.

## 3. P0 — 계약 결정 기준선

- 우선순위: 최우선, 모든 구현의 차단 조건
- 담당: 루트 관리자 주도, backend·AI workflow·frontend 분석, validator 승인
- 코드 변경: 없음
- 상태: 완료(2026-07-18). 다섯 기준 명세 동기화와 독립 validator `PASS`

### 3.1 결정 대상

#### 공개 API

- 가입·로그인 CSRF/session 응답과 탈퇴 command
- 모든 endpoint의 request/response, enum, nullability, 길이·개수 범위
- `version` 전달 대상과 409 복구
- dashboard/list filter와 route 진입점
- Idempotency-Key scope·TTL·응답 재생
- SSE event·snapshot·terminal·heartbeat·reconnect

#### 상태·수명주기

- 공고 업무 상태와 추출 상태
- 문서 parser 성공과 근거 추출 성공의 상태 분리
- Agent Run/Step 전체 transition, WAITING_USER, retry와 cancel
- cover letter active cardinality, version source, finalization
- 삭제, `SOURCE_DELETED`, 질문/version 보존, 회원 탈퇴
- research run cardinality, source coverage, mock feedback pending

#### 데이터·AI

- 사용자 소유 child와 복합 FK 강화 방식
- idempotency, outbox, async lease/heartbeat/cancel schema
- 공개 AI 품질, 내부 model tier, research quality mapping
- embedding model·dimension과 index 전략
- budget price version·reserve/settle·비용 포함 범위
- 미승인 chunk, provenance, fit score rubric

### 3.2 산출물

- 승인된 `docs/spec` 변경안
- canonical 상태 전이 표
- endpoint별 OpenAPI 기준선
- migration 목록과 FK/unique/check 설계
- workflow type/step/input/output 표
- page route와 API projection 표

### 3.3 완료 조건

- [x] [전체 설계의 Gate A–C](system-architecture.md#22-p0-승인-완료-게이트) 16개가 활성 명세에 연결됐다.
- [x] Backend와 Frontend가 같은 DTO와 enum을 추측 없이 선언할 수 있다.
- [x] migration 작성자가 nullable, cascade, owner, retry 의미를 추측할 필요가 없다.
- [x] validator가 기능↔DB↔API↔페이지↔상태 matrix를 `PASS`로 판정했다.

## 4. P1 — 공통 HTTP·인증·테스트 기반

- AC: AC-01의 가입·로그인·격리 기반
- 선행: P0
- 주 담당: backend, frontend
- 상태: 완료(2026-07-19). 공통 HTTP·Session 인증·CSRF·request ID·idempotency 기반과 Backend·Frontend 회귀 검증, 최종 validator `PASS`

### 4.1 Backend

- `common/api`: 오류 DTO, field error, factory
- `common/exception`: `ErrorCode`, typed exception, ControllerAdvice
- `common/security`: Security error writer, request ID, CSRF
- `auth`: user, password, signup/login/logout/me, account command
- Spring Session JDBC와 Cookie policy
- P0 승인 계약의 durable Idempotency record·공통 저장·filter
- Testcontainers·MockMvc·Security test fixture

성공 DTO는 envelope 없이 반환하고 실제 HTTP status를 사용한다.

### 4.2 Frontend

- app bootstrap, QueryClient, Axios client
- typed 공통 오류와 field error
- CSRF bootstrap과 인증 상태 `unknown/authenticated/anonymous`
- PublicLayout/AppLayout, public-only/auth-required guard
- signup/login/onboarding shell
- logout/401에서 Query cache와 draft 폐기

### 4.3 DB/API/Page

- `users`, `user_profiles` 기본 행, Spring Session
- `/auth/signup|login|logout|me|csrf`
- `/signup`, `/login`, 보호 route 기본 shell

### 4.4 검증

- password 최소 길이와 BCrypt
- email 정규화·중복
- signup transaction: user+profile+session
- Session rotation, logout 무효화, CSRF 403
- 401/403 공통 오류와 다른 사용자 404
- 두 사용자 cache/session 분리

### 4.5 완료 조건

- AC-01 가입·로그인과 자기 데이터만 조회하는 negative test가 통과한다.
- Security filter와 ControllerAdvice가 같은 오류 필드를 반환한다.
- signup/login/logout route flow가 component 또는 E2E test로 고정된다.

## 5. P2 — 프로필·직접 입력 근거

- AC: AC-02
- 선행: P1
- 주 담당: backend, frontend
- 상태: 완료(2026-07-19). Backend·Frontend·실제 Chromium 검증과 read-only validator `PASS`

### 5.1 Backend

- `profile` 기본 프로필과 education/certification/language/award/career aggregate
- 날짜, 대표 학력, profile completion, version 규칙
- 구조화 record 저장·수정·삭제와 `profile_evidence` 동기화
- owner-scoped CRUD와 교차 evidence document 검증

### 5.2 Frontend

- onboarding과 `/profile/basic`
- profile 5종 route, form, list, modal/timeline
- Zod schema와 server field error 연결
- profile-recommended 경고
- 409 최신 데이터 재조회·충돌 UX

### 5.3 DB/API/Page

- profile 계열 6개 table과 직접 근거
- `/profile` 및 하위 CRUD
- `/onboarding`, `/profile/*`

### 5.4 검증

- 대표 학력 사용자당 1개
- 날짜 역전 금지
- 직접 입력 근거 기본 `VERIFIED`
- 원본 수정 동기화와 삭제 정책
- 다른 사용자 child UUID 404

### 5.5 완료 조건

- 학력·자격증·어학·수상·경력 각각 CRUD가 가능하다.
- 모든 직접 입력 record와 근거의 owner·source link가 일치한다.
- AC-02와 profile route/component test가 통과한다.

## 6. P3 — Agent Run·AI runtime 기반

- AC: AC-13의 공통 기반
- 선행: P1, P0의 async·품질·예산 결정
- 주 담당: backend 후 ai_workflow, frontend
- 상태: 완료(2026-07-19). Backend 243 tests, Frontend 78 tests·production build, Chromium fixture 2개와 최종 read-only validator `PASS`

### 6.1 Backend 선행

- `agentrun` domain, repository, API, 상태 전이
- run/step/usage/policy/preference migration
- DB-backed claim, reconciliation, stale recovery, cooperative cancel
- TaskExecutor와 queue capacity
- SSE owner 검증, snapshot, terminal, heartbeat adapter
- retry/idempotent domain apply port

### 6.2 AI workflow 후속

- `WorkflowRegistry`, `AgentOrchestrator`
- `ContextBuilder` contract
- `ModelRouter`, `BudgetGuard`, `PromptRegistry`
- provider-independent Chat/Embedding/Search port
- Fake model/search/embedding adapter
- structured output validation과 prompt/tool allowlist

### 6.3 Frontend

- Agent Run list/detail query
- SSE client, snapshot-first, reconnect, polling fallback
- progress drawer, safe error, retry/cancel
- run 종료 후 resource query invalidation

### 6.4 검증

- Fake 3-step workflow 정상·실패·retry·cancel
- restart 전 QUEUED와 stale RUNNING 복구
- 동일 input success step 재사용과 다른 input 미재사용
- 두 동시 run budget reserve
- SSE 다른 사용자 404, reconnect snapshot, terminal close
- lease보다 긴 blocking gateway 호출 중 주기 heartbeat와 reconciliation 경쟁
- 전체 prompt/response·user content 로그 부재

### 6.5 완료 조건

- 실제 provider 없이 Agent Run 전체 수명주기가 검증된다.
- DB가 상태 원천이고 SSE 유실이 UI 상태 유실로 이어지지 않는다.
- AC-13에 필요한 detail projection이 frontend와 일치한다.

## 7. P4 — 문서·근거 pipeline

- AC: AC-03
- 선행: P2, P3
- 주 담당: backend → ai_workflow → frontend
- 상태: 완료(2026-07-19). Backend 287 tests, Frontend 95 tests, 실제 PostgreSQL·MinIO·Spring·Vue·Fake AI·Chromium E2E 4개와 최종 read-only Validator `PASS`

### 7.1 Backend

- Object Storage adapter와 불투명 key
- upload validation, checksum, compensation
- document/text/chunk persistence
- Tika/PDFBox/POI parser와 manual text
- privacy masker와 chunk policy
- delete/outbox와 `SOURCE_DELETED` command
- document/detail/download URL API

### 7.2 AI workflow

- embedding step
- `ProfileExtractionAgent` structured schema
- evidence candidate validation
- input hash와 partial success 재사용

### 7.3 Frontend

- dropzone과 native input
- document list/detail와 상태
- SSE/REST 진행
- manual text, reparse, download, delete
- evidence filter·편집·approve/reject

### 7.4 검증

- 정상 PDF/DOCX/TXT
- MIME 위장, macro, 손상, 20MB 초과, 텍스트 부족
- Object 성공/DB 실패 보상과 delete retry
- PII masking, 타 사용자 vector·download 차단
- parser 성공/AI 실패 뒤 재시도
- evidence는 PENDING이고 자동 VERIFIED가 아님

### 7.5 완료 조건

- [x] AC-03과 E2E 시나리오 A의 업로드→수동 보완→근거 검토 구간이 통과한다.
- [x] 삭제 뒤 현재 참조 없는 evidence 삭제와 Fake 참조 contributor의 `SOURCE_DELETED` tombstone branch가 통과한다.
- [x] 공개 API는 문서 8개만 추가되어 전체 43 operations/30 paths다.
- [x] 단일 V5가 빈 DB와 V1/V2/V3/V4-only upgrade를 통과하고 V1–V4는 불변이다.
- [x] production 기본 provider는 `none`이고 Fake embedding·Chat·price catalog는 test scope에만 있다.
- [x] parser 성공 뒤 AI 실패에도 text·chunk와 `PARSED` 상태를 보존한다.
- [x] 최종 read-only Validator가 `PASS`를 반환한다.

### 7.6 정규 경험·의미 중복 판정 확장 (2026-08-07 Backend)

- [x] V26 `experience_items`, source link, policy-versioned `vector(1536)` 저장소와 기존 document evidence backfill
- [x] `document-ingestion-v2` 후보 임베딩 step과 durable `p0-contract-v1` 레거시 실행기 동시 등록
- [x] exact fingerprint, 사용자·category·policy 범위 cosine Top-K, anchor·숫자 충돌 보수적 판정
- [x] 동일 경험은 보강 출처만 연결하고 유사·충돌은 사용자 match resolution 대상으로 보존
- [x] `/profile/experiences` 조회·상세·편집·승인·match resolution 5 operations
- [x] 승인된 정규 경험의 원본 삭제·reparse 생존과 후속 AI 단일 canonical evidence 사용
- [ ] Frontend `/profile/experiences` 경험 보관함과 문서 상세 중복 표시 연결

## 8. P5 — 공고 등록·추출·상태·Scheduler

- AC: AC-04~06
- 선행: P3
- 주 담당: backend → ai_workflow → frontend
- 상태: 완료(2026-07-27). Backend 322 tests, Frontend 122 tests·production build, 격리 Chromium E2E 5개와 최종 read-only validator `PASS`

### 8.1 Backend

- [x] company/job aggregate와 owner-scoped CRUD
- [x] URL validation·canonicalization·중복
- [x] DNS 고정·redirect 재검사·절대 deadline의 SSRF-safe page extraction gateway
- [x] 업무 상태 command와 history transaction
- [x] batch 마감 Scheduler와 concurrency
- [x] content hash와 분석 stale 입력 기반
- [x] V16 공고 revision별 자동 분석 후속 의도·lease reconciliation·결정적 Agent Run ID

### 8.2 AI workflow

- [x] 고정 5단계 `JobPostingExtraction` structured workflow
- [x] 사용자 입력 우선 merge
- [x] 추출 실패 분류와 safe error

### 8.3 Frontend

- [x] jobs list tabs/filter/sort
- [x] new job form와 extraction progress
- [x] manual body/deadline 보완
- [x] detail overview, retry, edit, delete
- [x] 제출 이력 보조 badge
- [x] 공통 resource header·sticky detail tab·plain text document view와 편집 모드 분리
- [x] 등록→추출→자동 분석을 하나의 사용자 진행 여정으로 표시

### 8.4 검증

- [x] canonical duplicate와 Idempotency-Key
- [x] login/bot/JS page 수동 fallback
- [x] private URL·redirect·DNS rebinding·응답 제한
- [x] 모든 허용/금지 상태 전이
- [x] Scheduler와 user command race
- [x] submitted timestamp 보존
- [x] 수동 본문·URL 추출·본문 보완 자동 `BALANCED` 접수, replay/retry/restart 중복 방지와 budget 차단 보존

### 8.5 완료 조건

- [x] AC-04~06과 URL 실패 수동 보완, 세 상태 filter, 자동 마감 test가 통과한다.
- [x] 공개 API는 Job 7개가 추가되어 전체 50 operations/34 paths다.
- [x] V6가 빈 DB와 V5 upgrade를 통과하고 V1~V5는 불변이다.
- [x] 실제 외부 웹사이트·유료 provider 없이 P5 Browser E2E 5/5가 통과한다.
- [x] 최종 read-only Validator가 신규 finding 없이 `PASS`를 반환한다.

## 9. P6 — 공고 분석·RAG

- AC: AC-07
- 선행: P2, P4, P5, P3
- 주 담당: backend → ai_workflow → frontend

### 9.1 Backend

- V7 immutable `job_analyses`·criterion·VERIFIED evidence provenance
- stable content/profile/evidence/context snapshot hash
- 분석 접수·목록·최신 API와 OUTDATED projection
- eligibility·deterministic score·matched evidence domain validation
- 동일 snapshot reuse와 explicit force reanalysis
- usable 공고 revision의 durable 자동 `BALANCED` 접수, 제한 retry와 안전한 `BLOCKED|SUPERSEDED` projection

### 9.2 AI workflow

- 정확한 8단계 `job-analysis-v1`
- user-scoped exact cosine+lexical verified evidence retrieval
- structured requirement·eligibility·matching과 Java rubric score
- hallucinated/cross-user evidence와 prompt injection 차단
- Backend command port 전용 persist/reuse attach
- provider 호출 밖의 `SERIALIZABLE` checkpoint·domain apply 원자 완료

### 9.3 Frontend

- `/jobs/:jobId/analysis` 자동 진행·실패·성공과 보조 재분석 상태
- eligibility, fit score 안내, responsibilities/requirements
- strength, gap, matched evidence와 criterion breakdown
- analysis history와 OUTDATED UI
- 변경·삭제된 historical evidence의 기존 결과 유지와 현재 상태 안내
- `/agent-runs` 사용자 명칭 `AI 작업`과 Job Analysis resource link
- 최초 분석 품질 selector 제거, 결과 요약·다음 행동과 접힌 재분석 옵션

### 9.4 검증

- profile incomplete warning이 차단으로 바뀌지 않음
- 두 사용자 vector/context 완전 격리
- hash가 같을 때 cache, 바뀌면 stale/reanalysis
- 점수가 합격 확률로 표현되지 않음
- structured output invalid/timeout/budget failure
- P7/P8 route·domain 누수 없음

### 9.5 완료 조건

- [x] 분석 result의 모든 evidence reference가 같은 사용자에게 속한다.
- [x] 보정 후 Backend 352 tests, Frontend 169 tests, P6 migration 3개와 OpenAPI 53/37가 통과한다.
- [x] 실제 외부 provider 없이 fixture Chromium P6/Agent Run 3/3이 통과한다.
- [x] 수정된 actual P6 E2E evidence owner assertion과 wrapper DB assertion이 통과한다.
- [x] 기존 read-only validator가 두 구현 MAJOR 해소를 확인했고 새 검증 주기의 actual gate가 유일한 잔존 completion gap을 닫는다.

## 10. P7 — 자기소개서

- AC: AC-08~09
- 선행: P6
- 주 담당: backend → ai_workflow → frontend
- 현재 판정: `DONE` — final-source actual 검증과 최종 read-only validator `PASS`

### 10.1 Backend

- cover letter active cardinality
- cover letter 목록 query와 승인된 `ARCHIVED` command·transition
- question CRUD/order와 보존 정책
- answer immutable version/current transaction
- evidence link, verification, finalization command
- edit/restore 시 DRAFT 전이
- editor content sanitization과 server character count

### 10.2 AI workflow

- question planning/analysis
- evidence retrieval와 경험 배분
- writer와 fact check
- generation/verification workflow
- 문항별 멱등 반영과 partial result 정책

### 10.3 Frontend

- `/cover-letters` 목록, 상태 filter와 archive action
- question navigator, TipTap, char count
- 사용자·resource별 local draft
- evidence 선택, generation progress
- version history/compare/restore
- verification issue·근거·제안
- finalization eligibility

### 10.4 검증

- current version 한 개와 동시 저장
- restore가 과거 row를 변경하지 않음
- FINALIZED 편집 시 DRAFT
- 출처 없는 수치와 과장 역할 탐지
- maxLength·XSS·content normalization
- WARNING/FAILED 최종화 정책
- 승인된 `ARCHIVED` 전이, 목록 filter와 보관 후 edit 가능 범위
- logout/사용자 전환 draft 격리

### 10.5 완료 조건

- AC-08~09와 E2E 시나리오 B가 통과한다.
- 공고 SUBMITTED 전이는 자기소개서 최종화와 독립적이다.
- 구현 기준선: V8, 공개 API 17개, generation 8단계, verification 6단계, Frontend route 3개
- 검증 기준선: Backend 54 suites/380 tests, Frontend 53 files/211 tests, OpenAPI 51 paths/70 operations, P7 actual Chromium 1/1·DB assertions와 P6 회귀 Chromium 2/2 통과
- 1차 validator MAJOR: verification suggestion을 공개 계약의 최대 20개·항목 1~1000자로 Backend OpenAPI·AI output·Frontend Zod에서 통일하고, TITLE·QUESTION·ORDER·ANSWER·LIFECYCLE 409를 immutable 사용자 snapshot·실제 최신 server field·명시적 최신 CAS 재적용으로 보정했다.
- 최종 read-only validator는 두 MAJOR 해소와 전체 P7 계약을 새 finding 없이 `PASS`로 판정했고 전후 worktree fingerprint가 동일했다.

## 11. P8 — 면접 조사·예상 질문·답변 피드백

- AC: AC-10~11
- 선행: P6, P7, P3
- 주 담당: backend → ai_workflow → frontend
- 현재 판정: `DONE` — 구현·final-source 검증·두 번째 single-agent read-only self-audit 통과

### 11.1 Backend

- research run/source와 question set/question
- `POST /research-runs/{researchRunId}/retry`의 승인된 run identity·idempotency
- interview answer version/current transaction
- feedback persistence
- source metadata·provenance와 preparation placeholder 상태
- owner 일치와 prerequisite command

### 11.2 AI workflow

- Company/Interview research와 source 분류
- 출처 부족 결과
- InterviewQuestion generation
- immutable answer version feedback
- 공식/커뮤니티 신뢰 경계

### 11.3 Frontend

- job Interview tab의 preparation form
- `/interviews` 목록의 question set 영역과 filter
- research 진행·summary·source
- 실패한 research run 재시도와 Agent Run 연결
- question filter/card, answer editor/version
- feedback 진행·history

### 11.4 검증

- 공고 분석·문항 선행 조건
- BASIC/ADVANCED 검색 분리
- 공식/후기/커뮤니티 표시
- source 부족 성공 결과
- research retry가 성공 결과를 중복 생성하지 않음
- 외부 검색 query 개인정보 부재
- 새 answer version이 과거 feedback 연결을 바꾸지 않음

### 11.5 완료 조건

- AC-10~11과 E2E 시나리오 C의 조사·답변 feedback 구간이 통과한다.
- 구현 기준선: V12, 공개 API 11개, preparation 10단계, feedback 5단계, Frontend route 3개
- 검증 기준선: Backend 61 suites/407 tests, Frontend 60 files/238 tests, OpenAPI 63 paths/84 operations
- 실제 외부 provider 없이 P8 actual Chromium 1/1과 DB assertions, final-source P7 Chromium 1/1 및 P6 Chromium 2/2 회귀가 통과했다.
- 1차 single-agent read-only self-audit의 `FOLLOW_UP` output·foreign owner 404 finding을 한 번의 제한 보정으로 해소했고, 두 번째 감사는 새 finding 없이 `PASS`했다.
- 두 번째 감사 전후 178개 변경 파일 fingerprint는 `6cc19fff43393713a8a1276297144f1bd916ca3bfe0155cc7140ef909d5eff08`로 동일했다.

## 12. P8.5 — 외부 AI Provider 연결·로컬 개발 활성화 게이트

- 목적: P4~P8 고정 workflow가 실제 OpenAI Chat·Embedding과 Tavily를 호출할 adapter와 안전한 실행 profile을 제공한다.
- 선행 조건: P3~P8.
- 제외 범위: 실제 capability·수직 흐름 성공 판정, P9, 제품 기능 한도.
- 현재 판정: `IMPLEMENTED_NOT_LIVE_VERIFIED`.

### 12.1 구현된 책임

- Backend/AI responsibility: Chat·Embedding 요청별 model·timeout·output token/dimension, provider retry 0, 중앙 strict schema registry, 최종 `StructuredOutputValidator`, system/untrusted data 분리를 유지한다.
- DB responsibility: V13 immutable price catalog `2026073101`, 다중 usage, `provider_call_id`와 price item별 중복 방지를 유지한다.
- API/Frontend responsibility: 새 공개 operation·route 없이 기존 P4~P8 workflow를 실제 gateway에 연결한다.
- Security/Privacy: Tavily HTTPS·redirect 금지·2MB bounded stream, OpenAI response storage 비활성, 원문·prompt·response log 금지.
- State lifecycle: local은 real/fail-closed, local-offline은 disabled, test/CI/E2E는 Fake 또는 disabled/network 0이다.
- Idempotency/Concurrency: 기존 Agent Run claim과 provider call 중복 방지를 사용한다.
- Failure semantics: 실제 Provider failure를 disabled adapter로 fallback하지 않고 incurred usage를 보존한다.
- Migration responsibility: V13 완료, V1~V12 불변.

### 12.2 Test strategy·Actual E2E boundary·완료 조건

- local/local-offline Bean matrix와 key·provider·price fail-closed, request option, bounded response, reserve/top-up/settle을 Fake/WireMock/PostgreSQL로 검증했다.
- 기준선은 Backend 67 suites/420 tests, Frontend 60 files/238 tests, P8~P4 actual 1/1·1/1·2/2·5/5·4/4다.
- 초기 strict schema 거절은 strict-compatible schema 보정으로 해소됐다. 실제 문서 run `26f9b3d0-3bf7-4587-b2f7-938e8d8e045d`의 semantic 거절 뒤 output contract를 보정했고, 후속 run `bf26f44e-4512-414d-af1e-863076941535`는 Chat strict output, Java/workflow validation, trusted ref mapping, evidence persistence와 document finalize까지 성공했다. candidate 6건 중 4건 적용·2건 정상 filtering이었지만 rejection을 가짜 failed scope로 만든 projection과 공용 partial error 하드코딩으로 Run terminal만 잘못 실패했다.
- 문서 Provider output v2는 server-owned identifier와 동적 metadata를 제거하고 `C1` local ref를 trusted chunk UUID로 복원한다. parse/schema/binding은 재호출하지 않고, model-repairable record/workflow 오류만 safe correction guidance로 1회 추가 시도한다. 정확한 과거 invalid field와 output truncation은 기존 safe data만으로 미확정이다.
- candidate rejection과 독립 scope failure를 분리하고 workflow별 terminal partial policy를 offline 회귀로 검증했다. 이 terminal 보정 뒤 실제 Provider 재호출은 없으므로 전체 P8.5/P8.5-V를 `DONE`으로 올리지 않는다.
- 다음 phase handoff는 P8.5-V이며 실제 key·prompt·response를 저장소에 남기지 않는다.

## 13. P8.5-V — 사용자 local 실제 Provider 검증 gate

- 상태: `USER_LOCAL_VALIDATION_PENDING`.
- 목적: 구현을 반복하지 않고 일반 local에서 capability 연결과 P4~P8 제품 수직 흐름을 사용자가 검증한다.
- 선행 조건: P8.5와 사용자 소유 key·일반 local 환경.
- 제외 범위: adapter 수정, Codex 반복 호출, 품질 tuning, P8.6 코드.

### 13.1 책임·상태·보안

- Backend responsibility: 기존 local fail-closed 설정과 request ID·Agent Run ID·usage 합계를 제공한다.
- DB responsibility: 새 migration 없음. 실제 usage는 V13 ledger에 기록한다.
- API responsibility: 새 operation 없음. 구현된 P4~P8 API만 사용한다.
- Frontend/Page responsibility: 현재 실제 route에서 사용자 기능을 수행한다.
- Security/Privacy: key, prompt, response, 문서·자소서·면접 답변 원문을 기록하지 않는다.
- State lifecycle: `IMPLEMENTED_NOT_LIVE_VERIFIED → LOCAL_CAPABILITY_VERIFIED → LOCAL_VERTICAL_VERIFIED → DONE`.
- Idempotency/Concurrency: capability별 1회 smoke, 성공 capability 재호출 금지.
- Failure semantics: 연결 실패와 결과 품질 문제를 분리하고 safe error code만 기록한다.
- Migration responsibility: 없음.

### 13.2 Test strategy·Actual E2E boundary·완료 조건

- capability smoke: OpenAI Chat 1회, Embedding 1회, Tavily BASIC 1회.
- actual boundary: 문서→embedding/근거, 공고→추출/분석, 자소서→생성/검증, 면접→검색/질문/답변 feedback.
- 기록은 기능 성공, safe error code, request ID, Agent Run ID, usage/cost 합계만 허용한다.
- capability만 성공하면 `LOCAL_CAPABILITY_VERIFIED`; P4~P8 전체가 성공해야 `DONE`이다.
- 다음 phase handoff: P8.6은 병행 가능하지만 P9 선행 gate는 P8.5-V 완료 전 해제하지 않는다.

## 13.1-A 이미지형 채용 공고·문자셋 보정 (`DONE`)

- `job-posting-extraction-v2`를 9단계 fixed workflow로 올리고 v1은 과거 run 격리용 non-canonical 정의로 유지한다.
- header/BOM/meta/strict UTF-8/제한적 MS949 fallback과 EUC-KR·CP949 alias를 strict decoder로 고정한다.
- DOM 품질과 generic image 후보를 자동 판정하고 JPEG·PNG 최대 6개, 각 5MiB, 전체 20MiB를 기존 SSRF 경계 안에서 fetch한다.
- OpenAI image input은 별도 `ImageTextExtractionGateway`로 호출하며 provider retry 0, `store=false`, 기존 chat token price/usage를 재사용한다.
- text-only는 image provider 0회, image-only·mixed는 DOM/OCR source label 병합, 자동 자료 부족은 `NEEDS_MANUAL_INPUT`/`WAITING_USER`로 전환한다.
- 당시 schema와 공개 DTO 변경이 없어 migration을 추가하지 않았다. 이후 공고 자동 분석 의도가 V16을 사용했으므로 P8.6 tentative 번호는 V17 이후로 이동한다.

## 13.1-B 이미지 공고 후속 계약 보정 (`DONE`)

- canonical workflow·prompt·image output·compose output을 v3으로 올리고 v1·v2는 executable 없는 immutable legacy definition으로 유지한다.
- OCR output은 trusted local `imageRef`를 필수로 가지며 allowlist·중복·개수를 검증한 뒤 input 순서로 정렬한다. 누락 이미지는 reference를 당기지 않고 URL·bytes는 checkpoint에 저장하지 않는다.
- text/image OpenAI adapter의 structured schema·credentials·model·quota·rate limit·5xx·timeout/network와 refusal·finish reason·usage 보존 의미를 공통 safe 경계로 통일한다.
- Job 전용 retry contributor가 v1·v2·현재 terminal predecessor를 최신 v3과 현재 Job snapshot으로 승격하고 resource/generic retry의 predecessor unique successor를 공유한다. `WAITING_USER` manual body는 same-run resume을 유지한다.
- WebP ImageIO plugin으로 RIFF/WEBP magic·정적 image decode·dimensions·pixel을 검증하고 JPEG·PNG와 같은 SSRF·byte·deadline 경계를 적용한다. item minimum 20자와 final aggregate 120자를 분리하고 cross-image 반복 line을 제거한다.
- Frontend 공개 상태·step key는 변경하지 않았으며 기존 safe fallback label, 새 Run retry 갱신, manual CTA와 SSE reconnect 회귀를 유지한다.
- Fake·synthetic image·mock model·WireMock 계열의 offline 검증만 사용했다. 사용자가 기존 live 경로를 별도 확인했으며 이 보정 작업은 실제 OpenAI Provider와 실제 채용 사이트를 재호출하거나 live 상태를 재판정하지 않는다.
- DB migration과 공개 OpenAPI path/operation 변경은 없다.

## 14. P8.6 — 제품 기능 한도·metering 기반

- 상태: `PLANNED`.
- AC: AC-14.
- 목적: Provider USD budget과 독립된 사용자·기능·기간별 제품 사용 한도를 P4~P9 공통으로 원자 적용한다.
- 선행 조건: P3, P8.5 구현. P9 key는 계약만 고정한다.
- 제외 범위: 내부 원가 집계 UI, 실제 결제, Backoffice mutation, P9 기능 구현.

### 14.1 Backend·DB·API·Frontend responsibility

- Backend: `usage` module이 immutable policy, assignment/override, period, reserve/commit/release, reconciliation port를 소유한다.
- DB: tentative V19 `feature_usage_policy_versions/items`, `user_feature_usage_assignments/overrides`, `feature_usage_periods/reservations/events`. V15는 사용자 직접 대외활동, V16은 공고 자동 분석 후속 의도, V17~V18은 Dashboard Career Guide에 사용됐다.
- API: `GET /settings/usage`, `GET /settings/usage/history`를 `PLANNED`로 구현하고 `/usage/summary` 중복 경계를 만들지 않는다.
- Frontend/Page: API consumer와 enforcement 오류를 연결하며 전체 `/settings/usage` 화면은 P8.7에서 제공한다.
- Canonical key: document/job/cover letter/interview 7개와 P9 mock 3개를 고정한다.

### 14.2 Security·State lifecycle·Idempotency/Concurrency·Failure

- Security/Privacy: owner scope, Provider/model/price/internal cost 비노출, override cross-user 격리.
- State lifecycle: `RESERVED→COMMITTED|RELEASED|EXPIRED`; event append-only.
- Idempotency/Concurrency: replay 중복 소비 금지, period lock/CAS로 oversubscription 차단, lock order는 feature period→AI budget ledger다.
- Cache/reuse는 새 사용자 의도면 비용 0이어도 1 unit, 자동 retry는 같은 unit, Provider 전 실패는 release, Provider 후 실패·취소/partial success는 commit한다.
- Failure semantics: `429 FEATURE_USAGE_LIMIT_EXCEEDED`와 `429 RATE_OR_BUDGET_LIMIT_EXCEEDED`를 code·message·CTA로 분리한다.
- Withdrawal purge: 개인정보 row는 purge하고 승인된 비식별 aggregate만 보존한다.
- Migration responsibility: V19 `TENTATIVE`; 구현 완료된 V1~V18 수정 금지.

### 14.3 Test strategy·Actual E2E boundary·완료 조건

- Repository: 동시 요청 limit, replay unique, KST 기간 경계, override 격리, expired reconciliation.
- API: summary/history owner scope, unlimited/reset/canExecute, 두 429 code와 내부 정보 비노출.
- Workflow: P4~P8 접수 command가 feature reserve와 budget reserve를 모두 통과해야 resource/run을 만든다.
- Actual E2E: limit 직전 성공→도달 거절→reset/override 성공, replay 추가 소비 없음.
- 완료 조건: AC-14와 P4~P8 enforcement가 통과한다.
- 다음 phase handoff: P8.7은 feature event를 제품·과금 가능 usage source로 사용한다.

## 15. P8.7 — 사용자 사용량·내부 원가·과금 가능 usage 집계

- 상태: `PLANNED`.
- AC: AC-15.
- 목적: raw usage를 사용자·기간·기능·workflow·outcome별로 reconcile하고 미래 과금 단위를 0원 상태로 고정한다.
- 선행 조건: P8.6 feature event와 기존 `ai_usage_records`.
- 제외 범위: plan 판매, 실제 청구, PG, subscription, invoice/refund/tax, MRR.

### 15.1 Backend·DB·API·Frontend responsibility

- Backend: `billing` module이 immutable zero-rate policy, SQL read model과 reconciliation을 소유하며 payment 책임은 갖지 않는다.
- DB: tentative V20 `billing_policy_versions/items`, feature event billing snapshot 제약과 집계 index. 별도 billing event ledger는 만들지 않는다.
- API: P8.6의 `/settings/usage` summary/history를 완성한다.
- Frontend/Page: `/settings/usage`에서 사용량·남은 횟수·reset·기간 내역·현재 무료/청구 없음만 표시한다.
- Source: 내부 원가=`ai_usage_records`, 제품·과금 가능 unit=`feature_usage_events`.

### 15.2 Security·State lifecycle·Idempotency/Concurrency·Failure

- billing snapshot: policy version, quantity, unit, `METERED_ZERO_RATE|NO_CHARGE`; 고객 청구 금액은 0이며 내부 `cost_usd`를 복사하지 않는다.
- Security/Privacy: 사용자 API에 내부 원가·model·margin·타 사용자 정보 비노출, 탈퇴 뒤 비식별 정책.
- State lifecycle: raw append→SQL projection→watermark/reconciliation finding→append-only correction.
- Idempotency/Concurrency: provider call/price item와 feature event unique를 유지하며 집계 재실행은 동일 결과다.
- Failure semantics: aggregation lag는 stale로 표시하고 reconciliation 불일치를 숨기지 않는다.
- Migration responsibility: V20 `TENTATIVE`; aggregate table은 실제 p95/raw scan 근거 뒤 별도 승인한다.

### 15.3 Test strategy·Actual E2E boundary·완료 조건

- raw usage↔Agent Run cost↔feature event↔read model 합계, failed/retry/cache/reuse/0-cost/soft-delete/KST boundary/과거 policy 불변.
- Actual E2E: 기능 실행→사용량/잔여 갱신→history→청구 없음과 내부 원가 비노출.
- 완료 조건: AC-15 reconciliation과 usage page 접근성·반응형 통과.
- 다음 phase handoff: P8.8은 usage 발생 가능성과 두 limit category를 표시한다.

## 16. P8.8 — AI 실패 UX·복구

- 상태: `PLANNED`.
- AC: AC-16.
- 목적: safe error를 B2C category, 복구 CTA, 데이터 보존, request ID와 usage 발생 가능성으로 통일한다.
- 선행 조건: P8.6 기능 한도 오류, P8.7 usage 의미, 기존 Agent Run safe error.
- 제외 범위: Provider raw 오류, support ticket, 자동 retry 확대, P9 UI.

### 16.1 Backend·DB·API·Frontend responsibility

- Backend: stable safe code/상태→versioned public failure presentation mapping.
- DB: 기존 safe code를 사용하며 migration 없음.
- API: `AiFailurePresentationDto`를 Agent Run과 AI mutation 오류에 적용한다.
- Frontend/Page: `features/ai-failures/`의 panel/actions를 문서·공고·자소서·면접·Agent Run에 연결한다.
- `normalizeApiError(error).message` 직접 노출은 category presentation으로 이동한다.

### 16.2 Security·State lifecycle·Idempotency/Concurrency·Failure

- Security/Privacy: Provider/model/raw response/prompt/stacktrace/internal endpoint/secret 비노출.
- State lifecycle: API/Agent Run failure와 transport `CONNECTION_RECOVERING`을 분리하고 SSE 단절만으로 terminal 실패를 만들지 않는다.
- Idempotency/Concurrency: same request는 replay/복구, new retry만 새 logical request·usage를 만든다.
- Failure semantics: 12 category, 9 suggested action, `retryable`, `dataPreserved`, `requestId`, `usageMayHaveOccurred`.
- Migration responsibility: 없음.

### 16.3 Test strategy·Actual E2E boundary·완료 조건

- category 일관성, quota/budget 구분, retry CTA, draft/partial 보존, request ID, mobile/keyboard/screen reader, 내부 정보 비노출.
- matrix: 문서, 공고, 자소서, 면접, Agent Run과 P9 mock fixture.
- Actual E2E는 Fake/WireMock 장애만 사용하고 실제 Provider 장애를 유발하지 않는다.
- 완료 조건: AC-16 matrix 통과.
- 다음 phase handoff: P8.9-A는 category/request ID를 운영 dimension으로 사용한다.

## 17. P8.9-A — ADMIN 읽기 전용 Backoffice

- 상태: `PLANNED`.
- AC: AC-17.
- 목적: ADMIN이 사용자별 기능 사용량, 내부 원가, 실패, Agent Run, readiness와 policy version을 안전하게 조회한다.
- 선행 조건: P8.6, P8.7, P8.8.
- 제외 범위: override, run cancel/retry, account lock, kill switch, 결제 KPI, 사용자 원문.

### 17.1 Backend·DB·API·Frontend responsibility

- Backend: `backoffice` query module, ADMIN Security, provisioning command, access audit; domain query port/read model만 사용한다.
- DB: tentative V21로 `users.role USER|ADMIN`, provisioning/access audit를 추가하고 signup USER를 유지한다.
- API: overview, users/detail/usage, ai-costs, agent-runs, failures, configuration GET을 `/api/v1/backoffice` 아래 `PLANNED`로 구현한다.
- Frontend/Page: 별도 `BackofficeLayout`과 overview/users/usage/ai-costs/agent-runs/failures/configuration route. AppLayout에는 노출하지 않는다.

### 17.2 Security·State lifecycle·Idempotency/Concurrency·Failure

- Security/Privacy: Backend ADMIN 최종 권위, USER 거부, 검색·상세·drill-down audit, 원문/transcript/prompt/response/key 비노출.
- 최소 노출: 업무상 필요한 email, internal user ID, aggregate usage/cost, request ID.
- State lifecycle: provisioning/access audit append-only, readiness는 configuration/live 분리, aggregate watermark/lag 표시.
- Idempotency/Concurrency: stable pagination/sort/snapshot; provisioning은 idempotency와 expected current role.
- Failure semantics: audit 실패 시 민감 상세 조회 fail-closed, stale aggregate는 마지막 정상 시각 표시.
- Migration responsibility: V21 `TENTATIVE`; P8.9-B 번호 예약 금지.

### 17.3 Test strategy·Actual E2E boundary·완료 조건

- USER 거부, ADMIN 조회, cross-user isolation, access audit, 원문 비노출, pagination/sort, lag/readiness.
- Actual E2E는 provisioned test ADMIN fixture만 사용하고 실제 ADMIN 계정을 이번 문서 작업에서 만들지 않는다.
- 완료 조건: AC-17 read-only matrix 통과.
- 다음 phase handoff: P9는 mock usage/cost/failure/stuck state를 이 관찰 경계에 연결한다.

## 18. P8.9-B — 제한된 운영 mutation

- 상태: `PLANNED_LATER`; P9 필수 선행 아님.
- 목적: 별도 승인 뒤 feature override, run cancel/retry, account lock/unlock, Provider kill switch를 제공한다.
- 선행 조건: P8.9-A ADMIN 인가와 audit.
- 제외 범위: 결제·구독과 bulk destructive mutation.
- Backend/DB/API/Frontend responsibility: reason, expected version, idempotency, before/after, admin/request ID, 확인 UI와 audit를 함께 구현한다.
- Security/Privacy: least privilege/RBAC 필요성을 재평가한다.
- State lifecycle·Idempotency/Concurrency·Failure: optimistic version, partial success 금지, audit 실패 시 rollback.
- Migration responsibility: 실제 착수 시 next available; 번호 선점 금지.
- Test strategy·Actual E2E: action별 happy/denied/conflict/replay/audit/rollback.
- 완료 조건·handoff: 별도 사용자 승인 없이는 착수하지 않는다.

## 19. P9 — 모의 면접

- 상태: `BLOCKED_BY_P8_5V_TO_P8_9A`.
- AC: AC-12와 AC-14~17의 P9 적용.
- 목적: 기존 모의 면접 계약을 구현하되 quota, Provider budget, usage/billing, failure UX, Backoffice 기반을 공통 사용한다.
- 선행 조건: P3, P8.5-V, P8.6, P8.7, P8.8, P8.9-A.
- 제외 범위: P8.9-B, 음성/영상, 결제.

### 19.1 Backend·DB·API·Frontend responsibility

- Backend: session 상태/CAS, `clientRequestId`, message sequence, bounded synchronous turn, complete와 async feedback run.
- DB: P8.9-A 완료 시 next available migration(현재 예상 V22, `TENTATIVE`)에 mock session/turn/message/feedback과 owner FK를 구현한다.
- API: 기존 명세의 mock endpoints를 구현하며 merge될 때만 implemented path/operation 수를 갱신한다.
- Frontend/Page: `/mock-interviews/:sessionId`, 생성 form, READY/IN_PROGRESS/COMPLETED/CANCELLED, feedback 상태.
- AI: turn당 Chat 1회, Provider retry 0, structured `TurnDecision`, async aggregate feedback.

### 19.2 Quota·budget·usage·failure·Backoffice

- 기능 key: `MOCK_INTERVIEW_SESSION_CREATE`, `MOCK_INTERVIEW_TURN`, `MOCK_INTERVIEW_SESSION_FEEDBACK`.
- session 생성, turn, 질문 수 한도를 독립 적용한다.
- 같은 `clientRequestId`와 terminal failure replay는 중복 unit·Provider 호출이 없다. 새 ID retry는 새 unit이다.
- Provider budget은 turn USD cap, session sync cap, async feedback reserve를 기능 한도와 별도로 검사한다.
- feature usage, Provider usage/internal cost, billable quantity, session aggregate와 failure/retry를 기록한다.
- failure UX는 timeout, in-progress, replay, feature/budget limit, temporary Provider, safety, invalid output, session conflict를 구분한다.
- Backoffice는 active/stuck session, turn count, internal cost, failure category, feedback state를 표시하고 transcript는 비노출한다.

### 19.3 Security·State lifecycle·Idempotency/Concurrency·Failure

- Security/Privacy: owner composite FK, 타 사용자 404, 답변/prompt log 금지, Backoffice transcript 비노출.
- State lifecycle: `READY→IN_PROGRESS|CANCELLED`, `IN_PROGRESS→COMPLETED|CANCELLED`; feedback 독립 상태.
- Idempotency/Concurrency: session CAS, `(user,session,clientRequestId)` unique, message sequence, 다중 tab 경쟁 차단.
- Failure semantics: timeout/invalid output의 원 terminal 응답을 replay하고 same ID로 재호출하지 않는다.
- Migration responsibility: 현재 예상 V22는 tentative이며 시작 시 latest migration을 확인한다.

### 19.4 Test strategy·Actual E2E boundary

- 상태 전이, replay, 다중 tab, timeout 중복 호출 방지, 최대 질문 수, user 종료, async feedback 성공/실패.
- quota vs budget, feature/provider/billing 합계, Backoffice 관찰, 실패 CTA를 포함한다.
- Actual E2E는 사용자 검증된 local Provider에서 bounded turn/session을 별도 승인 범위로 수행하고 CI는 Fake/network 0을 유지한다.

### 19.5 완료 조건과 다음 phase handoff

- AC-12와 P9의 AC-14~17 적용, E2E 시나리오 C 전체가 통과한다.
- P10-A는 P9와 P8.7 사용자 projection을 Dashboard/settings shell에 통합한다.

## 20. P10-A — 사용자 Dashboard·설정

- 상태: `PLANNED`.
- 목적: server-owned Dashboard와 account/AI/usage/privacy 설정을 일관된 사용자 shell로 완성한다.
- 선행 조건: P1~P9, 특히 P8.7 `/settings/usage`.
- 제외 범위: 운영 concurrency, Backoffice mutation, 결제.
- Backend responsibility: canonical `GET /dashboard`, account/AI/privacy projection과 기존 usage API.
- DB responsibility: 기존 원천을 사용하고 필요성이 입증되지 않으면 migration 없음.
- API responsibility: Dashboard·settings 구현 시에만 OpenAPI implemented baseline을 갱신한다.
- Frontend/Page responsibility: `/settings/account|ai|usage|privacy`, loading/empty/error, quality와 usage 안내; 내부 원가 비노출.
- Security/Privacy: 사용자 owner scope, 회원 탈퇴 purge, Provider/model/internal cost 비노출.
- State lifecycle: 설정 version/CAS와 탈퇴 task 상태를 기존 계약대로 사용한다.
- Idempotency/Concurrency: settings 409 비교·재적용, 탈퇴 중복 접수 금지.
- Failure semantics: P8.8 panel과 usage CTA를 공통 사용한다.
- Migration responsibility: 기본 없음; 시작 시 계약 재확인.
- Test strategy: API projection, route guard, cache/draft purge, 접근성·반응형.
- Actual E2E boundary: dashboard→각 settings→usage→탈퇴 negative/confirmation.
- 완료 조건: 사용자 Dashboard/settings 계약과 관련 AC matrix 통과.
- 다음 phase handoff: P10-B.

## 21. P10-B — 운영 안정성·동시성

- 상태: `PLANNED`.
- 목적: 사용자·Provider별 동시성, queue/backpressure, graceful shutdown과 stale reconciliation을 실측 기반으로 고정한다.
- 선행 조건: P9와 P10-A의 실제 traffic shape.
- 제외 범위: 근거 없는 Kafka/Redis/microservice 도입.
- Backend responsibility: 사용자별 AI 작업 수, capability/provider limiter, queue saturation, 새 claim 차단, shutdown/reconciliation, API/worker role 분리 검토.
- DB responsibility: 기존 claim/lease/ledger 우선, 측정 근거가 있을 때만 next migration.
- API responsibility: 사용자에게 안전한 429/503과 retry/CTA; 내부 운영 endpoint 비공개.
- Frontend/Page responsibility: 대기·포화·복구 UI를 P8.8 category로 표시한다.
- Security/Privacy: metric label에 user ID·원문·prompt 금지.
- State lifecycle: new claim stop→active drain/interrupt→reconciliation.
- Idempotency/Concurrency: replay를 새 작업으로 계산하지 않고 reserve/claim lock order 유지.
- Failure semantics: circuit breaker는 failure rate/latency 근거 뒤에만 채택한다.
- Migration responsibility: 기본 없음, 실측 승인 전 번호 선점 금지.
- Test strategy: saturation, graceful shutdown, stale run, fairness, quota/budget race, metrics/alerts.
- Actual E2E boundary: controlled local load이며 실제 Provider에 부하를 주지 않는다.
- 완료 조건: 정의된 concurrency와 복구 SLO/alert 검증.
- 다음 phase handoff: P10-C.

## 22. P10-C — 출시 준비·전체 회귀

- 상태: `PLANNED`.
- 목적: AC-01~17, 사용자/ADMIN 격리, 실제 Provider UAT와 migration/rollback을 출시 gate로 검증한다.
- 선행 조건: P10-A, P10-B, P9.
- 제외 범위: 새 제품 기능과 paid plan.
- Backend/DB/API responsibility: fresh/upgrade migration, backup/restore, OpenAPI parity, rollback runbook.
- Frontend/Page responsibility: responsive/accessibility, route/API parity, 사용자/Backoffice navigation isolation.
- Security/Privacy: user/ADMIN isolation, 개인정보·secret scan, withdrawal purge와 audit 보존.
- State lifecycle: 모든 domain/Agent Run/quota/budget/billing/failure terminal matrix.
- Idempotency/Concurrency: replay/race/oversubscription/claim recovery 전체 회귀.
- Failure semantics: P8.8 category와 운영 runbook, rollback 후 안전 상태.
- Migration responsibility: P10-C 자체 schema 없음이 기본이며 필요한 변경은 별도 phase로 되돌린다.
- Test strategy: Backend/Frontend/Compose, AC matrix, P4~P9 actual, security/privacy, backup/restore.
- Actual E2E boundary: `local` real Provider UAT, `local-offline` boot, test/CI/E2E network 0을 각각 검증한다.
- 완료 조건: AC-01~17, fresh/upgrade, OpenAPI/frontend, 접근성, backup/restore, rollback PASS.
- 다음 phase handoff: release 승인. 미검증 actual 또는 `OPEN_DECISION_BLOCKER`가 있으면 출시하지 않는다.

## 23. 목표 package와 directory 생성 순서

target 구조는 설계 경계이며 phase가 시작되기 전 빈 directory를 대량 생성하지 않는다.

### 23.1 Backend

```text
com.hiresemble/
├─ common/                    # P1
├─ auth/                      # P1
├─ profile/                   # P2
├─ agentrun/                  # P3
├─ ai/                        # P3부터 workflow별 확장
├─ document/                  # P4
├─ job/                       # P5~P6
├─ coverletter/               # P7
├─ research/                  # P8
├─ interview/                 # P8~P9
├─ usage/                     # P8.6 제품 기능 한도·metering
├─ billing/                   # P8.7 과금 가능 usage policy·집계, 결제 제외
└─ backoffice/                # P8.9 ADMIN 운영 query/action
```

기능 package 내부:

```text
feature/
├─ api/
│  ├─ controller/             HTTP endpoint
│  ├─ dto/                    request/response contract
│  ├─ mapper/                 HTTP DTO conversion
│  └─ sse/                    SSE transport
├─ application/
│  ├─ service/                use case, transaction
│  ├─ port/                   application boundary
│  ├─ command/                mutation input
│  ├─ query/                  read input
│  ├─ model/                  application result/value
│  └─ config/                 application execution configuration
├─ domain/
│  ├─ model/                  aggregate, value, state
│  ├─ policy/                 invariant policy
│  ├─ service/                cross-model domain rule
│  ├─ repository/             domain repository port
│  └─ event/                  domain event
└─ infrastructure/
   ├─ persistence/            JPA/JDBC implementation
   ├─ adapter/                external adapter
   ├─ config/                 infrastructure configuration
   ├─ worker/                 background worker
   ├─ scheduling/             scheduler
   └─ event/                  infrastructure event bridge
```

위 하위 package는 허용 책임의 분류 기준이다. 실제 책임과 파일이 있는 package만 생성하고 미래 phase나 빈 계층을 선행 생성하지 않는다. `common`과 `ai`는 기존 전문 경계를 유지하며, package-private 결합을 해소하려고 접근 제한자를 넓히지 않는다. P1~P4의 구조 세분화는 파일 경로와 `package`·`import`만 바꾸며 API·DB·workflow 동작을 유지한다.

### 23.2 AI

```text
ai/
├─ orchestration/
├─ workflow/
├─ context/
├─ model/
├─ budget/
├─ prompt/
├─ agent/
├─ port/
└─ infrastructure/
```

- domain query/command port는 해당 기능 module의 backend 소유다.
- `ai/port`는 Chat/Embedding/Search 같은 provider 경계만 소유한다.

### 23.3 Frontend

```text
frontend/src/
├─ app/                       # P1
├─ router/                    # P1부터 route별 확장
├─ layouts/                   # P1, P8.9 BackofficeLayout
├─ shared/                    # P1부터 실제 공용 사용처
├─ stores/                    # auth/ui/draft
├─ pages/                     # phase별 route page, P8.9 pages/backoffice
└─ features/                  # phase별 feature, usage/ai-failures/backoffice
```

page는 조합, feature는 상호작용, Vue Query는 서버 상태, Pinia는 최소 전역 상태만 맡는다. Provider 원가 ledger는 기존 `agentrun`/`ai`, 제품 횟수는 `usage`, 과금 가능 unit은 `billing`, 운영 query는 `backoffice`에 두며 실제 결제 package는 만들지 않는다.

## 24. 에이전트별 작업 분배와 파일 소유권

### 24.1 역할

| 역할         | 소유 책임                                                                                                                          | 수정 금지                                   |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| root manager | 계약 결정, 공유 경계 순서, 최종 docs/index/progress                                                                                | 역할별 구현을 검증 없이 덮어쓰기            |
| backend      | domain, API/OpenAPI, Security, JPA/JDBC, Flyway, Storage/parser, URL fetch, Scheduler, TaskExecutor, Agent Run persistence/API/SSE | `ai/**` prompt/workflow, `frontend/**`      |
| ai_workflow  | `com.hiresemble.ai/**`, prompt resource, Spring AI/model/search adapter, Fake workflow tests                                       | Controller, domain entity, Flyway, frontend |
| frontend     | `frontend/**`의 UI/API consumer/query/store/test                                                                                   | backend, migration, spec 임의 변경          |
| validator    | 요구사항·diff·contract·test 읽기 검증                                                                                              | 모든 파일 수정                              |

### 24.2 경로별 단일 소유자

| 경로                                                                                        | 소유자       | 순서 규칙                                      |
| ------------------------------------------------------------------------------------------- | ------------ | ---------------------------------------------- |
| `backend/build.gradle.kts`                                                                  | backend      | AI dependency 요청은 handoff 후 backend가 반영 |
| `backend/src/main/resources/application*.yml`                                               | backend      | AI 설정 key 계약을 먼저 받고 단일 edit         |
| `backend/src/main/resources/db/migration/**`                                                | backend      | phase별 migration plan 승인 후 작성            |
| `com/hiresemble/common/**`                                                                  | backend      | 실제 2개 이상 사용처만 공통화                  |
| `backend/src/main/java/com/hiresemble/<feature>/{api,application,domain,infrastructure}/**` | backend      | domain public port 선행                        |
| `com/hiresemble/agentrun/**`                                                                | backend      | AI는 공개 application port만 소비              |
| `com/hiresemble/ai/**`                                                                      | ai_workflow  | domain entity/repository 직접 접근 금지        |
| `backend/src/main/resources/prompts/**`                                                     | ai_workflow  | prompt version과 schema 함께 관리              |
| `frontend/**`                                                                               | frontend     | 확정 OpenAPI/enum 후 구현                      |
| `docs/spec/**`                                                                              | root manager | 사용자 승인된 계약 변경만                      |
| 모든 `index.md`·`progress.md`                                                               | root manager | 서브 에이전트는 handoff만 반환                 |
| `docs/design/**`                                                                            | root manager | validator는 read-only                          |

### 24.3 역할 간 handoff

```text
root: 상태·DTO·DB 계약 승인
→ backend: domain/API/migration/public port
→ ai_workflow: workflow·provider adapter
→ frontend: typed consumer·UI
→ validator: AC/DB/API/page/state/isolation/test 검증
→ root: 작은 연결 수정과 추적 문서 통합
```

같은 Spring DTO·migration·workflow file을 backend와 AI가 동시에 수정하지 않는다.

## 25. 검증 에이전트 체크리스트

validator는 구현을 수정하지 않고 다음을 phase마다 확인한다.

- [ ] phase가 매핑한 AC가 실제 test로 검증됨
- [ ] 기능 상태와 DB CHECK, Java enum, API enum, frontend enum이 동일함
- [ ] DB FK/unique/nullable/cascade가 기능 수명주기와 일치함
- [ ] API success/error/status가 명세와 OpenAPI에 일치함
- [ ] 페이지의 모든 action·filter·state에 API가 존재함
- [ ] 모든 owner query, child join, vector, SSE, Object URL이 사용자 격리됨
- [ ] sync/async 기준과 200/201/202/204가 일치함
- [ ] retry·cancel·restart가 중복 도메인 결과를 만들지 않음
- [ ] backend domain과 AI workflow가 같은 규칙을 중복 구현하지 않음
- [ ] frontend cache/draft가 사용자 전환 때 폐기됨
- [ ] 실제 유료 provider 호출 없이 CI가 통과함
- [ ] docs/index/progress가 실제 상태와 일치함

## 26. 위험과 대응

| 위험                            | 조기 검증                                                 |
| ------------------------------- | --------------------------------------------------------- |
| 계약 누락을 구현자가 추측       | P0 OpenAPI·상태 matrix·migration plan                     |
| 사용자 데이터 교차 연결         | 두 사용자 fixture와 DB composite/owner join test          |
| TaskExecutor 유실·중복          | DB claim/reconciliation와 idempotent apply test           |
| 비용 동시성 초과                | reserve/settle 경쟁 test                                  |
| 문서·웹 prompt injection        | Tool allowlist, content delimiting, structured validation |
| 파일 parser/URL fetch 자원 고갈 | size/time/redirect/DNS/resource limit test                |
| 편집 version 유실               | optimistic lock, current partial unique, multi-tab test   |
| SSE 단절로 영구 진행 표시       | snapshot-first, terminal GET, polling fallback            |
| 과거 provenance 유실            | SOURCE_DELETED/soft delete/FK test                        |
| 프론트 local draft 노출         | user-scoped session storage와 logout purge test           |

## P6 구현 후 남은 위험

- 2026-07-30 final-source actual Browser E2E는 정상 분석·reuse·OUTDATED·재분석·근거 부족과 공고/분석/Run/evidence owner 404를 Chromium 2/2로 통과했고, 같은 wrapper의 분석·criterion·provenance·Run DB assertion도 통과했다.
- 최초 gate 실행에서 후속 assertion이 실제 `agent_runs.error_code` 대신 존재하지 않는 `safe_error_code`를 조회한 `TEST_HARNESS_DEFECT`가 확인됐다. assertion 컬럼명만 보정한 허용된 1회 재실행이 `BUILD SUCCESSFUL`로 종료됐다.
- 1차 read-only validator의 atomic apply와 historical evidence rendering MAJOR는 허용된 보정 라운드에서 `SERIALIZABLE` completion transaction, rollback·crash/restart와 근거 상태 전환 회귀로 수정했고 2차 validator가 해소를 확인했다.
- production provider adapter는 계속 명시적으로 비활성화되어 있다. 실제 provider를 연결할 때는 승인된 immutable price item과 model policy, timeout·network failure 분류와 heartbeat를 함께 검증해야 한다.
- V1~V6는 적용 이력으로 보존했고 P6 schema는 V7 forward migration으로 추가했다. P7 이후 result schema도 기존 migration 수정 없이 새 migration으로만 추가한다.
- P6 retrieval은 owner-scoped exact cosine과 direct evidence lexical fallback을 구현했다. ANN index 도입은 데이터 규모와 실행 계획을 측정한 뒤 결정한다.
- `EvidenceReferenceQueryPort`는 P6 provenance 참조를 반영해 분석에서 사용한 direct evidence 삭제를 차단한다.
- P3는 AC-13의 Agent Run·AI runtime 공통 기반만 완료한다. Dashboard·공개 설정은 P10-A, 운영 hardening은 P10-B~C 범위로 남긴다.
- P7 이후 도메인·API·UI를 phase 선행 관계보다 먼저 빈 package나 stub으로 만들지 않는다.
