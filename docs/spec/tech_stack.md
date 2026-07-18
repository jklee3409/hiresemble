# 기술 스택 명세서

- 문서 버전: 1.1 (P0 승인 기준선)
- 기준일: 2026-07-18
- 대상 범위: 핵심 MVP
- 아키텍처 원칙: Spring Boot 모듈러 모놀리스 + Spring AI 기반 통제형 멀티 에이전트 워크플로
- 공통 API Prefix: `/api/v1`
- 공통 식별자: UUID
- 공통 시간 형식: UTC `timestamptz`, API는 ISO-8601

---

## 1. 목표와 설계 원칙

본 서비스는 사용자가 회원 가입 후 학력·자격증·어학·수상·경력과 이력서·포트폴리오 파일을 등록하고, 채용 공고 분석, 자기소개서 작성·검증·버전 관리, 면접 정보 조사와 대화형 모의 면접까지 수행하는 개인 맞춤형 취업 준비 서비스이다.

### 핵심 설계 원칙

1. **비즈니스 데이터의 원천은 Spring Boot와 PostgreSQL에 둔다.**
2. **Spring AI는 모델 호출, 구조화 출력, Tool Calling, RAG 통합 계층으로 사용한다.**
3. **에이전트 실행 순서는 자유 루프가 아니라 코드로 정의된 워크플로로 통제한다.**
4. **각 에이전트의 입력과 출력은 Java Record/DTO 기반 구조화 데이터로 전달한다.**
5. **모든 장기 작업은 `agent_runs`, `agent_steps`에 상태와 산출물을 기록한다.**
6. **모델명은 코드에 하드코딩하지 않고 작업 유형과 품질 모드별 정책으로 라우팅한다.**
7. **원본 파일과 개인정보는 필요한 최소 범위만 LLM에 전달한다.**
8. **MVP는 단일 인스턴스 운영을 기준으로 하되, 다중 사용자 데이터 격리는 처음부터 적용한다.**
9. **장기 실행의 상태 원천은 DB이고 SSE는 snapshot-first best-effort 전달 수단이다.**
10. **chat·embedding·search 비용은 immutable 가격 version과 원자 reserve/settle로 통제한다.**

---

## 2. 전체 아키텍처

```text
[Vue 3 SPA]
    │ HTTPS / Session Cookie / CSRF
    ▼
[Spring Boot 4.1 Modular Monolith]
    ├─ Auth / User / Profile
    ├─ Document / Evidence
    ├─ Job Posting / Job Analysis
    ├─ Cover Letter
    ├─ Interview Preparation
    ├─ Workflow Orchestrator
    ├─ Model Router / Budget Guard
    └─ Research & URL Extraction Gateway
          │
          ├─ Spring AI 2.0 → LLM / Embedding Provider
          ├─ Tavily Search API → 회사·면접 정보 검색
          └─ Jsoup → 사용자 등록 공고 URL 본문 추출
    │
    ├─ PostgreSQL 18 + pgvector
    └─ S3-compatible Object Storage
```

### 배포 단위

| 구성요소        | MVP 배포 단위                             |
| --------------- | ----------------------------------------- |
| Frontend        | 정적 호스팅 또는 Nginx                    |
| Backend         | Spring Boot 단일 애플리케이션             |
| Database        | PostgreSQL 단일 인스턴스                  |
| Object Storage  | 개발: MinIO / 운영: S3 또는 Cloudflare R2 |
| AI Provider     | OpenAI 우선, Gateway 추상화               |
| Search Provider | Tavily Search API 우선, Gateway 추상화    |

마이크로서비스, Kafka, Redis, 별도 Python Agent Server는 MVP에서 사용하지 않는다.

---

## 3. 버전 기준

| 영역             | 선택                     |
| ---------------- | ------------------------ |
| Language         | Java 21 LTS              |
| Backend          | Spring Boot 4.1.x        |
| AI Framework     | Spring AI 2.0.x          |
| Frontend         | Vue 3.x + TypeScript 5.x |
| Build            | Gradle Kotlin DSL        |
| Database         | PostgreSQL 18.x          |
| Vector Extension | pgvector 0.8.x           |
| Node             | 활성 LTS 버전            |
| Package Manager  | pnpm                     |
| Container        | Docker Compose           |

