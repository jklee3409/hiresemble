# Hiresemble 전체 시스템 설계

- 문서 상태: 구현 전 설계 기준선
- 기준 명세: [기능](../spec/functional.md), [DB](../spec/db.md), [API](../spec/api.md), [페이지](../spec/page.md), [기술 스택](../spec/tech_stack.md)
- 현재 구현 상태: 백엔드·프론트엔드 부트스트랩과 로컬 인프라만 존재하며 비즈니스 기능은 미구현
- 상세 실행 계획: [구현 계획](implementation-plan.md)
- P0 승인 전 결정안: [P0 계약 결정 제안서](p0-contract-decision-proposal.md)

이 문서는 다섯 기준 명세를 구현 구조로 연결한 파생 설계다. 명세를 대체하거나 변경하지 않으며, 충돌·누락이 있는 계약은 권장안과 보류 범위를 분리한다.

## 1. 해석 기준과 결정 상태

### 1.1 우선순위

1. 현재 사용자 요청
2. `docs/spec/`의 기능·API·DB·페이지·기술 명세
3. 저장소 작업 규칙
4. 현재 코드와 추적 문서에서 확인한 구현 사실

### 1.2 결정 상태

| 상태   | 의미                                                        | 구현 처리                                     |
| ------ | ----------------------------------------------------------- | --------------------------------------------- |
| 확정   | 여러 명세가 같은 계약을 명시하거나 상위 명세가 명확함       | 설계와 테스트 기준으로 사용                   |
| 권장   | 명세의 목표를 만족하는 해결안이지만 공개 계약 변경이 필요함 | 문서에 제안만 기록하고 계약 승인 전 구현 보류 |
| 미결정 | 명세끼리 충돌하거나 필수 정보가 없음                        | 관련 migration·공개 DTO·상태 전이 구현 차단   |

### 1.3 명시적 설계 가정

- MVP 배포는 명세대로 Spring Boot 단일 인스턴스와 Vue SPA, PostgreSQL, S3 호환 Object Storage를 사용한다.
- 장기 작업의 상태 원천은 PostgreSQL이며 SSE는 상태 전달 수단이다.
- API 예시 JSON은 필드가 완전하다고 명시되지 않은 경우 완전한 DTO 계약으로 간주하지 않는다.
- 보안상 필요한 URL SSRF 차단, 외부 콘텐츠의 prompt injection 격리, 로그 민감정보 제거는 제품 기능을 확장하는 가정이 아니라 기존 보안·통제형 workflow 원칙의 구현 통제로 취급한다.
- 아래 `미결정` 항목은 임의 기본값으로 확정하지 않는다.

## 2. 프로젝트 목적과 핵심 사용자 흐름

Hiresemble은 사용자가 직접 입력하거나 문서에서 추출한 뒤 승인한 경력 근거를 중심으로 채용 공고 적합도 분석, 자기소개서 생성·검증·버전 관리, 출처 기반 면접 준비와 대화형 모의 면접을 제공하는 개인 맞춤형 AI 취업 준비 서비스다.

```text
가입·세션 생성
→ 기본 프로필과 구조화 경력 등록
→ 이력서·포트폴리오 업로드
→ 비동기 파싱·마스킹·청킹·근거 후보 추출
→ 사용자의 근거 승인·수정·거절
→ 공고 URL 등록·본문 추출 또는 수동 보완
→ 공고 분석·지원 자격·강점·부족점·적합도 확인
→ 자기소개서 문항 등록
→ 문항별 AI 초안·근거 검증
→ 사용자 편집·버전 저장·복원·최종화
→ 회사·유사 직무 면접 조사와 출처 확인
→ 예상 질문·답변 버전·피드백
→ 대화형 모의 면접·종합 피드백
```

문서 파싱 실패나 공고 URL 추출 실패는 사용자 계정 또는 공고 레코드 생성을 취소하지 않는다. 수동 입력 경로를 제공하고, AI 결과는 사용자가 명시적으로 저장·최종화하기 전 확정 제출물로 보지 않는다.

## 3. MVP 범위와 제외 범위

### 3.1 MVP

- 이메일·비밀번호 기반 가입, 로그인, 로그아웃, 현재 사용자와 계정 설정
- 기본 프로필과 학력·자격증·어학·수상·경력 CRUD
- 직접 입력 레코드와 문서 추출 결과를 연결하는 사용자 근거 관리
- PDF·DOCX·TXT 업로드, 파싱, 수동 텍스트 보완, 근거 검토, 삭제
- 단일 공고 URL 등록, 추출, 직접 보완, 업무 상태·마감 자동 처리
- 공고 분석, 지원 가능 여부, 적합도, 강점·부족점과 근거 매칭
- 자기소개서 문항, AI 생성, 사실 검증, 사용자 편집, 버전·복원·최종화
- 회사·면접 정보 조사, 출처, 예상 질문, 답변 버전과 피드백
- 텍스트 기반 모의 면접과 세션·메시지·종합 피드백
- Agent Run 진행률, 단계, 비용, 실패, 취소, 재시도와 SSE UI
- AI 품질·비용 및 개인정보 상태 설정

### 3.2 제외

- 채용 사이트 전체 자동 수집·크롤링
- 소셜 로그인
- 이미지 PDF OCR, HWP, PPTX 직접 파싱
- 음성 STT/TTS와 영상 면접 분석
- 관리자, 결제, 구독
- 다국어 UI
- 실시간 공동 편집
- 별도 Python/LangGraph Agent 서버
- Kafka·Redis 기반 분산 처리

이 제외 항목을 위한 빈 package, UI, 확장 API를 MVP 선행 작업으로 만들지 않는다.

## 4. 기술 스택과 아키텍처 원칙

| 영역        | 기준                                                                                   |
| ----------- | -------------------------------------------------------------------------------------- |
| Backend     | Java 21, Spring Boot 4.1, Spring Web MVC, Security, Session JDBC, Data JPA, JdbcClient |
| AI          | Spring AI 2.0, ChatClient, Structured Output, 제한된 Tool Calling, pgvector            |
| Database    | PostgreSQL 18, pgvector 0.8, Flyway, UUID, `timestamptz`, JSONB                        |
| File        | Apache Tika, PDFBox, POI, S3 호환 Object Storage                                       |
| External    | 공고 URL용 Jsoup 계열 추출 gateway, 조사용 Tavily gateway                              |
| Frontend    | Vue 3, TypeScript 5, Vite, Router, Pinia, Vue Query, Axios, PrimeVue, TipTap, Zod      |
| Test        | JUnit 5, Testcontainers, MockMvc, WireMock/Fake, Vitest, Vue Test Utils, Playwright    |
| Local infra | Docker Compose의 PostgreSQL/pgvector, MinIO, 선택적 Mailpit                            |

핵심 원칙:

