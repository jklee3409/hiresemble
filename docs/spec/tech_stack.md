# 기술 스택 명세서

- 문서 버전: 1.0
- 기준일: 2026-07-17
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

| 구성요소 | MVP 배포 단위 |
|---|---|
| Frontend | 정적 호스팅 또는 Nginx |
| Backend | Spring Boot 단일 애플리케이션 |
| Database | PostgreSQL 단일 인스턴스 |
| Object Storage | 개발: MinIO / 운영: S3 또는 Cloudflare R2 |
| AI Provider | OpenAI 우선, Gateway 추상화 |
| Search Provider | Tavily Search API 우선, Gateway 추상화 |

마이크로서비스, Kafka, Redis, 별도 Python Agent Server는 MVP에서 사용하지 않는다.

---

## 3. 버전 기준

| 영역 | 선택 |
|---|---|
| Language | Java 21 LTS |
| Backend | Spring Boot 4.1.x |
| AI Framework | Spring AI 2.0.x |
| Frontend | Vue 3.x + TypeScript 5.x |
| Build | Gradle Kotlin DSL |
| Database | PostgreSQL 18.x |
| Vector Extension | pgvector 0.8.x |
| Node | 활성 LTS 버전 |
| Package Manager | pnpm |
| Container | Docker Compose |

Spring AI 2.0.x와 Spring Boot 4.0/4.1 호환 범위를 기준으로 선택한다. 패치 버전은 프로젝트 생성 시점의 최신 안정 버전으로 고정하고 Renovate 또는 Dependabot으로 업데이트한다.

---

## 4. Backend 기술 스택

### 4.1 Core

| 기술 | 용도 |
|---|---|
| Spring Web MVC | REST API |
| Spring Security | 인증·인가·CSRF |
| Spring Session JDBC | DB 기반 로그인 세션 |
| Spring Data JPA | 도메인 CRUD |
| JdbcClient | pgvector 및 복잡 쿼리 |
| Bean Validation | 입력 검증 |
| Flyway | DB 마이그레이션 |
| Spring Scheduling | 공고 마감 자동 처리 |
| Spring TaskExecutor | 문서 분석·AI 워크플로 비동기 실행 |
| Spring Actuator | Health / Metrics |
| Micrometer Observation | 지연시간·모델 사용량 관찰 |
| springdoc-openapi | OpenAPI 문서 |
| Jackson | JSON / JSONB 직렬화 |

### 4.2 AI

| 기술 | 용도 |
|---|---|
| Spring AI ChatClient | LLM 호출 |
| Spring AI Structured Output | Java DTO 기반 결과 생성 |
| Spring AI Tool Calling | 검색·근거 조회 등 제한된 도구 호출 |
| Spring AI VectorStore 또는 JdbcClient | pgvector 검색 |
| ModelRouter | 작업별 모델 선택 |
| BudgetGuard | 호출 수·토큰·비용 상한 |
| PromptRegistry | 버전이 있는 프롬프트 관리 |
| AiUsageRecorder | 모델·토큰·비용 기록 |

### 4.3 문서 처리

| 기술 | 용도 |
|---|---|
| Apache Tika | MIME 탐지 및 공통 텍스트 추출 |
| Apache PDFBox | PDF 페이지별 텍스트 추출 |
| Apache POI | DOCX 텍스트 추출 |
| Jsoup | 채용 공고 URL HTML 추출 및 정제 |
| SHA-256 | 중복 파일 및 중복 공고 감지 |

MVP 지원 파일은 `PDF`, `DOCX`, `TXT`이며, 이미지 기반 PDF OCR, HWP, PPTX는 제외한다. 텍스트 추출량이 기준 이하이면 `NEEDS_MANUAL_TEXT` 상태로 전환해 사용자가 텍스트를 직접 보완한다.

### 4.4 외부 검색

회사 정보와 유사 직무 면접 정보는 `WebSearchGateway`를 통해 검색한다.

```java
public interface WebSearchGateway {
    SearchResult search(SearchRequest request);
    ExtractResult extract(List<String> urls);
}
```