Spring AI 2.0.x와 Spring Boot 4.0/4.1 호환 범위를 기준으로 선택한다. 패치 버전은 프로젝트 생성 시점의 최신 안정 버전으로 고정하고 Renovate 또는 Dependabot으로 업데이트한다.

---

## 4. Backend 기술 스택

### 4.1 Core

| 기술                   | 용도                                         |
| ---------------------- | -------------------------------------------- |
| Spring Web MVC         | REST API                                     |
| Spring Security        | 인증·인가·CSRF                               |
| Spring Session JDBC    | DB 기반 로그인 세션                          |
| Spring Data JPA        | 도메인 CRUD                                  |
| JdbcClient             | pgvector 및 복잡 쿼리                        |
| Bean Validation        | 입력 검증                                    |
| Flyway                 | DB 마이그레이션                              |
| Spring Scheduling      | 공고 마감 자동 처리                          |
| Spring TaskExecutor    | DB claim으로 얻은 문서 분석·AI 워크플로 실행 |
| Spring Actuator        | Health / Metrics                             |
| Micrometer Observation | 지연시간·모델 사용량 관찰                    |
| springdoc-openapi      | OpenAPI 문서                                 |
| Jackson                | JSON / JSONB 직렬화                          |

### 4.2 AI

| 기술                               | 용도                                                       |
| ---------------------------------- | ---------------------------------------------------------- |
| Spring AI ChatClient               | `ChatGateway` provider adapter 내부의 LLM 호출             |
| Spring AI Structured Output        | Java DTO 기반 결과 생성                                    |
| Spring AI Tool Calling             | 검색·근거 조회 등 제한된 도구 호출                         |
| JdbcClient 중심 Vector Search port | user/model/dimension/generation 조건의 pgvector exact 검색 |
| ModelRouter                        | 작업별 모델 선택                                           |
| BudgetGuard                        | 호출 수·토큰·비용 상한                                     |
| PromptRegistry                     | 버전이 있는 프롬프트 관리                                  |
| AiUsageRecorder                    | chat·embedding·search usage와 immutable 가격 version 기록  |

Domain/Application은 Spring AI concrete API를 참조하지 않는다. `ChatGateway`, `EmbeddingGateway`, `WebSearchGateway` port를 AI provider adapter가 구현한다. Structured Output은 `JSON Schema → Java record → workflow validator → domain command validator`를 모두 통과한 결과만 반영한다.

### 4.3 문서 처리

| 기술          | 용도                            |
| ------------- | ------------------------------- |
| Apache Tika   | MIME 탐지 및 공통 텍스트 추출   |
| Apache PDFBox | PDF 페이지별 텍스트 추출        |
| Apache POI    | DOCX 텍스트 추출                |
| Jsoup         | 채용 공고 URL HTML 추출 및 정제 |
| SHA-256       | 중복 파일 및 중복 공고 감지     |

MVP 지원 파일은 `PDF`, `DOCX`, `TXT`이며, 이미지 기반 PDF OCR, HWP, PPTX는 제외한다. 텍스트 추출량이 기준 이하이면 `NEEDS_MANUAL_TEXT` 상태로 전환해 사용자가 텍스트를 직접 보완한다.

### 4.4 외부 검색

회사 정보와 유사 직무 면접 정보는 `WebSearchGateway`를 통해 검색한다.

```java
public interface WebSearchGateway {
    SearchResult search(SearchRequest request);
}
```

사용자가 등록한 공고 URL은 별도 `JobPageFetchGateway`가 가져온다. 이 gateway는 HTTP(S)만 허용하고 DNS·redirect마다 private/link-local/loopback 차단, credential URL 거부, byte·redirect·timeout 상한을 적용한다. Tavily extract를 사용자 URL fetch에 사용하지 않는다.

MVP 구현체는 Tavily REST API를 사용한다.

- 기본 검색: `basic`
- 사용자가 고품질 재조사를 요청한 경우에만 `advanced`
- 검색 결과에는 URL, 제목, 발행일, 조회일, 스니펫을 저장
- 커뮤니티 정보와 공식 출처를 구분
- 원문 전체를 영구 보관하지 않고 필요한 인용 스니펫과 메타데이터만 저장

---

## 5. Frontend 기술 스택