1. 비즈니스 데이터와 상태 전이의 원천은 Spring Boot와 PostgreSQL이다.
2. Spring AI는 모델·embedding·구조화 출력·제한된 도구 호출의 adapter다.
3. Agent는 자유 루프가 아니라 코드로 정의한 유한 workflow를 실행한다.
4. 외부 경계와 단계 입출력은 Java record/DTO와 검증 가능한 schema를 사용한다.
5. 외부 호출은 DB transaction 밖에서 수행하고 결과 반영만 짧은 transaction으로 처리한다.
6. 모델명·provider·비용은 정책으로 라우팅하고 코드에 하드코딩하지 않는다.
7. 원문과 개인정보는 최소화·마스킹하며 전체 prompt/response를 로그에 남기지 않는다.
8. 단일 인스턴스 MVP라도 사용자 격리, 멱등성, 재시작 복구를 처음부터 적용한다.

## 5. 전체 시스템 아키텍처

```text
[Vue SPA]
  ├─ Router / Layout / Feature UI
  ├─ Vue Query(server state) / Pinia(auth·UI·draft)
  └─ Axios + same-origin EventSource
              │ HTTPS, Session Cookie, CSRF, /api/v1
              ▼
[Spring Boot Modular Monolith]
  ├─ API adapters: Controller, DTO, validation, Security error writer
  ├─ Application: use case, transaction, ownership, state commands
  ├─ Domain: aggregate, invariant, transition
  ├─ Agent Run: durable state, dispatcher, recovery, SSE
  ├─ AI orchestration: fixed workflow, context, router, budget, prompt
  └─ Infrastructure adapters
       ├─ JPA/JdbcClient ───────────────> PostgreSQL + pgvector
       ├─ S3 client ────────────────────> MinIO/S3/R2
       ├─ parser / URL extraction ──────> local parser / remote page
       ├─ Spring AI gateway ────────────> LLM / Embedding provider
       └─ WebSearchGateway ─────────────> Tavily
```

### 5.1 요청 경계

- Controller는 HTTP, 인증 사용자, DTO 변환만 담당한다.
- Application service는 소유권, use case transaction, idempotency와 workflow 접수를 조정한다.
- Domain은 상태 전이와 불변식을 담당한다.
- Infrastructure는 DB, Object Storage, URL, 검색, 모델을 구현한다.
- AI workflow는 도메인 entity/repository를 직접 변경하지 않고 공개 query/command port를 사용한다.

### 5.2 배포 경계

- Frontend: 정적 자산
- Backend: 단일 Spring Boot 프로세스
- Database: PostgreSQL 단일 인스턴스
- Object Storage: 개발 MinIO, 운영 S3/R2
- 외부 provider: 추상화 gateway 뒤에서 명시적으로 활성화

## 6. 백엔드·AI 워크플로·프론트엔드 모듈 책임

### 6.1 백엔드 기능 모듈

| 모듈          | 책임                                                | 소유 데이터                      |
| ------------- | --------------------------------------------------- | -------------------------------- |
| `common`      | 오류 계약, request ID, 시간, 공통 보안, 멱등 기반   | 공통 기술 데이터                 |
| `auth`        | 사용자, 비밀번호, Session, 계정 수명주기            | `users`, Spring Session          |
| `profile`     | 기본 프로필, 구조화 이력, 근거 상태·동기화          | profile 계열, `profile_evidence` |
| `document`    | 업로드, metadata, parser, text, chunk, storage 보상 | document 계열                    |
| `job`         | 공고, 추출 상태, 업무 상태·이력, 분석 version       | job 계열, `companies`            |
| `coverletter` | 지원서, 문항, 답변 version, 검증·최종화             | cover letter 계열                |
| `research`    | 회사·면접 조사 실행과 출처                          | research 계열                    |
| `interview`   | 질문 세트, 답변, 피드백, mock session               | interview 계열                   |
| `agentrun`    | 실행·단계 상태, claim, 복구, 취소, SSE, 사용량 조회 | agent/AI 정책 계열               |

### 6.2 AI workflow 모듈

| 구성                | 책임                                                                                |
| ------------------- | ----------------------------------------------------------------------------------- |
| `WorkflowRegistry`  | workflow type/version별 고정 단계·입출력·허용 Tool·진행률·실패 정책                 |
| `AgentOrchestrator` | run 상태 전이, 단계 순서, 재시도·취소·복구·재사용·결과 반영 조정                    |
| `ContextBuilder`    | 사용자별 최신 resource와 provenance를 조회·마스킹·순위화·token 절단해 snapshot 생성 |
| `ModelRouter`       | task, 공개 품질 선택, capability, 활성 정책을 provider/model로 해석                 |
| `BudgetGuard`       | 실행 전·호출 전·호출 후 비용 한도 검사와 예약·정산 경계                             |
| `PromptRegistry`    | 검토되고 version이 있는 prompt와 structured schema 제공                             |
| `AgentExecutor`     | Chat/Embedding/허용 Tool 호출, timeout, schema 검증, 안전한 오류 변환               |
| `ExecutionRecorder` | run/step, prompt/model version, token·비용, 안전한 오류와 진행 event 기록           |

AI 모듈은 “어떤 결과를 도메인에 저장할 수 있는가”를 결정하지 않는다. 도메인 command가 사용자 소유권과 불변식을 다시 검증한다.

### 6.3 프론트엔드

| 영역          | 책임                                                              |
| ------------- | ----------------------------------------------------------------- |
| Router/Layout | public-only, auth-required, profile-recommended 정책과 공통 shell |
| Page          | route parameter와 feature 조합, page-level 상태                   |
| Feature       | form, table, editor, 상태 전이 UI                                 |
| API client    | Session/CSRF, 직접 성공 DTO, typed error, idempotency header      |
| Vue Query     | 서버 상태, filter, pagination, invalidation, snapshot polling     |
| Pinia         | 인증 사용자, 전역 UI, 네트워크 전 임시 draft만                    |
| SSE client    | best-effort 진행 event 수신, 재연결, REST snapshot 보정           |

서버 entity나 권한 판단을 Pinia에 별도 원천으로 복제하지 않는다.

## 7. 도메인 모델과 모듈 의존 관계

### 7.1 aggregate

| Aggregate root       | 주요 자식/참조                                             | 핵심 불변식                                         |
| -------------------- | ---------------------------------------------------------- | --------------------------------------------------- |
| User                 | profile, education, certification, language, award, career | 정규화 email unique, 사용자 소유                    |
| Document             | text, chunks, extracted evidence                           | file 검증, parse 상태, 삭제 provenance              |
| ProfileEvidence      | source entity/document                                     | 사용자 검토 상태, source 추적                       |
| JobPosting           | status history, analyses                                   | 업무 상태와 추출 상태 분리, owner, analysis version |
| CoverLetter          | questions, answer versions, verification                   | current version 1개, 편집 시 DRAFT                  |
| ResearchRun          | sources                                                    | source metadata·출처 유형·조회 시점                 |
| InterviewQuestionSet | questions, answer versions, feedback                       | job/cover letter owner 일치                         |
| MockInterviewSession | messages, feedback                                         | 순서, session 상태, 최대 질문 수                    |
| AgentRun             | steps, usage                                               | 유한 상태, 단계 attempt, owner, 비용                |

### 7.2 의존 방향

```text
auth
 └─ profile
     ├─ document ── evidence command ─────────────> profile
     └─ job ─────── evidence query ───────────────> profile
          ├─ coverletter ─────────────────────────> job + profile
          └─ research/interview ──────────────────> job + coverletter + profile

domain API ── WorkflowLauncher port ──────────────> agentrun/ai
ai workflow ── read/command ports ───────────────> domain modules
```