MVP 구현체는 Tavily REST API를 사용한다.

- 기본 검색: `basic`
- 사용자가 고품질 재조사를 요청한 경우에만 `advanced`
- 검색 결과에는 URL, 제목, 발행일, 조회일, 스니펫을 저장
- 커뮤니티 정보와 공식 출처를 구분
- 원문 전체를 영구 보관하지 않고 필요한 인용 스니펫과 메타데이터만 저장

---

## 5. Frontend 기술 스택

| 기술 | 용도 |
|---|---|
| Vue 3 Composition API | UI |
| TypeScript | 타입 안정성 |
| Vite | 개발·빌드 |
| Vue Router | 라우팅 |
| Pinia | 인증 사용자·전역 UI 상태 |
| TanStack Vue Query | 서버 상태·캐시·재조회 |
| Axios | REST 요청 및 CSRF 처리 |
| Tailwind CSS | 스타일 |
| PrimeVue 또는 shadcn-vue | 접근 가능한 UI 컴포넌트 |
| TipTap | 자기소개서·면접 답변 편집기 |
| Zod | 폼 스키마 검증 |
| Vitest + Vue Test Utils | 단위·컴포넌트 테스트 |
| Playwright | 핵심 E2E |

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
- MVP 데이터 규모에서는 정확 검색으로 시작 가능
- 데이터 증가 시 cosine distance 기반 HNSW 인덱스 사용
- 임베딩 모델 변경 시 `embedding_model`, `embedding_dimension`을 함께 저장하고 재색인

### 6.3 Object Storage

원본 파일은 DB BLOB가 아니라 S3 호환 스토리지에 저장한다.

```text
users/{userId}/documents/{documentId}/{sanitizedFilename}
```

- 개발: MinIO
- 운영: AWS S3 또는 Cloudflare R2
- 다운로드는 인증 후 단기 Presigned URL 발급
- 삭제 시 Object와 DB 메타데이터를 함께 삭제
- 업로드 파일명은 표시용이며 저장 키 생성에 직접 사용하지 않음

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

---

## 8. 멀티 에이전트 및 워크플로

### 8.1 에이전트 목록

| Agent | 책임 |
|---|---|
| ProfileExtractionAgent | 업로드 문서에서 구조화 경력·성과 근거 추출 |
| JobPostingExtractionAgent | URL 본문에서 회사·직무·마감일 추출 |
| JobAnalysisAgent | 필수·우대·업무·지원자격 구조화 |
| EligibilityAgent | 지원 가능 여부 판정 |
| ExperienceMatcherAgent | 공고 요구사항과 사용자 근거 매칭 |
| CompanyResearchAgent | 공식 회사·직무 정보 조사 |
| CoverLetterWriterAgent | 문항별 초안 생성 |
| FactCheckAgent | 수치·역할·경험 근거 검증 |
| InterviewResearchAgent | 유사 직무 면접 정보 검색·정리 |
| InterviewQuestionAgent | 예상 질문·답변 가이드 생성 |
| InterviewerAgent | 대화형 후속 질문 생성 |
| InterviewFeedbackAgent | 답변과 세션 피드백 생성 |

### 8.2 Orchestrator 구성

```text
WorkflowRegistry
├─ DocumentIngestionWorkflow
├─ JobRegistrationWorkflow
├─ JobAnalysisWorkflow
├─ CoverLetterGenerationWorkflow
├─ CoverLetterVerificationWorkflow
├─ InterviewPreparationWorkflow
└─ MockInterviewWorkflow
```

```text
AgentOrchestrator
├─ WorkflowStateStore
├─ ContextBuilder
├─ ModelRouter
├─ BudgetGuard
├─ PromptRegistry
├─ AgentExecutor
└─ ExecutionRecorder
```

### 8.3 실행 규칙