| 기술                     | 용도                        |
| ------------------------ | --------------------------- |
| Vue 3 Composition API    | UI                          |
| TypeScript               | 타입 안정성                 |
| Vite                     | 개발·빌드                   |
| Vue Router               | 라우팅                      |
| Pinia                    | 인증 사용자·전역 UI 상태    |
| TanStack Vue Query       | 서버 상태·캐시·재조회       |
| Axios                    | REST 요청 및 CSRF 처리      |
| Tailwind CSS             | 스타일                      |
| PrimeVue 또는 shadcn-vue | 접근 가능한 UI 컴포넌트     |
| TipTap                   | 자기소개서·면접 답변 편집기 |
| Zod                      | 폼 스키마 검증              |
| Vitest + Vue Test Utils  | 단위·컴포넌트 테스트        |
| Playwright               | 핵심 E2E                    |

서버 데이터는 Vue Query가 관리하고, Pinia에는 로그인 사용자, UI 설정, 임시 작성 상태처럼 전역 공유가 필요한 최소 상태만 저장한다.

---

## 6. Database와 Storage

### 6.1 PostgreSQL

사용 대상:

- 사용자·프로필·공고·지원서·면접 데이터
- Spring Session
- 에이전트 실행 상태와 체크포인트
- JSONB 분석 산출물
- pgvector 임베딩

### 6.2 pgvector

- 문서 청크와 사용자 근거 검색에 사용
- 모든 검색 쿼리에 `user_id` 조건 필수
- MVP active policy: provider `OpenAI`, model `text-embedding-3-small`, dimension `1536`, cosine distance
- provider·model·dimension·embedding generation을 하나의 immutable policy version으로 관리
- `vector(1536)` typed column을 사용하고 boot 시 configured model dimension과 DB typmod가 다르면 fail fast
- active document와 승인된 provider/model/dimension/generation을 모든 검색 조건에 포함
- 초기 검색은 exact cosine이며 HNSW index를 만들지 않음
- live chunk 50,000개 이상 또는 대표 query p95가 200ms를 넘을 때만 별도 forward migration으로 HNSW 검토
- model·dimension을 같은 column/index에 혼합하지 않음
- 변경 시 새 generation과 typed vector column/index 생성→backfill→정합성 검증→active generation 전환→후속 cleanup

### 6.3 Object Storage

원본 파일은 DB BLOB가 아니라 S3 호환 스토리지에 저장한다.

```text
users/{userId}/documents/{documentId}/content
```

- 개발: MinIO
- 운영: AWS S3 또는 Cloudflare R2
- 다운로드는 인증 후 단기 Presigned URL 발급
- 삭제 접수 즉시 DB metadata를 API에서 숨긴다. text/chunk/embedding은 DB deletion transaction에서 제거하고 Object 삭제만 Outbox로 재시도한다.
- `original_filename`과 사용자 `display_name`은 storage locator와 분리한다. 표시명은 1..255자이며 제어문자·경로 구분자를 거부하고 업로드 파일명은 저장 키 생성에 사용하지 않는다.

---

## 7. 인증과 보안

### 인증 방식

- 이메일 + 비밀번호
- Spring Security 세션 인증
- Spring Session JDBC
- 비밀번호: BCrypt cost 12 이상
- 운영 쿠키: `HttpOnly`, `Secure`, `SameSite=Lax`
- CSRF 활성화
- Production은 Frontend와 API를 동일 Site로 Reverse Proxy

### 인가 규칙

- 모든 도메인 조회는 `resource.user_id == authenticatedUserId`
- UUID만으로 리소스 접근 허용 금지
- Object 다운로드 전 DB 소유권 검증
- 에이전트 실행 SSE 연결도 소유권 검증

### 파일 보안

- 확장자와 MIME 모두 검증
- 허용 형식: PDF, DOCX, TXT
- 기본 최대 크기: 파일당 20MB
- 사용자당 총 저장량 제한 설정 가능
- 매크로 포함 Office 파일 거부
- HTML/SVG 업로드 금지
- 파일 내용과 프롬프트를 일반 로그에 기록하지 않음

### LLM 개인정보 정책

LLM 전송 전 기본 마스킹 대상:

- 전화번호
- 이메일
- 상세 주소
- 주민등록번호 패턴
- 계정·비밀키 패턴