- 기능 모듈은 Spring AI의 concrete class를 의존하지 않는다.
- AI workflow는 JPA repository를 직접 횡단하지 않는다.
- 교차 aggregate FK를 받을 때 인증 사용자와 모든 상위 resource의 owner 일치를 검증한다.
- 공통 package에는 실제 둘 이상의 도메인이 공유하는 기술 계약만 둔다.

## 8. 기능·DB·API·페이지 구현 연결

| AC    | 기능                | DB                                                          | API                                   | 페이지                             |
| ----- | ------------------- | ----------------------------------------------------------- | ------------------------------------- | ---------------------------------- |
| AC-01 | 가입·로그인·격리    | `users`, `user_profiles`, Spring Session                    | `/auth/*`                             | `/signup`, `/login`, 보호 route    |
| AC-02 | 프로필 5종 CRUD     | profile 6개 테이블, `profile_evidence`                      | `/profile`, `/profile/*`              | `/onboarding`, `/profile/*`        |
| AC-03 | 문서·근거           | `documents`, texts, chunks, evidence, run                   | `/documents/*`, `/profile/evidence/*` | `/documents*`, `/profile/evidence` |
| AC-04 | 공고 등록·수동 보완 | `job_postings`, company, run                                | `POST /jobs`, edit, retry             | `/jobs/new`, job overview          |
| AC-05 | 공고 상태 필터      | `job_postings`, history                                     | jobs list, status patch               | `/jobs`                            |
| AC-06 | 자동 마감           | `job_postings`, history                                     | 목록·상세에 결과 노출                 | jobs/dashboard                     |
| AC-07 | 공고 분석           | `job_analyses`, evidence, run                               | job analysis endpoints                | job analysis tab                   |
| AC-08 | 자소서 생성·검증    | cover letter questions, versions, links, verifications, run | generate, verify                      | cover letter editor                |
| AC-09 | 편집·버전·복원      | answer versions                                             | version list/create/restore           | editor history drawer              |
| AC-10 | 면접 조사·출처      | research runs/sources                                       | preparation, research endpoints       | job interview tab, question set    |
| AC-11 | 질문 답변·피드백    | interview answer versions/feedback                          | answer/feedback endpoints             | question set detail                |
| AC-12 | 모의 면접           | mock sessions/messages/feedback                             | mock session endpoints                | mock interview page                |
| AC-13 | 장기 작업 상태      | runs/steps/usage                                            | run list/detail/events/retry/cancel   | header drawer, run pages           |

### 8.1 보조 기능 연결

- AI 설정: `user_ai_preferences`, `ai_model_policies`, usage → `/settings/ai` → `/settings/ai`
- 개인정보 상태: user consent와 document aggregate → `/settings/privacy` → `/settings/privacy`
- 계정 설정: `users`와 Session → `/account/*` → `/settings/account`
- 자기소개서 목록·보관: `cover_letters.status` → `GET /cover-letters`, `POST /cover-letters/{id}/archive` → `/cover-letters`와 job Cover Letter tab
- 조사 재시도: `research_runs`, `agent_runs` → `POST /research-runs/{id}/retry` → 질문 세트의 조사 상태·재시도 UI
- 면접 준비 목록: question set과 mock session aggregate → `GET /interview-question-sets`, `GET /mock-interview-sessions` → `/interviews`
- Dashboard: 여러 aggregate의 summary가 필요하지만 현재 전용 API 계약은 없다.

## 9. 문서 업로드·파싱·근거 추출 흐름

### 9.1 접수

1. 인증·CSRF와 `Idempotency-Key`를 검증한다.
2. 파일당 20MB, 확장자, 탐지 MIME, Office macro를 검증한다.
3. 사용자 입력 파일명과 분리된 불투명 storage key를 생성한다.
4. Object를 저장하고 checksum을 계산한다.
5. 짧은 transaction에서 `documents(UPLOADED)`와 `agent_run(QUEUED)`을 만든다.
6. DB 실패 시 Object를 보상 삭제한다.
7. commit 뒤 dispatcher가 실행한다.

### 9.2 처리

```text
UPLOADED
→ PARSING
→ deterministic text extraction
→ document_texts
→ privacy masking
→ document_chunks
→ masked embedding
→ ProfileExtractionAgent structured output
→ domain validation
→ profile_evidence(PENDING)
→ PARSED
```

- PDFBox/POI/Tika의 parser metadata와 페이지 범위를 남긴다.
- embedding에는 `masked_content`를 사용하고 model과 dimension을 함께 기록한다.
- 추출 근거는 자동 승인하지 않는다.
- 사용자가 수정 후 `VERIFIED` 또는 `REJECTED`로 전환한다.
- 직접 입력 profile record의 동기화 근거는 기본 `VERIFIED`다.

### 9.3 실패·수동 보완·삭제

- 텍스트 기준 미달은 `NEEDS_MANUAL_TEXT`로 표시하고 수동 텍스트를 같은 masking/chunk/extraction pipeline에 다시 넣는다.
- parser 성공 후 AI 근거 추출만 실패하면 이미 추출한 text/chunk를 보존하고 실패 단계부터 재실행한다.
- 손상·지원 불가·일시 외부 오류는 안전한 오류 code로 분류한다.
- Object 삭제와 DB 상태 변경은 분리 실패할 수 있으므로 재시도 가능한 outbox가 필요하다.
- 과거 답변이 참조한 근거는 `SOURCE_DELETED`로 provenance를 보존하고 원문·text·chunk는 삭제하는 안이 권장되지만 계약 확정이 필요하다.

## 10. 채용 공고 등록·분석·상태 변경 흐름

### 10.1 등록·추출

1. URL 문법과 안전성을 동기 검증한다.
2. canonical URL과 사용자 범위 중복을 검사한다.
3. `job_posting(status=IN_PROGRESS, extractionStatus=QUEUED)`과 run을 만든다.
4. 공고 전용 URL gateway가 HTTP(S) 단일 페이지를 가져오고 Jsoup 계열로 정제한다.
5. 구조화 단계가 회사·직무·본문·마감일 후보를 만든다.
6. 사용자 입력값을 자동 추출값보다 우선 병합한다.
7. 본문 hash와 추출 상태를 저장한다.

사용자 URL fetch는 검색 provider와 분리한다. private/link-local/loopback, 자격증명 포함 URL, redirect 후 재검증 실패, 응답 크기·시간 초과를 차단한다.

### 10.2 분석

```text
latest job snapshot
→ JobAnalysis
→ Eligibility
→ verified evidence retrieval
→ ExperienceMatcher
→ rubric/result validation
→ immutable job_analysis version
```

- 적합도는 합격 확률이 아니라 공고 요구와 등록 정보의 일치도다.
- 분석 입력에는 공고 content hash와 profile/evidence snapshot hash를 포함한다.
- 최신 hash와 분석 hash가 다르면 stale 후보지만 공개 `OUTDATED` 계약은 미결정이다.
- 등록 후 URL 추출과 분석은 별도 endpoint·run이며 자동 연쇄 실행으로 가정하지 않는다.

### 10.3 업무 상태