- 워크플로 최대 단계 수: 12
- 단계별 기본 재시도: 2회
- 요청당 최대 LLM 호출 수: 정책값
- 실패한 단계부터 재실행
- 이미 성공한 단계는 입력 해시가 동일하면 재사용
- 사용자 검토가 필요한 경우 `WAITING_USER`
- 각 단계 출력은 JSONB와 Java DTO 스키마를 동시에 관리
- 프롬프트 버전과 모델명을 실행 기록에 저장

---

## 9. 모델 라우팅

코드에서는 실제 모델명 대신 작업 등급을 사용한다.

```text
LOW_COST
BALANCED
HIGH_QUALITY
```

### 기본 작업 매핑

| 작업 | 기본 등급 |
|---|---|
| 요청 분류 | LOW_COST |
| 문서 필드 추출 | LOW_COST |
| 공고 필드·마감일 추출 | LOW_COST |
| 지원 자격 판정 | LOW_COST |
| 공고 적합도 분석 | BALANCED |
| 경험 매칭 | BALANCED |
| 자기소개서 초안 | BALANCED |
| 근거 검증 | BALANCED |
| 예상 면접 질문 | BALANCED |
| 대화형 면접 | BALANCED |
| 최종 고품질 문장 검토 | HIGH_QUALITY, 사용자 선택 |

환경 변수 예시:

```env
AI_PROVIDER=openai
AI_MODEL_LOW_COST=<provider-low-cost-chat-model>
AI_MODEL_BALANCED=gpt-5-mini
AI_MODEL_HIGH_QUALITY=<provider-high-quality-model>
AI_EMBEDDING_MODEL=<provider-small-embedding-model>
AI_DAILY_BUDGET_USD=2.00
AI_RUN_MAX_COST_USD=0.30
```

### 라우팅 원칙

1. 모델명을 애플리케이션 코드에 하드코딩하지 않는다.
2. `ai_model_policies` 테이블과 환경 변수로 정책을 오버라이드한다.
3. 저비용 모델 실패 또는 구조화 출력 검증 실패 시 `BALANCED`로 1회 승격한다.
4. `HIGH_QUALITY` 모델은 `user_ai_preferences.high_quality_enabled=true`이고 사용자가 해당 요청에서 명시적으로 선택한 경우에만 사용한다.
5. 사용자 비용 한도와 시스템 비용 한도 중 더 낮은 값을 적용한다.
6. 모델별 토큰 사용량과 예상 비용을 `ai_usage_logs`에 기록한다.

---

## 10. 비동기 처리와 상태 복구

### 비동기 대상

- 문서 파싱·근거 추출
- 공고 URL 본문 추출
- 공고 분석
- 자기소개서 생성·검증
- 회사·면접 정보 조사
- 면접 질문 세트 생성

### 처리 방식

```text
API 요청
→ agent_run 생성(QUEUED)
→ TaskExecutor 실행
→ 단계별 agent_step 저장
→ SSE로 진행 상태 전송
→ 최종 도메인 데이터 반영
```

서버 재시작 시 `RUNNING` 상태로 오래 남은 실행을 `INTERRUPTED`로 변경하고, 멱등성이 보장된 마지막 실패 단계부터 사용자가 재시도할 수 있다.

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

| 계층 | 기술 | 필수 범위 |
|---|---|---|
| Domain | JUnit 5 | 상태 전이, 권한, 버전 규칙 |
| Repository | Testcontainers PostgreSQL | FK, unique, pgvector 검색 |
| API | MockMvc | 인증, 검증, 오류 응답 |
| External API | WireMock | LLM/Search/Object Storage 실패 |
| Workflow | Fake Model Gateway | 단계 순서, 재시도, 복구 |
| Frontend | Vitest | 폼, 상태 표시, 편집기 |
| E2E | Playwright | 가입→업로드→공고→자소서→면접 |

LLM 품질 테스트는 고정 Fixture와 평가 기준을 사용하며, CI 기본 테스트에서는 실제 유료 API를 호출하지 않는다.

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
AI_DAILY_BUDGET_USD
AI_RUN_MAX_COST_USD

TAVILY_API_KEY
FRONTEND_ORIGIN
```

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
