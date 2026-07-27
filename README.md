# Hiresemble

Hiresemble은 사용자의 검증된 경력 근거를 바탕으로 채용 공고 분석, 자기소개서 작성·검증, 면접 준비와 모의 면접을 지원하는 개인 맞춤형 AI 취업 준비 서비스입니다.

현재 저장소는 **P0 계약 기준선부터 P5 채용 공고 등록·URL 추출·상태·자동 마감까지** 구현되어 있습니다. P6 공고 분석·RAG부터 P10 운영 완성도까지는 아직 착수하지 않았습니다. 상세 계약과 단계별 상태는 [`docs/spec`](docs/spec)과 [`docs/design/implementation-plan.md`](docs/design/implementation-plan.md)에서 확인할 수 있습니다.

## 구성

```text
hiresemble/
├─ backend/       Java 21, Spring Boot 4.1, Spring AI 2.0, Gradle
├─ frontend/      Vue 3, TypeScript 5, Vite, pnpm
├─ docs/spec/     기능·API·DB·페이지·기술 명세
├─ .github/       CI와 Dependabot 설정
└─ compose.yaml   PostgreSQL/pgvector, MinIO, 선택적 Mailpit
```

Spring Boot와 Vue는 로컬 프로세스로 실행하고, 상태가 필요한 개발 인프라만 Docker Compose로 실행합니다.

## 준비물

- Java 21
- Docker Desktop 또는 Docker Engine + Compose
- Node.js 24 LTS
- Corepack (Node.js 배포판에 포함)

Gradle과 pnpm 버전은 각각 Wrapper와 `packageManager` 필드로 고정되어 있으므로 별도 전역 설치가 필요하지 않습니다.

## 1. 로컬 환경 변수

루트 예시 파일을 복사합니다.

```powershell
Copy-Item .env.example .env
```

macOS/Linux에서는 `cp .env.example .env`를 사용합니다. `.env`에는 로컬 전용 값만 두며 Git에 포함되지 않습니다. 기본값만 사용할 경우 복사하지 않아도 Compose는 실행됩니다.

## 2. 개발 인프라 실행

```shell
docker compose up -d
docker compose ps
```

메일 확인 환경까지 필요할 때만 `mail` 프로필을 활성화합니다.

```shell
docker compose --profile mail up -d
```

기본 접속 정보:

| 서비스        | 주소                                       | 용도                     |
| ------------- | ------------------------------------------ | ------------------------ |
| PostgreSQL    | `localhost:${POSTGRES_PORT}` (기본 `5432`) | 애플리케이션 DB          |
| MinIO API     | `http://localhost:9000`                    | S3 호환 API              |
| MinIO Console | `http://localhost:9001`                    | 개발용 Object Storage UI |
| Mailpit SMTP  | `localhost:1025`                           | 개발 메일 수신           |
| Mailpit UI    | `http://localhost:8025`                    | 개발 메일 확인           |

`minio-init` 컨테이너는 `${OBJECT_STORAGE_BUCKET}` 버킷을 비공개로 생성한 뒤 정상 종료합니다.

## 3. 백엔드 실행

Windows:

```powershell
Set-Location backend
.\gradlew.bat bootRun
```

macOS/Linux:

```shell
cd backend
sh ./gradlew bootRun
```

기본 포트는 `8080`, Actuator health 경로는 `/actuator/health`입니다. 공개 API는 인증 5개, 프로필·direct evidence 25개, Agent Run 5개, Document 8개와 Job 7개인 총 50 operations/34 paths이며 Spring Session Cookie와 CSRF를 사용합니다. PostgreSQL schema는 기존 V1~~V5를 보존한 Flyway V1~~V6가 관리합니다.

`.env.example`의 `IDEMPOTENCY_HMAC_KEY`는 의도적으로 비어 있습니다. Document와 Job 생성처럼 멱등성이 필요한 mutation을 실행하려면 안전한 versioned 운영 secret을 설정해야 합니다.

유료 외부 호출 없이 로컬 부팅과 테스트가 가능하도록 실제 AI 모델과 VectorStore 자동 구성은 기본적으로 꺼져 있습니다. P5 URL fetch와 AI 추출 테스트도 Fake gateway만 사용합니다. P6 이후 실제 연동을 별도로 승인하고 구성할 때만 `.env`에서 API 키와 provider 값을 명시적으로 활성화합니다.

```dotenv
AI_CHAT_MODEL_PROVIDER=openai
AI_EMBEDDING_MODEL_PROVIDER=openai
AI_VECTOR_STORE_PROVIDER=pgvector
```

검증 명령:

```shell
cd backend
./gradlew check
```

## 4. 프론트엔드 실행

```shell
cd frontend
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

기본 주소는 `http://localhost:5173`입니다. Vite는 `/api` 요청을 `http://localhost:8080`으로 프록시합니다.

익명 사용자는 `/login` 또는 `/signup`, 인증 사용자는 보호된 `/onboarding`, `/profile/**`, `/agent-runs`, `/documents`, `/jobs`, `/jobs/new`, `/jobs/:jobId/overview`와 `/dashboard`를 사용할 수 있습니다. Dashboard는 아직 shell이고 P6 분석 route는 존재하지 않습니다.

검증 명령:

```shell
cd frontend
corepack pnpm check
```

Playwright 브라우저는 E2E 테스트를 작성하는 단계에서 다음 명령으로 설치할 수 있습니다.

```shell
corepack pnpm exec playwright install --with-deps chromium
```

P5 실제 브라우저 여정은 Backend가 격리 PostgreSQL·Spring·Vue·Fake fetch/AI·Chromium을 함께 기동하는 전용 Gradle task로 검증합니다.

```powershell
Set-Location backend
.\gradlew.bat p5BrowserE2eTest
```

## 인프라 종료

```shell
docker compose --profile mail down
```

Named volume의 개발 데이터를 함께 삭제하려면 의도적으로 `docker compose down --volumes`를 실행해야 합니다.

## 현재 제외한 범위

- P6 공고 분석·RAG, fit score, eligibility와 analysis history
- 자기소개서, 면접 준비·조사와 모의 면접
- Dashboard 실제 집계와 공개 AI 설정
- 실제 OpenAI·Tavily provider 기본 활성화
- 운영 배포 구성

이후 구현은 `docs/spec`의 모듈러 모놀리스 원칙, 사용자별 데이터 격리, 구조화된 AI 워크플로와 비용 통제 요구를 기준으로 진행합니다.
