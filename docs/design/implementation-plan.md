# Hiresemble 구현 계획

이 계획은 [전체 시스템 설계](system-architecture.md)를 AC-01~AC-13의 검증 가능한 수직 단계로 구현하기 위한 순서와 완료 조건을 정의한다. 공개 계약과 데이터 수명주기를 먼저 확정하고, 승인 근거→공고→자기소개서→면접의 도메인 선행 관계를 유지한다.

## 범위

- 포함:
  - 명세 결정, Backend·AI workflow·Frontend 구현 순서
  - package·directory 목표 구조
  - DB/API/page 연결과 단계별 완료 조건
  - 테스트, 검증, 파일 소유권과 역할 간 handoff
- 제외:
  - 이번 문서 작업에서 실제 비즈니스 코드·dependency·migration·API·UI를 변경하는 일
  - [기술 명세의 MVP 제외 범위](../spec/tech_stack.md#14-mvp-제외-범위)
  - 미결 계약을 임의 기본값으로 확정하는 일

## 실행 체크리스트

- [ ] P0에서 상태·enum·DTO·수명주기·AI 정책 결정 게이트를 닫는다.
- [ ] 공통 HTTP 오류, Security/Session/CSRF, request ID와 테스트 기반을 구현한다.
- [ ] 사용자·프로필·직접 입력 근거를 구현해 AC-01~02를 고정한다.
- [ ] Agent Run, 고정 workflow, Model Router, Context Builder, Budget Guard와 SSE 기반을 Fake로 검증한다.
- [ ] 문서 업로드·파싱·근거 검토를 구현해 AC-03을 고정한다.
- [ ] 공고 등록·수동 보완·상태·Scheduler·분석을 구현해 AC-04~07을 고정한다.
- [ ] 자기소개서 생성·검증·version·최종화를 구현해 AC-08~09를 고정한다.
- [ ] 면접 조사·출처·예상 질문·답변 피드백을 구현해 AC-10~11을 고정한다.
- [ ] 모의 면접과 비동기 종합 피드백을 구현해 AC-12를 고정한다.
- [ ] Dashboard·설정·Agent Run UX, 보안·복구·접근성과 전체 E2E로 AC-13 및 MVP 회귀를 완료한다.

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
                              P9 모의 면접
                                    ▼
                         P10 통합·운영 hardening
```

- P2와 P3은 P1 이후 파일 소유권이 겹치지 않으면 병렬화할 수 있다.
- P4와 P5도 Agent Run 공개 계약이 고정된 뒤 병렬화할 수 있다.
- P6은 최신 공고 본문, profile과 승인 근거가 필요하다.
- P7은 공고 분석과 version domain이 필요하다.
- P8은 공고 분석과 자기소개서가 필요하다.
- P9는 면접 질문·답변 context와 공통 AI executor가 필요하다.
- Frontend는 각 phase의 OpenAPI/DTO와 상태 계약이 backend에서 먼저 고정된 뒤 같은 수직 단계로 진행한다.

## 2. 전 단계 공통 완료 조건

각 단계는 다음을 모두 만족해야 완료다.

1. 해당 AC와 기능·DB·API·페이지 연결이 추적 가능하다.
2. 공개 DTO, 상태, 오류 code, ownership과 transaction 경계가 테스트로 고정됐다.
3. 모든 사용자 소유 조회·mutation·SSE·Object 접근에 owner scope가 있다.
4. 외부 호출과 장시간 처리는 DB transaction 밖에 있다.
5. migration은 빈 DB와 기존 DB upgrade에서 검증됐다.
6. 실제 유료 AI·검색 API 없이 Fake/WireMock 검증이 통과했다.
7. Backend 변경은 `.\gradlew.bat check`, Frontend 변경은 `corepack pnpm check`를 통과했다.
8. 주요 사용자 흐름 단계는 Playwright 또는 그 단계의 계약 test로 검증됐다.
9. 영향받은 `index.md`와 `progress.md`가 실제 상태를 반영한다.
10. 미검증·실패·후속 작업이 숨겨지지 않았다.

## 3. P0 — 계약 결정 기준선

- 우선순위: 최우선, 모든 구현의 차단 조건
- 담당: 루트 관리자 주도, backend·AI workflow·frontend 분석, validator 승인
- 코드 변경: 없음

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

- [전체 설계의 Gate A~C](system-architecture.md#22-구현-전-결정-게이트)에 미결정 항목이 없거나 명시적으로 MVP 이후로 제외됐다.
- Backend와 Frontend가 같은 DTO와 enum을 추측 없이 선언할 수 있다.
- migration 작성자가 nullable, cascade, owner, retry 의미를 추측할 필요가 없다.
- validator가 기능↔DB↔API↔페이지↔상태 matrix를 PASS로 판정한다.

## 4. P1 — 공통 HTTP·인증·테스트 기반

- AC: AC-01의 가입·로그인·격리 기반
- 선행: P0
- 주 담당: backend, frontend

### 4.1 Backend

- `common/api`: 오류 DTO, field error, factory
- `common/exception`: `ErrorCode`, typed exception, ControllerAdvice
- `common/security`: Security error writer, request ID, CSRF
- `auth`: user, password, signup/login/logout/me, account command
- Spring Session JDBC와 Cookie policy
- Idempotency 기반이 P0에서 승인된 경우 공통 저장·filter
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
- 전체 prompt/response·user content 로그 부재

### 6.5 완료 조건

- 실제 provider 없이 Agent Run 전체 수명주기가 검증된다.
- DB가 상태 원천이고 SSE 유실이 UI 상태 유실로 이어지지 않는다.
- AC-13에 필요한 detail projection이 frontend와 일치한다.

## 7. P4 — 문서·근거 pipeline

- AC: AC-03
- 선행: P2, P3
- 주 담당: backend → ai_workflow → frontend

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

- AC-03과 E2E 시나리오 A의 업로드→수동 보완→근거 검토 구간이 통과한다.
- 삭제 뒤 과거 provenance가 승인된 계약대로 보존된다.

## 8. P5 — 공고 등록·추출·상태·Scheduler

- AC: AC-04~06
- 선행: P3
- 주 담당: backend → ai_workflow → frontend

### 8.1 Backend

- company/job aggregate와 owner-scoped CRUD
- URL validation·canonicalization·중복
- SSRF-safe page extraction gateway
- 업무 상태 command와 history transaction
- 마감 Scheduler와 concurrency
- content hash와 분석 stale 입력 기반

### 8.2 AI workflow

- `JobPostingExtraction` structured step
- 사용자 입력 우선 merge
- 추출 실패 분류와 safe error

### 8.3 Frontend

- jobs list tabs/filter/sort
- new job form와 extraction progress
- manual body/deadline 보완
- detail overview, retry, edit, delete
- 제출 이력 보조 badge

### 8.4 검증

- canonical duplicate와 Idempotency-Key
- login/bot/JS page 수동 fallback
- private URL·redirect·응답 제한
- 모든 허용/금지 상태 전이
- Scheduler와 user command race
- submitted timestamp 보존

### 8.5 완료 조건

- AC-04~06과 URL 실패 수동 보완, 세 상태 filter, 자동 마감 test가 통과한다.

## 9. P6 — 공고 분석·RAG

- AC: AC-07
- 선행: P2, P4, P5, P3
- 주 담당: backend → ai_workflow → frontend

### 9.1 Backend

- immutable `job_analysis` version
- content/profile/evidence snapshot hash
- analysis list/latest API와 stale projection
- eligibility·score·matched evidence domain validation

### 9.2 AI workflow

- JobAnalysis, Eligibility, Evidence Retrieval, ExperienceMatcher
- user-scoped hybrid retrieval
- rubric-based fit score와 explanation
- 근거 없는 result 제거·경고

### 9.3 Frontend

- run/re-run
- eligibility, fit score 안내, responsibilities/requirements
- strength, gap, matched evidence
- analysis history와 OUTDATED UI

### 9.4 검증

- profile incomplete warning이 차단으로 바뀌지 않음
- 두 사용자 vector/context 완전 격리
- hash가 같을 때 cache, 바뀌면 stale/reanalysis
- 점수가 합격 확률로 표현되지 않음
- structured output invalid/timeout/budget failure

### 9.5 완료 조건

- AC-07과 E2E 시나리오 A 전체가 통과한다.
- 분석 result의 모든 evidence reference가 같은 사용자에게 속한다.

## 10. P7 — 자기소개서

- AC: AC-08~09
- 선행: P6
- 주 담당: backend → ai_workflow → frontend

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

## 11. P8 — 면접 조사·예상 질문·답변 피드백

- AC: AC-10~11
- 선행: P6, P7, P3
- 주 담당: backend → ai_workflow → frontend

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

## 12. P9 — 모의 면접

- AC: AC-12
- 선행: P8, P3
- 주 담당: backend → ai_workflow → frontend

### 12.1 Backend

- mock session 상태와 CAS/version
- client request ID/idempotency
- message sequence와 transaction 경계
- synchronous turn executor boundary
- complete command와 feedback run link

### 12.2 AI workflow

- structured `TurnDecision`
- 질문 선택, 답변 분석, follow-up/next
- 질문 수·호출 수·비용·timeout 상한
- immediate 또는 end-only feedback
- session aggregate feedback

### 12.3 Frontend

- `/interviews` 목록의 mock session 영역과 상태 filter
- 생성 form과 READY 설정 확인
- IN_PROGRESS 대화·전송 잠금·중복 복구
- cancel/complete
- COMPLETED + feedback pending
- feedback summary와 재연습

### 12.4 검증

- READY/IN_PROGRESS/COMPLETED/CANCELLED 전이
- 같은 client request 재전송 결과 재사용
- 다중 tab 경쟁과 message sequence
- timeout 뒤 중복 유료 호출 방지
- 최대 질문 수와 user 종료
- completed 뒤 async feedback 성공/실패
- `/interviews`에서 question set과 mock session 상세 진입

### 12.5 완료 조건

- AC-12와 E2E 시나리오 C 전체가 통과한다.

## 13. P10 — Dashboard·설정·운영 hardening

- AC: AC-13과 전체 MVP 회귀
- 선행: P1~P9
- 주 담당: 전 역할 순차, validator 최종

### 13.1 기능

- dashboard summary·quick action
- account/AI/privacy settings
- Agent Run list/detail/filter
- 회원 탈퇴와 Object/domain 삭제 복구
- 모든 loading/empty/error/disabled/success 상태
- 접근성·반응형·keyboard

### 13.2 운영·보안

- queue saturation와 backpressure
- stale run reconciliation 운영 test
- presigned URL TTL, session expiry, rate/budget
- parser resource limit, SSRF, prompt injection
- log/metric의 민감정보 검사
- Object/DB 일관성 reconciliation

### 13.3 검증

- Backend `.\gradlew.bat check`
- Frontend `corepack pnpm check`
- `docker compose config --quiet`
- Playwright 시나리오 A~C와 두 사용자 404
- OpenAPI↔frontend type↔page contract
- 빈 DB migration과 기존 DB upgrade
- 실제 유료 provider가 비활성인 local boot/CI

### 13.4 완료 조건

- AC-01~13 전체 matrix가 PASS다.
- 실패 복구·재시도·SSE 단절·사용자 격리 negative E2E가 통과한다.
- 미결정 공개 계약이나 추적되지 않은 생성물이 없다.

## 14. 목표 package와 directory 생성 순서

target 구조는 설계 경계이며 phase가 시작되기 전 빈 directory를 대량 생성하지 않는다.

### 14.1 Backend

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
└─ interview/                 # P8~P9
```

기능 package 내부:

```text
feature/
├─ api/                       Controller, HTTP DTO
├─ application/               use case, transaction, public port
├─ domain/                    aggregate, invariant, repository port
└─ infrastructure/            JPA/JDBC/external adapter
```

실제 책임이 없는 계층은 만들지 않는다.

### 14.2 AI

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

### 14.3 Frontend

```text
frontend/src/
├─ app/                       # P1
├─ router/                    # P1부터 route별 확장
├─ layouts/                   # P1
├─ shared/                    # P1부터 실제 공용 사용처
├─ stores/                    # auth/ui/draft
├─ pages/                     # phase별 route page
└─ features/                  # phase별 feature
```

page는 조합, feature는 상호작용, Vue Query는 서버 상태, Pinia는 최소 전역 상태만 맡는다.

## 15. 에이전트별 작업 분배와 파일 소유권

### 15.1 역할

| 역할         | 소유 책임                                                                                                                          | 수정 금지                                   |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| root manager | 계약 결정, 공유 경계 순서, 최종 docs/index/progress                                                                                | 역할별 구현을 검증 없이 덮어쓰기            |
| backend      | domain, API/OpenAPI, Security, JPA/JDBC, Flyway, Storage/parser, URL fetch, Scheduler, TaskExecutor, Agent Run persistence/API/SSE | `ai/**` prompt/workflow, `frontend/**`      |
| ai_workflow  | `com.hiresemble.ai/**`, prompt resource, Spring AI/model/search adapter, Fake workflow tests                                       | Controller, domain entity, Flyway, frontend |
| frontend     | `frontend/**`의 UI/API consumer/query/store/test                                                                                   | backend, migration, spec 임의 변경          |
| validator    | 요구사항·diff·contract·test 읽기 검증                                                                                              | 모든 파일 수정                              |

### 15.2 경로별 단일 소유자

| 경로                                          | 소유자       | 순서 규칙                                      |
| --------------------------------------------- | ------------ | ---------------------------------------------- |
| `backend/build.gradle.kts`                    | backend      | AI dependency 요청은 handoff 후 backend가 반영 |
| `backend/src/main/resources/application*.yml` | backend      | AI 설정 key 계약을 먼저 받고 단일 edit         |
| `backend/src/main/resources/db/migration/**`  | backend      | phase별 migration plan 승인 후 작성            |
| `com/hiresemble/common/**`                    | backend      | 실제 2개 이상 사용처만 공통화                  |
| `com/hiresemble/*/api                         | application  | domain                                         | infrastructure/**` | backend | domain public port 선행 |
| `com/hiresemble/agentrun/**`                  | backend      | AI는 공개 application port만 소비              |
| `com/hiresemble/ai/**`                        | ai_workflow  | domain entity/repository 직접 접근 금지        |
| `backend/src/main/resources/prompts/**`       | ai_workflow  | prompt version과 schema 함께 관리              |
| `frontend/**`                                 | frontend     | 확정 OpenAPI/enum 후 구현                      |
| `docs/spec/**`                                | root manager | 사용자 승인된 계약 변경만                      |
| 모든 `index.md`·`progress.md`                 | root manager | 서브 에이전트는 handoff만 반환                 |
| `docs/design/**`                              | root manager | validator는 read-only                          |

### 15.3 역할 간 handoff

```text
root: 상태·DTO·DB 계약 승인
→ backend: domain/API/migration/public port
→ ai_workflow: workflow·provider adapter
→ frontend: typed consumer·UI
→ validator: AC/DB/API/page/state/isolation/test 검증
→ root: 작은 연결 수정과 추적 문서 통합
```

같은 Spring DTO·migration·workflow file을 backend와 AI가 동시에 수정하지 않는다.

## 16. 검증 에이전트 체크리스트

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

## 17. 위험과 대응

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

## 미결 질문

- 공개 계약: 품질·version·질문 enum, 완전한 DTO/version/filter, CSRF·탈퇴, finalization·editor content, mock/SSE 계약을 어떤 안으로 승인할 것인가?
- 데이터 수명주기: tenant 복합 무결성, idempotency/outbox/lease schema, 삭제·provenance, research cardinality와 embedding dimension을 어떤 migration 기준으로 확정할 것인가?
- AI 운영 정책: retry/WAITING_USER/run identity, budget 가격·예약, 미승인 chunk, score/source coverage, 동기 mock turn 경계를 어떤 정책으로 확정할 것인가?