사용자가 자기소개서에 이름 등 식별 정보가 필요하다고 명시한 경우에만 최종 렌더링 단계에서 서버가 치환한다.

- 외부 page·검색 text는 instruction이 아닌 untrusted data로 delimiter 처리한다.
- 외부 콘텐츠의 tool 지시·prompt injection을 실행하지 않고 step별 Tool allowlist와 호출 상한을 적용한다.
- 미승인 masked chunk는 evidence 후보 탐색·semantic 탐색·FactCheck 모순 확인에만 사용한다.
- 미승인 chunk만으로 긍정 사실을 작성하거나 score·interview 질문의 근거로 쓰지 않으며 PASSED 대신 `WARNING + UNVERIFIED_CLAIM`으로 처리한다.
- Object key, 일반 log, analytics와 browser console에 사용자 filename·원문·전체 prompt/response를 남기지 않는다.

---

## 8. 통제형 AI 워크플로

Agent class 이름은 구현 세부이며 실행 계약의 원천이 아니다. `WorkflowRegistry`는 아래 8개 `WorkflowType`, workflow version, 고정 step, input/output schema, 허용 tool·호출 상한과 실패 정책을 등록한다. `[*]`는 모델 자유 loop가 아니라 검증된 bounded ID 목록을 registry 순서대로 처리하는 fan-out이다.