```text
IN_PROGRESS → SUBMITTED
IN_PROGRESS → CLOSED
SUBMITTED   → CLOSED
CLOSED      → IN_PROGRESS | SUBMITTED
```

- 상태 변경과 `job_status_history`는 한 transaction이다.
- 최초 `submitted_at`은 CLOSED 전환 뒤에도 보존한다.
- 매시간 Scheduler가 마감 대상 `IN_PROGRESS|SUBMITTED`를 `CLOSED`로 바꾸고 `DEADLINE_PASSED`를 기록한다.
- 사용자 변경과 Scheduler 경쟁은 optimistic lock 또는 조건부 update로 한쪽만 성공시키고 충돌을 409로 노출한다.
- reopen 시 timestamp mutation의 세부 계약은 미결정이다.

## 11. 자기소개서 생성·검증·버전 관리 흐름

### 11.1 준비

- 공고에 active 자기소개서와 문항을 만든다.
- 문항에는 순서, 내용, 글자 수 제한, memo가 있다.
- AI 생성 입력은 최신 공고 분석, profile, `VERIFIED` 근거, provenance가 있는 관련 masked chunk, 사용자 선택 근거, 문항과 제한, 이미 존재하는 공식 회사 조사다.

### 11.2 생성

“문항별 독립 생성”과 “지원서 전체 경험 중복 최소화”를 함께 만족시키기 위한 실행 구조:

```text
selected questions
→ question planning
→ per-question analysis
→ evidence retrieval
→ cover-letter-wide experience allocation
→ per-question writer
→ per-question fact check
→ answer version + evidence links + verification
```

- 공동 planning은 경험 배분만 담당하며 실제 writer와 fact check는 문항별 step이다.
- 미승인 chunk는 후보 탐색·검증 보조로만 사용하고 사실 주장은 승인 근거나 사용자가 요청에서 명시한 경험으로 제한하는 안을 권장한다.
- 생성 중 fact check 결과와 별도 verify endpoint의 관계는 “생성 직후 검증 + 현재 version 재검증” 안이 권장되지만 명세 확정이 필요하다.
- 일부 문항 성공 결과의 저장 단위도 미결정이며 단계 재사용을 위해 문항별 멱등 반영이 권장된다.

### 11.3 사용자 version

- AI 생성은 `AI_GENERATED`, 사용자 명시 저장은 `USER_EDITED`다.
- restore는 기존 row를 바꾸지 않고 parent를 가리키는 새 version을 만든다.
- 현재 version 교체와 기존 `is_current=false`는 한 transaction이다.
- 문항 또는 current answer가 바뀌면 `FINALIZED → DRAFT`다.
- `ARCHIVED` command와 목록 filter는 MVP에 포함되지만 어느 상태에서 archive할 수 있는지와 다시 `DRAFT`로 복귀할 수 있는지는 미결정이다.
- browser draft는 서버 version을 만들지 않으며 사용자·resource별로 격리하고 logout/탈퇴 때 폐기한다.
- `RESTORED`와 `AI_REVISED`의 canonical 경로는 미결정이다.

### 11.4 검증·최종화

- 검증은 current answer version에 연결된 immutable 결과다.
- 사실, 수치, 역할, 회사·직무, 문항 요구, 글자 수, 반복을 검사한다.
- 검증은 자동 수정하지 않으며 제안 적용은 editor 변경 후 새 사용자 version 저장으로 해석한다.
- `FINALIZED`의 허용 조건, 특히 `WARNING` 허용 여부와 current version별 검증 freshness는 구현 전에 확정한다.
- 공고 `SUBMITTED` 변경은 별도 사용자 command다.

## 12. 면접 조사·예상 질문·모의 면접 흐름

### 12.1 면접 준비

선행 조건은 공고 분석 완료와 자기소개서 문항 1개 이상이다.

```text
precondition check
→ research run / question set placeholder
→ official company research
→ similar-role/interview research
→ source dedupe·classification
→ limited-source assessment
→ job + cover letter + verified evidence context
→ InterviewQuestionAgent
→ question set persistence
```

- 검색 query에 사용자 이름·연락처·문서 원문·개별 성과를 넣지 않는다.
- 공식, 기술 블로그, 뉴스, 후기, 커뮤니티를 구분하고 익명 후기를 사실로 단정하지 않는다.
- 출처 URL, 제목, 발행일, 조회일, snippet을 저장한다.
- 검색 부족은 제한된 결과로 성공할 수 있지만 이를 표현할 품질 상태는 미결정이다.
- API가 단일 `researchRunId`를 반환하는 반면 DB가 COMPANY/INTERVIEW를 구분하므로 실행 cardinality 결정이 필요하다.

### 12.2 예상 질문과 답변

- 질문에는 유형, 의도, 평가 포인트, 가이드, 관련 근거, 꼬리 질문, 출처 기반 여부가 있다.
- 질문 유형의 canonical enum은 기능/API/페이지 사이에서 미결정이다.
- 사용자 답변은 immutable version으로 저장하고 feedback run은 요청 당시 version을 입력으로 고정한다.
- 새 답변이 생성돼도 과거 feedback은 원래 version에 남는다.

### 12.3 모의 면접

```text
READY
→ start: first interviewer turn
→ IN_PROGRESS
   ├─ user message
   ├─ structured turn decision
   ├─ optional immediate feedback
   └─ follow-up or next question
→ complete request
→ COMPLETED + asynchronous session feedback
```

- `start`와 `messages`는 현재 API 계약상 동기 200이므로 제한된 1회 모델 호출, timeout, 질문·비용 상한을 적용한다.
- 사용자 message와 후속 interviewer message 중복을 막으려면 client request ID와 session version/CAS가 필요하다.
- 종합 feedback만 `202 + agentRunId` 비동기다.
- session이 COMPLETED여도 feedback run은 진행 중일 수 있으므로 별도 UI 상태가 필요하다.
- 주요 프로젝트 선택, mock turn과 Agent Run의 연결, 생성 UI 진입점은 미결정이다.

## 13. Agent Orchestrator·Model Router·Context Builder·Budget Guard

### 13.1 실행 순서

```text
authenticated command
→ ownership + idempotency
→ domain resource + agent_run(QUEUED)
→ commit
→ claim/reconciliation
→ WorkflowRegistry
→ ContextBuilder snapshot
→ for each fixed step
     ├─ cancellation/budget check
     ├─ input hash + reusable success lookup
     ├─ ModelRouter
     ├─ PromptRegistry
     ├─ AgentExecutor
     ├─ structured/domain validation
     ├─ usage + step checkpoint
     └─ idempotent domain command
→ terminal run state
```

### 13.2 Context Builder

Context snapshot은 최소 다음 provenance를 가진다.

- 공고 ID, content hash, 분석 version
- profile/evidence snapshot hash
- `VERIFIED` evidence ID·category·source
- masked chunk ID·document ID·page range
- research source ID·URL·type·retrievedAt
- prompt input token budget와 잘린 항목 목록
- 전체 context hash

모든 vector·DB 조회에 user scope를 넣고 외부 콘텐츠 안의 명령을 Tool 지시로 해석하지 않는다.

### 13.3 Model Router

- 공개 품질 선택과 내부 model tier를 별도 type으로 취급한다.
- task type, capability, 활성 policy와 비용을 기준으로 실제 provider/model을 선택한다.
- LOW_COST structured output 실패 시 BALANCED 1회 승격 규칙은 retry budget과 함께 확정해야 한다.
- HIGH_QUALITY는 사용자 설정과 요청별 재선택이 모두 있어야 한다.

### 13.4 Budget Guard

- 실행 전 예상 실행 비용과 실행당 상한을 확인한다.
- 각 호출 전 사용자 일일·시스템 일일·run 잔여 예산을 확인한다.
- 동시 run은 예약 없이 단순 합계 조회만 하면 한도를 초과할 수 있으므로 원자적 reserve/settle 계약이 필요하다.
- model, embedding, 검색 중 비용 집계 범위와 가격 version은 미결정이다.

### 13.5 Agent 목록과 workflow

명세에 있는 추출·분석·매칭·조사·writer·fact check·question·interviewer·feedback 역할을 고정 workflow step으로 배치한다. `QuestionAnalysisAgent`와 답변/세션 feedback workflow registry 이름은 기술 명세와 완전히 일치하지 않아 클래스명은 확정하지 않는다.

## 14. 인증·인가·사용자 격리·개인정보

### 14.1 인증

- Spring Security + Session JDBC
- BCrypt cost 12 이상, 비밀번호 최소 10자
- 운영 Cookie: HttpOnly, Secure, SameSite=Lax
- CSRF 활성화, frontend/API 동일 site reverse proxy
- 로그인 성공 시 session fixation 방지를 위한 session rotation
- 401/403도 공통 오류 factory를 사용

### 14.2 사용자 격리

1. 모든 aggregate root 조회는 `id + authenticatedUserId + deletedAt is null` 조건을 포함한다.
2. 자식 ID endpoint는 상위 aggregate까지 join해 owner를 검증한다.
3. 교차 resource command는 job, cover letter, evidence, question set의 owner가 모두 같은지 확인한다.
4. 타 사용자 resource는 404로 숨긴다.
5. vector query, Agent Run/SSE, presigned URL, idempotency record도 user scope를 강제한다.
6. frontend는 logout·탈퇴·사용자 교체 때 Query cache와 draft를 지운다.

DB의 공통 `user_id NOT NULL` 규칙과 자식 테이블 정의가 완전히 일치하지 않으므로 DB 강화 방식은 결정 게이트다.

### 14.3 개인정보

- 원본, 자기소개서, 면접 답변, 전체 prompt/response, 이메일·전화번호를 일반 로그·analytics·console에 남기지 않는다.
- LLM 전송 전 전화번호, 이메일, 상세 주소, 주민번호 패턴, 계정·비밀키 패턴을 마스킹한다.
- Object key와 로그에 사용자 파일명을 직접 넣지 않는다.
- 외부 검색 query에 개인 내용을 넣지 않는다.
- `agent_steps.output_json`은 최소 구조화 산출물과 ID 참조만 저장한다.
- presigned URL은 owner 확인 뒤 짧은 TTL로 발급하되 TTL 값은 미결정이다.

## 15. 비동기 작업·실패 복구·재시도·SSE

### 15.1 동기·비동기 기준

| 처리       | 기준                                                                                                    |
| ---------- | ------------------------------------------------------------------------------------------------------- |
| 동기       | 인증, CRUD, 상태 command, version 저장, bounded mock interview turn                                     |
| 비동기 202 | 문서 pipeline, 공고 URL 추출, 공고 분석, 자소서 생성·검증, 조사·질문, 답변 feedback, mock 종합 feedback |

### 15.2 내구성

- API transaction은 resource와 run을 함께 만들고 commit 후 dispatch한다.
- 주기적 reconciliation이 처리 가능한 `QUEUED` run을 다시 찾는다.
- `QUEUED → RUNNING`은 조건부 원자 update로 claim한다.
- 오래된 RUNNING은 heartbeat/lease 기준 `INTERRUPTED` 처리한다.
- 실행은 at-least-once일 수 있으므로 step input hash와 도메인 command idempotency가 필수다.
- 완료된 step은 동일 user·workflow/prompt/schema·input snapshot hash일 때만 재사용한다.
- cancel은 cooperative하며 외부 호출 전후와 결과 반영 전에 확인한다.

lease/heartbeat/cancel request 필드는 현재 DB 명세에 없으므로 공개·DB 계약 확정 전 migration을 만들지 않는다.

### 15.3 재시도

- 자동 재시도는 timeout, 429/5xx, 구조화 출력 재시도 가능 오류처럼 분류된 오류만 대상으로 한다.
- 비복구 validation·ownership·비용 오류는 자동 재시도하지 않는다.
- “자동 재시도 2회”의 총 attempt 수와 model 승격 차감 방식은 미결정이다.
- 사용자 retry가 같은 run을 재개하는지 새 run을 만드는지도 미결정이다.
- 재시도 전에 기존 성공 step의 input hash를 다시 검증한다.

### 15.4 SSE

- 연결 시 user ownership을 검증하고 현재 DB snapshot을 먼저 전송한다.
- 진행 event는 transaction commit 후 발행한다.
- heartbeat와 terminal 알림이 필요하다.
- event replay 계약이 없으므로 frontend는 재연결 때 `GET /agent-runs/{id}`를 다시 읽는다.
- SSE는 best-effort이며 지속 실패 시 제한적 polling으로 전환한다.
- terminal snapshot을 확인하면 연결을 닫는다.

event type, payload, ID, `Last-Event-ID` 지원 여부는 API 계약 결정이 필요하다.

## 16. 목표 패키지·디렉터리 구조

### 16.1 Backend

```text
backend/src/main/java/com/hiresemble/
├─ common/
├─ auth/
├─ profile/
├─ document/
├─ job/
├─ coverletter/
├─ research/
├─ interview/
├─ agentrun/
└─ ai/
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

각 기능은 필요해질 때 `api/application/domain/infrastructure`를 추가한다. 빈 계층을 한 번에 생성하지 않는다.

### 16.2 Frontend

```text
frontend/src/
├─ app/
├─ router/
├─ layouts/
├─ pages/
├─ shared/
│  ├─ api/
│  ├─ components/
│  ├─ schemas/
│  └─ utils/
├─ stores/
└─ features/
   ├─ auth/
   ├─ profile/
   ├─ documents/
   ├─ evidence/
   ├─ jobs/
   ├─ cover-letters/
   ├─ interviews/
   ├─ agent-runs/
   └─ settings/