| WorkflowType                | 고정 step 순서                                                                                                                                                                                                                                                               |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DOCUMENT_INGESTION`        | `LOAD_DOCUMENT_SOURCE → EXTRACT_OR_ACCEPT_TEXT → MASK_TEXT → CHUNK_TEXT → EMBED_CHUNKS → EXTRACT_EVIDENCE_CANDIDATES → APPLY_EVIDENCE_CANDIDATES → FINALIZE_DOCUMENT`                                                                                                        |
| `JOB_POSTING_EXTRACTION`    | `FETCH_JOB_PAGE → SANITIZE_PAGE_TEXT → EXTRACT_JOB_FIELDS → MERGE_USER_OVERRIDES → APPLY_JOB_EXTRACTION`                                                                                                                                                                     |
| `JOB_ANALYSIS`              | `BUILD_JOB_SNAPSHOT → EXTRACT_REQUIREMENTS → ASSESS_ELIGIBILITY → RETRIEVE_VERIFIED_EVIDENCE → MATCH_EVIDENCE → SCORE_FIT → VALIDATE_ANALYSIS → PERSIST_ANALYSIS`                                                                                                            |
| `COVER_LETTER_GENERATION`   | `BUILD_GENERATION_CONTEXT → PLAN_QUESTIONS → ANALYZE_QUESTION[*] → RETRIEVE_EVIDENCE[*] → ALLOCATE_EXPERIENCES → WRITE_ANSWER[*] → FACT_CHECK_ANSWER[*] → APPLY_ANSWER_VERSION[*]`                                                                                           |
| `COVER_LETTER_VERIFICATION` | `LOAD_ANSWER_VERSION → BUILD_PROVENANCE_CONTEXT → CHECK_FACTS → CHECK_REQUIREMENTS_AND_LENGTH → AGGREGATE_VERIFICATION → PERSIST_VERIFICATION`                                                                                                                               |
| `INTERVIEW_PREPARATION`     | `VALIDATE_PREREQUISITES → BUILD_PUBLIC_SEARCH_PLAN → SEARCH_OFFICIAL_SOURCES → SEARCH_INTERVIEW_SOURCES → DEDUPE_CLASSIFY_SOURCES → ASSESS_SOURCE_COVERAGE → BUILD_QUESTION_CONTEXT → GENERATE_QUESTIONS → VALIDATE_QUESTION_PROVENANCE → PERSIST_RESEARCH_AND_QUESTION_SET` |
| `INTERVIEW_ANSWER_FEEDBACK` | `LOAD_ANSWER_VERSION → BUILD_FEEDBACK_CONTEXT → ANALYZE_ANSWER → VALIDATE_FEEDBACK → PERSIST_FEEDBACK`                                                                                                                                                                       |
| `MOCK_INTERVIEW_FEEDBACK`   | `LOAD_SESSION_SNAPSHOT → ANALYZE_TURNS → SYNTHESIZE_SESSION_FEEDBACK → VALIDATE_FEEDBACK → PERSIST_FEEDBACK`                                                                                                                                                                 |

공고 create에 usable 수동 본문이 있으면 `JOB_POSTING_EXTRACTION` run을 만들지 않고 `MANUAL_INPUT_PROVIDED`로 저장한다. 수동 본문이 없을 때만 위 extraction workflow를 enqueue한다.

동기 mock start/message는 WorkflowType이나 Agent Run이 아니며 bounded turn executor와 `mock_interview_turns`를 사용한다. 회원 탈퇴도 Agent Run이 아니라 독립 deletion task다.

`AgentOrchestrator`는 `WorkflowStateStore`, `ContextBuilder`, `ModelRouter`, `BudgetGuard`, `PromptRegistry`, `AgentExecutor`, `ExecutionRecorder`를 조정한다. 기능 domain은 AI repository를 직접 쓰지 않고 owner·version을 다시 검증하는 query/command port를 제공한다.

### 8.1 실행·재사용 규칙

- 자동 재시도 2회는 최초 포함 총 3 attempt다.
- 429/5xx, 일시 network, 비동기 timeout, structured output validation 실패만 자동 재시도한다.
- owner/input/domain validation, safety block, configuration, budget 오류는 자동 재시도하지 않는다.
- LOW_COST→BALANCED 자동 승격은 최대 1회이며 attempt를 하나 소비한다. HIGH_QUALITY 자동 승격은 없다.
- terminal user retry는 lineage를 가진 새 run, `WAITING_USER` manual resume은 같은 run이다.
- 성공 step 재사용 hash에는 user scope, workflow/step/scope/version, resource·upstream·context hash, prompt/schema/model/deterministic policy version과 requested quality를 포함한다. run ID와 attempt는 제외한다.
- 원본 결과가 존재하고 hash·사용자·품질이 정확히 같을 때만 `REUSED`한다. HIGH_QUALITY 요청은 낮은 품질 결과를 재사용하지 않는다.
- step output에는 result/provenance ref와 validation summary만 저장하고 원문·전체 prompt/response는 저장하지 않는다.

### 8.2 Context 정책

우선순위는 요청·지시 → 최신 공고/analysis → 선택 VERIFIED evidence → 관련 VERIFIED evidence → current answer → research provenance → step allowlist의 미승인 masked chunk다. 모든 DB/vector 조회는 user scope와 active embedding generation을 포함한다. Context snapshot은 resource version/hash, evidence·chunk·source ID, page 범위, 당시 verification 상태와 truncation summary를 가진다.

---

## 9. 모델 라우팅과 비용

공개 `AiQualityMode=ECONOMY|BALANCED|HIGH_QUALITY`는 사용자 품질 의도이고 내부 `ModelTier=LOW_COST|BALANCED|HIGH_QUALITY`는 provider-independent routing 결과다. 일반 API는 provider/model ID와 step별 tier를 노출하지 않고 Agent Run의 `highestModelTierUsed`만 표시한다.

| 공개 모드      | 내부 정책                                                                    |
| -------------- | ---------------------------------------------------------------------------- |
| `ECONOMY`      | 생성·분석도 LOW_COST 우선, retryable structured failure 때 BALANCED 1회 가능 |
| `BALANCED`     | 추출·분류 LOW_COST, 분석·생성 BALANCED, HIGH_QUALITY 자동 승격 금지          |
| `HIGH_QUALITY` | 전처리·검색·추출은 저비용, 허용 workflow의 최종 생성·검토만 HIGH_QUALITY     |

`HIGH_QUALITY`는 `highQualityEnabled=true`, 요청별 명시 선택, 비용 예약 성공을 모두 요구한다. 허용 workflow는 자기소개서 생성·검증과 면접 답변 feedback뿐이다. 공고 분석과 면접 준비는 `ECONOMY|BALANCED`, 문서·공고 추출은 내부 저비용 정책만 사용한다. 모의 면접 종합 feedback은 `BALANCED` 고정이다.

### 9.1 가격·reserve/settle

- 초기 user default daily budget USD 1.00, system maximum daily budget per user USD 2.00, maximum async Agent Run USD 0.30, reset zone `Asia/Seoul`이다.
- 값은 운영 설정과 versioned policy에 두고 비즈니스 코드 상수로 하드코딩하지 않는다.
- 외부 provider 가격은 문서에 금액으로 고정하지 않고 immutable price catalog version/item에 저장한다.
- chat input/cached input/output, embedding input unit, BASIC/ADVANCED search request를 모두 usage와 예산에 포함한다. 무료/cache hit도 0 cost row를 남긴다.
- resource/run/turn을 만들기 전 worst-case 비용을 ledger에 원자 reserve하고 접수 당시 price version으로 settle한다.
- 실제액이 예약을 넘으면 top-up 성공 때만 다음 호출을 허용한다. terminal·`WAITING_USER`에는 미사용액을 release한다.
- `actualCostUsd`는 provider invoice가 아니라 고정 catalog로 계산한 billable estimate다.
- 비용 부족은 resource/run 생성 전 `429 RATE_OR_BUDGET_LIMIT_EXCEEDED`이며 자동 재시도하지 않는다.

---

## 10. 비동기 처리와 상태 복구

### 비동기 대상

- 문서 파싱·근거 추출
- 공고 URL 본문 추출
- 공고 분석
- 자기소개서 생성·검증
- 회사·면접 정보 조사
- 면접 질문 세트 생성
- 면접 답변 feedback
- 모의 면접 session 종합 feedback

### 처리 방식

```text
API 요청
→ domain resource + agent_run(QUEUED) 한 transaction
→ commit 뒤 DB claim/reconciliation
→ TaskExecutor 실행
→ 단계별 agent_step 저장
→ 검증된 결과를 멱등 domain command로 반영
→ commit된 snapshot을 SSE로 전달
```

- `QUEUED→RUNNING`은 조건부 update 또는 `FOR UPDATE SKIP LOCKED`로 worker 하나가 claim한다.
- heartbeat 15초, lease 60초, reconciliation 30초다. lease 만료 RUNNING은 immutable `INTERRUPTED`다.
- cooperative cancel은 외부 호출 전후와 domain apply 직전에 확인하고 run terminal 처리와 resource 마지막 안정 상태 복원을 한 transaction으로 수행한다.
- 이미 발생한 provider 비용은 settle하지만 cancel 뒤 결과는 적용하지 않는다.
- 안정 상태 mapping은 job extraction에서 usable source가 있으면 `EXTRACTED|MANUAL_INPUT_PROVIDED`, 없으면 `NEEDS_MANUAL_INPUT`; document parse는 같은 revision의 committed text/chunk가 있으면 `PARSED`, 없으면 `UPLOADED`; evidence extraction은 prior 성공 snapshot이 있으면 `SUCCEEDED`, 없으면 `NOT_STARTED`다.
- interview preparation cancel은 research `CANCELLED`와 빈 read-only question set, answer feedback은 row 없음, mock feedback은 `feedbackStatus=CANCELLED`, cover verification은 연결 PENDING verification `FAILED`로 종결한다. reconciliation도 같은 mapping을 사용한다.
- SSE는 연결 직후 DB `snapshot`, 이후 `progress|step|waiting_user|heartbeat|terminal`을 전송한다. durable replay는 만들지 않는다.
- client는 `stateVersion`으로 중복을 제거하고 재연결 실패 시 REST polling으로 전환한다.
- TaskExecutor queue와 SSE memory state는 상태 원천이 아니다.

### 동기 모의 면접 경계

- start/message는 Agent Run을 만들지 않는 bounded 동기 executor다.
- HTTP deadline 20초, request당 chat 1회, search 0회, embedding 0회, turn USD 0.03, session 동기 합계 USD 0.30이다.
- timeout·structured output 실패를 서버가 자동 재호출하지 않는다.
- 동일 `clientRequestId`/hash는 처리 상태 또는 성공·실패 terminal의 원래 안전 응답을 복구하며 실패 replay도 재호출하지 않는다. 명시적으로 새 ID를 받은 경우만 새 유료 호출을 수행한다.
- immediate feedback도 같은 structured 응답에서 만들고 별도 모델 호출을 하지 않는다.
- session complete만 `MOCK_INTERVIEW_FEEDBACK` Agent Run을 만들며 품질은 `BALANCED` 고정이다.

### 공고 마감 Scheduler

- 실행 주기: 매시간
- 대상: `status IN (IN_PROGRESS, SUBMITTED)` 및 `deadline_at <= now()`
- 처리: `CLOSED`로 변경
- `submitted_at`은 보존
- `job_status_history` 기록
- 사용자 입력 마감일이 변경되면 명시적 재오픈 가능

---

## 11. 관찰성

### 공통 추적 필드

- `X-Request-Id`
- `agent_run_id`
- `agent_step_id`
- `user_id`는 로그에 직접 노출하지 않고 내부 상관키 사용
- workflow / agent / model
- latency
- input/output token
- estimated cost
- retry count
- status / error code

### 로그 금지 항목

- 이력서·포트폴리오 원문
- 자기소개서 본문
- 면접 답변 본문
- LLM 전체 프롬프트와 전체 응답
- 이메일·전화번호 등 개인정보

---

## 12. 테스트 전략

| 계층         | 기술                      | 필수 범위                                                                                              |
| ------------ | ------------------------- | ------------------------------------------------------------------------------------------------------ |
| Domain       | JUnit 5                   | 전체 상태 전이, owner, version, finalization/archive, scheduler·cancel race                            |
| Repository   | Testcontainers PostgreSQL | composite FK, partial unique, CHECK, budget reserve 경쟁, pgvector user/generation exact 검색          |
| API          | MockMvc                   | Session/CSRF, DTO 상한, 실제 status/error, idempotency replay, 404/409/429                             |
| External API | WireMock/Fake             | LLM/Search/Object/URL fetch 429·5xx·timeout, SSRF, 출처 부족                                           |
| Workflow     | Fake Gateway              | 고정 step 순서, 최대 3 attempt, same-run resume/new-run retry, reuse, cancel, restart, partial success |
| Embedding    | Fake + PostgreSQL         | configured 1536↔typmod fail-fast, model/generation 혼합 금지, exact cosine fixture                     |
| Frontend     | Vitest + Vue Test Utils   | form, enum, OUTDATED/coverage/feedback 상태, 409 비교, user cache/draft, SSE 복구                      |
| E2E          | Playwright                | 가입→업로드→공고→자소서→면접, 두 사용자 404, logout/탈퇴 purge UI                                      |

LLM 품질 테스트는 고정 Fixture와 평가 기준을 사용한다. local/CI의 chat·embedding·search provider는 명시적으로 활성화하지 않으면 `none`/Fake이며 실제 유료 API를 호출하지 않는다. timeout, 구조화 output 실패, 예산 부족과 prompt injection은 Fake/WireMock으로 재현한다.

---

## 13. 개발 및 운영 환경

### Docker Compose

```text
postgres:18 + pgvector
minio
mailpit (선택)
```

Spring Boot와 Vue는 로컬 프로세스로 실행한다.

### Production 최소 구성

```text
Nginx
├─ Vue static
└─ /api → Spring Boot