```

상세 파일 소유권과 생성 시점은 [구현 계획](implementation-plan.md)을 따른다.

## 17. 테스트·검증 전략

| 계층       | 핵심 검증                                                                                |
| ---------- | ---------------------------------------------------------------------------------------- |
| Domain     | 모든 상태 전이, owner, finalization, current version, scheduler race                     |
| Repository | PostgreSQL FK/unique/check, tenant join, pgvector user/model/dimension, partial unique   |
| Migration  | 빈 DB 적용과 기존 DB upgrade                                                             |
| API        | Session/CSRF, 직접 성공 DTO, 공통 오류, 404 격리, 409/413/415/429/503                    |
| File       | MIME 위장, macro, 손상·텍스트 부족 PDF/DOCX/TXT, storage 보상                            |
| External   | LLM/Search/Object/URL 429·5xx·timeout·부족 결과를 Fake/WireMock으로 검증                 |
| Workflow   | 단계 순서, tool allowlist, 재시도, 비용, 재시작, 취소, partial success, idempotent apply |
| Security   | 두 사용자 fixture, SSRF, prompt injection 경계, 로그 원문 부재                           |
| Frontend   | form, route guard, typed error, 상태 표시, editor version, cache/draft 격리, SSE 복구    |
| E2E        | 명세 시나리오 A–C와 AC-01–AC-13, 실패 수동 보완, 다른 사용자 404                         |

CI 기본 검증은 실제 유료 AI·검색 API를 호출하지 않는다.

## 18. 구현 순서와 선행 의존성

```text
계약 결정
→ 공통 HTTP·Security·Session·test 기반
→ 사용자·프로필
→ Agent Run·Model/Context/Budget 기반
→ 문서·근거
→ 공고 등록·상태·분석
→ 자기소개서
→ 조사·예상 질문·답변 피드백
→ 모의 면접
→ dashboard/settings/보안·복구·전체 E2E
```

- 승인 근거가 공고 분석보다 먼저다.
- 공고 분석이 자기소개서·면접 준비보다 먼저다.
- 자기소개서 domain/version이 generation workflow보다 먼저다.
- Agent Run·SSE 공통 기반은 최초 장기 작업인 문서 pipeline보다 먼저다.
- API/DTO/DB 계약을 먼저 확정한 뒤 frontend와 AI consumer가 구현한다.

단계별 완료 조건은 [구현 계획](implementation-plan.md)에 정의한다.

## 19. 개발 에이전트 작업 분배 원칙

- `backend`: 도메인, API/OpenAPI, Security, DB/Flyway, storage/parser, URL fetch, Scheduler, TaskExecutor, Agent Run persistence/API/SSE adapter
- `ai_workflow`: `com.hiresemble.ai/**`, prompt resource, workflow, context, model routing, budget, Spring AI/search adapter, Fake workflow test
- `frontend`: `frontend/**`의 route/page/feature/API client/query/store/SSE UI/test
- `validator`: 수정 없이 AC, diff, 상태, DB/API/page, tenant, test 결과 검증
- 루트 관리자: 명세 결정, shared DTO/port 소유 순서, 설계·추적 문서 통합

`build.gradle.kts`, `application.yml`, Flyway, shared DTO/port는 동시 편집하지 않는다. 상세 파일 소유 표는 [구현 계획](implementation-plan.md)을 따른다.

## 20. 교차 검증 결과

| 검증 항목          | 결과                                                                                                  |
| ------------------ | ----------------------------------------------------------------------------------------------------- |
| 모든 MVP 기능 포함 | AC-01~13을 설계 flow와 추적 표에 모두 연결                                                            |
| 기능↔DB            | 핵심 aggregate는 대응하나 idempotency·삭제 작업·async lease·일부 provenance가 누락                    |
| DB↔API             | 주요 endpoint는 대응하나 DTO, enum, version, idempotency, Agent Run 상세 계약이 불완전                |
| API↔페이지         | 핵심 route는 대응하나 dashboard 집계, 일부 filter, mock 생성, feedback pending, route redirect가 누락 |
| 상태 일치          | 공고 업무 상태는 일치; 추출 상태 표현, version source, step terminal, WAITING_USER가 불완전           |
| 동기↔비동기        | 장기 작업 202 기준은 대체로 일치; mock turn과 Agent Run 관계가 미결정                                 |
| 사용자 격리        | 모든 계층에 적용하도록 설계했으나 DB 자식 FK 강제 방식은 미결정                                       |
| Backend↔AI 책임    | 도메인·HTTP·persistence와 context/model/workflow를 port로 분리                                        |
| 구현 선후 관계     | 계약→기반→근거→공고→자소서→면접 순으로 정의                                                           |

아래 문제를 해결하기 전에는 관련 공개 DTO, enum, migration 또는 상태 전이를 임의로 구현하지 않는다.

## 21. 명세 불일치·누락·결정 필요 사항

### D-01. 공고의 업무 상태와 추출 상태

문제: 기능 명세는 URL 추출 실패 때 공고 “상태”를 `NEEDS_MANUAL_INPUT`으로 표시하지만 공고 업무 상태는 `IN_PROGRESS|SUBMITTED|CLOSED`뿐이다.

관련 명세: [기능 JOB-001/JOB-002](../spec/functional.md), [DB 5.2](../spec/db.md), [API 공고](../spec/api.md), [페이지 7](../spec/page.md)

영향: 하나의 enum으로 구현하면 목록 filter, Scheduler와 상태 전이가 깨진다.

권장 해결안: `status`와 `extractionStatus`를 별도 축으로 확정한다.

설계에서 적용한 처리: 업무 `status=IN_PROGRESS`를 유지하고 추출 `NEEDS_MANUAL_INPUT`을 별도로 쓰는 구조를 적용하되 기능 문구 정정 전 계약 이슈로 남긴다.

### D-02. 품질·유형·version enum

문제: `ECONOMY/LOW_COST/BASIC/ADVANCED`가 서로 다른 품질 개념에 혼용되고, 자기소개서 `RESTORED`는 기능 명세에 없으며 `AI_REVISED` 생성 API도 없다. 면접 질문 유형도 문서별 집합이 다르다.

관련 명세: [기능 CL-004/INT-003/SYS-003](../spec/functional.md), [DB 2/7/8/9](../spec/db.md), [API 자기소개서·면접·설정](../spec/api.md), [기술 9](../spec/tech_stack.md)

영향: 공개 DTO, DB CHECK, router/form schema와 ModelRouter key가 달라진다.

권장 해결안: `AiQualityMode`, 내부 `ModelTier`, `ResearchQuality`를 분리하고 version source·question type의 canonical enum과 생성 경로를 확정한다.

설계에서 적용한 처리: 내부 type을 분리해 설계했지만 공개 enum과 migration은 보류한다.

### D-03. API DTO·version·page projection 누락

문제: 여러 CRUD의 request/response/nullability/validation/version이 생략됐고 dashboard 카드, 문서 상세·download 만료, Agent Run 필수 표시, 일부 목록 filter를 현재 API로 안정적으로 만들 수 없다.

관련 명세: [API 전반](../spec/api.md), [DB 전반](../spec/db.md), [페이지 4~13](../spec/page.md)

영향: OpenAPI, Bean Validation, TypeScript/Zod, 409 UX와 화면 데이터가 추측에 의존한다.

권장 해결안: 구현 0단계에서 endpoint별 완전한 DTO와 enum, pagination 최대값, `version`, summary/filter를 확정하고 `GET /dashboard` 또는 동등 projection을 결정한다.

설계에서 적용한 처리: endpoint 연결만 확정하고 명세에 없는 필드·aggregate API는 구현 보류한다.

### D-04. 사용자 소유 DB 무결성

문제: DB 공통 규칙은 사용자 소유 테이블의 `user_id NOT NULL`을 요구하지만 일부 정의는 NOT NULL이 빠지고 aggregate child에는 `user_id`가 없다. 교차 FK의 owner 일치 제약도 명시되지 않았다.

관련 명세: [DB 1과 각 테이블](../spec/db.md), [기술 6~7](../spec/tech_stack.md), [API 소유권 404](../spec/api.md)

영향: ID 단독 조회나 cross-user FK 연결이 사용자 격리를 깨뜨릴 수 있다.

권장 해결안: aggregate child 예외를 명시하고 owner join을 repository 규칙으로 고정하며 중요 교차 참조는 `user_id`+복합 FK/unique로 강화한다.

설계에서 적용한 처리: application/API owner join은 필수로 적용하고 DB 강화 방식은 migration 전 결정 대상으로 둔다.

### D-05. 삭제·provenance·outbox

문제: 사용자 콘텐츠 기본 soft delete, 자식 cascade, 문서 파생 근거 “삭제 또는 비활성화”, 보존된 답변의 `SOURCE_DELETED` 요구가 충돌한다. Object 삭제 실패용 outbox table도 없다.

관련 명세: [기능 PROF-007/DOC-004/CL-004](../spec/functional.md), [DB 1/4/6/12/13](../spec/db.md), [API delete](../spec/api.md)

영향: 과거 version의 근거 링크·검증·감사 이력이 유실되거나 Object와 DB가 불일치할 수 있다.

권장 해결안: 문서 metadata soft delete, Object/text/chunk 물리 삭제, 참조 근거 `SOURCE_DELETED` 보존, version이 있는 질문 soft delete, retry outbox를 명시한다.

설계에서 적용한 처리: provenance 보존을 우선하는 안만 적용하며 cascade·outbox schema는 승인 전 보류한다.

### D-06. 파일 key와 표시명

문제: 기술 명세 Object key 예시는 sanitized filename을 포함하지만 같은 명세와 백엔드 규칙은 사용자 파일명을 key에 직접 쓰지 말라고 한다. 업로드 `displayName`의 DB column도 없다.

관련 명세: [기술 6.3](../spec/tech_stack.md), [API POST documents](../spec/api.md), [DB 4.1](../spec/db.md)

영향: 개인정보·경로 노출과 API 입력 손실이 발생한다.

권장 해결안: `users/{userId}/documents/{documentId}/original` 같은 불투명 key와 별도 표시명 column을 사용한다.

설계에서 적용한 처리: 불투명 key를 설계 원칙으로 사용하고 표시명 schema는 보류한다.

### D-07. 멱등성 영속 계약

문제: API는 여러 POST의 `Idempotency-Key`를 요구하지만 DB table, scope, request hash, TTL, 응답 재생 규칙이 없다.

관련 명세: [API 11](../spec/api.md), [DB 전체](../spec/db.md), [기술 8~10](../spec/tech_stack.md)

영향: 재전송이 중복 문서·공고·run과 유료 호출을 만든다.

권장 해결안: user+method+route scope+key+request hash+resource/response reference+expiry를 가진 영속 record를 추가한다.

설계에서 적용한 처리: 멱등 경계를 architecture에 포함했지만 DB·API 계약 전 완료로 간주하지 않는다.

### D-08. Agent Run 내구성·상태·재시도·SSE

문제: `TaskExecutor` 접수 뒤 QUEUED 유실, claim/lease/heartbeat/cancel race, step의 CANCELLED/INTERRUPTED, WAITING_USER 재개, retry 횟수·run identity, SSE event/replay 계약이 없다.

관련 명세: [기능 SYS-001/002](../spec/functional.md), [DB 2/9](../spec/db.md), [API 1.5/9](../spec/api.md), [기술 8/10](../spec/tech_stack.md)

영향: 재시작·취소·재연결 때 유실, 중복 실행, 영구 RUNNING/WAITING과 비용 차이가 생긴다.

권장 해결안: DB-backed claim/reconciliation, heartbeat/lease, cooperative cancel, 전체 state transition, maxAttempts, retry run 정책, snapshot/heartbeat/terminal SSE 계약을 확정한다.

설계에서 적용한 처리: DB/REST를 상태 원천으로 하고 at-least-once+멱등 반영, SSE snapshot 보정을 적용하지만 schema와 공개 event는 보류한다.

### D-09. Agent Run 표시와 예산 계산

문제: 기능·페이지가 모델 등급, 예상/실제 비용, 소요 시간, retry 가능 여부를 요구하지만 run DB/API에 일부가 없고 동시 예산 예약, 가격 version, embedding/search 비용 범위도 없다.

관련 명세: [기능 SYS-001/003](../spec/functional.md), [DB 9](../spec/db.md), [API Agent Run/settings](../spec/api.md), [페이지 12/13](../spec/page.md)

영향: BudgetGuard가 한도를 정확히 막지 못하고 UI가 필수 정보를 표시할 수 없다.

권장 해결안: run detail projection과 `retryable/requiredUserAction`, 원자 reserve/settle, 가격 version과 비용 포함 범위를 정의한다.

설계에서 적용한 처리: BudgetGuard 책임은 포함했으나 공개 필드와 저장 schema는 보류한다.

### D-10. 자기소개서 cardinality·content·최종화

문제: 공고당 자기소개서 하나인지 active 하나인지 불명확하고, 생성 FactCheck와 별도 verify 관계, partial success, TipTap 저장 형식·글자 계산, `WARNING` 최종화 허용, 일반 PUT status와 command endpoint 관계가 없다.

관련 명세: [기능 CL-001~006](../spec/functional.md), [DB 6](../spec/db.md), [API 6](../spec/api.md), [페이지 8](../spec/page.md)

영향: unique 제약, XSS/글자 수, current version 검증 freshness와 상태 우회가 달라진다.

권장 해결안: active cardinality, plain-text/structured editor 저장·sanitization, 문항별 atomicity, 생성 검증, finalization eligibility와 전용 command를 확정한다.

설계에서 적용한 처리: current version transaction과 편집 시 DRAFT만 확정하고 나머지 공개 계약은 보류한다.

### D-11. 면접 조사·질문·provenance

문제: preparation API는 단일 research ID를 반환하지만 DB는 COMPANY/INTERVIEW를 구분하고, 검색 부족 품질 상태와 question↔research source 연결이 없다. 회사 조사 단독 생성 경로도 없다.

관련 명세: [기능 INT-001~004](../spec/functional.md), [DB 7~8](../spec/db.md), [API 7](../spec/api.md), [기술 8](../spec/tech_stack.md)

영향: run cardinality, UI 부족 상태, 자기소개서 회사 Context와 질문 provenance를 재현할 수 없다.

권장 해결안: research run 단위, `sourceCoverage`, typed provenance/link와 회사 조사 생성 시점을 확정한다.

설계에서 적용한 처리: 논리 workflow와 출처 metadata만 설계하고 row cardinality·link schema는 보류한다.

### D-12. 모의 면접 멱등성·Agent Run·feedback 상태

문제: page의 client request ID가 API/DB에 없고, 동기 turn과 `MockInterviewWorkflow`/Agent Run 관계, 주요 프로젝트 입력, session 생성 UI, COMPLETED 뒤 feedback 진행 상태가 없다.

관련 명세: [기능 INT-005/006](../spec/functional.md), [DB 8](../spec/db.md), [API 8](../spec/api.md), [페이지 7/9/10/11](../spec/page.md), [기술 8/10](../spec/tech_stack.md)

영향: timeout·다중 탭에서 중복 메시지/비용이 생기고 화면 흐름과 비용 기록이 일관되지 않는다.

권장 해결안: client request ID unique, session CAS, 동기 executor와 비동기 feedback 경계, preferred project IDs, 생성 form, feedback status/run link를 확정한다.

설계에서 적용한 처리: turn은 bounded 동기, 종합 feedback은 비동기로 분리했지만 공개 필드·run 연결은 보류한다.

### D-13. 인증·CSRF·프로필 완료·탈퇴·browser draft

문제: 가입 후 session 응답, login 전 CSRF bootstrap, `profile_completed` 계산, 탈퇴 request/202 삭제 추적과 user 상태 전이, browser draft 저장소·TTL·폐기 정책이 불완전하다.

관련 명세: [기능 AUTH/PROF](../spec/functional.md), [DB users/profile/13](../spec/db.md), [API auth/account](../spec/api.md), [페이지 3/13/14](../spec/page.md)

영향: onboarding 접근, Security 설정, route guard, 재가입·삭제 복구, 동일 browser 사용자 간 원문 노출이 달라진다.

권장 해결안: public CSRF bootstrap과 signup/login session rotation, server profile completion policy, 즉시 WITHDRAWN+session 폐기+삭제 job, 사용자별 sessionStorage draft와 폐기를 확정한다.

설계에서 적용한 처리: signup이 인증 session을 만든다는 기능 계약과 cache/draft 폐기 원칙만 적용하며 상세 DTO·삭제 흐름은 보류한다.

### D-14. 공고 canonical URL·추출 실패·timestamp·OUTDATED·score

문제: nullable canonical URL이 중복 제약을 우회하고, `FAILED`와 `NEEDS_MANUAL_INPUT` 및 수동 입력 완료 상태, reopen timestamp, PATCH version, OUTDATED 계산/API, fit score rubric이 없다.

관련 명세: [기능 JOB-001~005](../spec/functional.md), [DB 5](../spec/db.md), [API 5](../spec/api.md), [페이지 7](../spec/page.md)

영향: 중복 비용, 잘못된 CTA, Scheduler race, stale 결과 표시와 점수 해석이 달라진다.

권장 해결안: 접수 전 canonicalization, 추출 상태 의미, 상태별 timestamp mutation, expected version, stale hash inputs, score 범위·rubric을 확정한다.

설계에서 적용한 처리: 지원 상태/추출 상태 분리, immutable analysis version, 상태+history transaction만 확정하고 나머지는 보류한다.

### D-15. 문서 pipeline의 부분 성공과 수동 재개

문제: `PARSED`가 parser 성공인지 근거 추출까지 성공인지, 후속 AI 실패 때 document 상태, `WAITING_USER`와 manual-text의 기존/new run 관계, 텍스트 부족 기준이 없다.

관련 명세: [기능 DOC-001~003/SYS](../spec/functional.md), [DB 4/9](../spec/db.md), [API documents](../spec/api.md), [기술 4.3/10](../spec/tech_stack.md)

영향: 재처리 범위, UI 완료 표시, run 중복과 evidence 누락이 달라진다.

권장 해결안: parse 상태와 evidence extraction 결과를 분리하거나 `PARSED` 완료 범위를 명시하고 manual resume/new run, 최소 text 기준을 확정한다.

설계에서 적용한 처리: text/chunk 부분 성공을 보존하고 실패 step 재시작을 권장하지만 공개 상태 매핑은 보류한다.

### D-16. AI workflow 목록·Context·embedding·gateway 경계

문제: `QuestionAnalysisAgent`와 일부 feedback workflow가 기술 registry에 없고, 승인 근거 우선과 미승인 chunk 사용 범위, vector dimension/HNSW, 공고 URL 추출과 Tavily extract 경계가 불명확하다.

관련 명세: [기능 CL/INT](../spec/functional.md), [DB chunks/provenance](../spec/db.md), [기술 4/6/8/9](../spec/tech_stack.md)

영향: class·prompt 소유권, 미승인 사실 사용, migration·재색인, 비용·SSRF 경계가 구현마다 달라진다.

권장 해결안: logical step과 Agent class를 구분하고, 미승인 chunk는 탐색/검증 전용, embedding model/dimension 고정 후 index, 공고 URL gateway와 검색 gateway 분리를 명시한다.

설계에서 적용한 처리: 안전한 Context 제한과 gateway 분리는 적용하되 class 이름과 vector migration은 보류한다.

### D-17. route·필터·화면 진입점

문제: `/` redirect, job 기본 child, catch-all 404, mock session 생성 위치가 없고 dashboard·interviews·Agent Run 화면이 요구하는 일부 filter가 API에 없다.

관련 명세: [페이지 1/4/7/10/11/15](../spec/page.md), [API 목록 endpoint](../spec/api.md)

영향: deep link, return URL, E2E, 목록 카드와 생성 사용자 흐름이 끊긴다.

권장 해결안: canonical redirect/404, mock 생성 form 위치와 필요한 aggregate/filter API를 page/API 명세에 함께 확정한다.

설계에서 적용한 처리: route tree와 연결 의도만 유지하고 구체 redirect·추가 endpoint는 보류한다.

### D-18. 자기소개서 ARCHIVED 전이

문제: 자기소개서 상태에 `ARCHIVED`가 있고 전용 archive API와 목록 filter가 있지만 진입 가능한 상태, `finalized_at` 처리와 복귀 가능 여부가 정의되지 않았다.

관련 명세: [기능 CL-001](../spec/functional.md), [DB 2/6.1](../spec/db.md), [API 자기소개서 목록·archive](../spec/api.md), [페이지 11.1](../spec/page.md)

영향: Domain transition, DB timestamp, API 오류와 목록 action이 계층별로 달라질 수 있다.

권장 해결안: `DRAFT|FINALIZED → ARCHIVED` 허용 여부, archive 뒤 read-only 범위, 복귀 command와 `finalized_at` 보존 정책을 명시한다.

설계에서 적용한 처리: 목록·archive command를 MVP 범위에 포함하되 허용 전이와 복귀 동작은 계약 승인 전 구현 보류한다.

## 22. 구현 전 결정 게이트

### Gate A: 공개 계약

- 품질·질문·version source enum
- endpoint별 완전한 DTO, validation, version, filter
- CSRF/signup/login과 탈퇴
- cover letter finalization·editor content
- cover letter `ARCHIVED` 진입·복귀
- mock message idempotency와 Agent Run detail/SSE

### Gate B: 데이터·수명주기

- tenant composite integrity
- idempotency, outbox, async lease/heartbeat/cancel schema
- 삭제·SOURCE_DELETED·질문 version 보존
- research/provenance cardinality
- embedding model·dimension

### Gate C: AI·운영 정책

- retry/WAITING_USER/run identity
- model tier mapping, 가격 version과 예산 reserve
- Context에서 미승인 chunk 사용 범위
- score rubric, source coverage
- 동기 mock turn timeout·비용·run 연결

세 gate의 해당 항목이 승인되기 전에는 관련 migration이나 외부 공개 계약을 생성하지 않는다.