Spring Boot
PostgreSQL
S3/R2
External LLM/Search API
```

### 필수 환경 변수

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
SESSION_COOKIE_SECURE

OBJECT_STORAGE_ENDPOINT
OBJECT_STORAGE_ACCESS_KEY
OBJECT_STORAGE_SECRET_KEY
OBJECT_STORAGE_BUCKET

AI_PROVIDER
AI_PROVIDER_API_KEY
AI_MODEL_LOW_COST
AI_MODEL_BALANCED
AI_MODEL_HIGH_QUALITY
AI_EMBEDDING_MODEL
AI_EMBEDDING_DIMENSION
AI_DAILY_BUDGET_USD
AI_RUN_MAX_COST_USD
AI_BUDGET_RESET_ZONE

TAVILY_API_KEY
FRONTEND_ORIGIN
```

환경 변수는 versioned DB policy를 선택·override하는 운영 입력이며 모델·비용 한도 자체를 business code에 하드코딩하지 않는다. active embedding policy는 provider/model/dimension/generation이 함께 검증돼야 boot가 성공한다.

---

## 14. MVP 제외 범위

- 채용 사이트 전체 자동 수집·크롤링
- 소셜 로그인
- 음성 STT/TTS 면접
- 영상 면접 분석
- 관리자·결제·구독
- 다국어 UI
- 실시간 공동 편집
- Python/LangGraph 서버
- Kafka·Redis 기반 분산 처리
- OCR과 HWP 직접 파싱

이 기능들은 핵심 흐름 검증 후 확장한다.
