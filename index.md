# Hiresemble 저장소 안내

## 디렉터리 목적

이 저장소는 Hiresemble의 프론트엔드, Spring 백엔드, 로컬 인프라, 명세와 Codex 작업 규칙을 하나의 모노레포에서 관리한다. P0 계약 기준선, P1 인증, P2 프로필과 P3 Agent Run·AI runtime 기반이 구현되어 있다.

## 주요 파일 및 하위 디렉터리

| 경로                           | 역할                                             |
| ------------------------------ | ------------------------------------------------ |
| [`AGENTS.md`](AGENTS.md)       | Codex가 모든 작업 전에 읽어야 하는 최상위 지침   |
| [`.codex/`](.codex/)           | 저장소 범위 Codex 설정과 전문 서브 에이전트 역할 |
| [`.github/`](.github/)         | CI와 Dependabot 설정                             |
| [`backend/`](backend/)         | Java/Spring Boot 모듈                            |
| [`frontend/`](frontend/)       | Vue/TypeScript SPA 모듈                          |
| [`docs/`](docs/)               | 제품 명세, 전체 구현 설계와 에이전트 세부 규칙   |
| [`compose.yaml`](compose.yaml) | PostgreSQL/pgvector, MinIO, 선택적 Mailpit 구성  |
| [`.env.example`](.env.example) | 로컬 환경 변수의 안전한 예시                     |
| [`README.md`](README.md)       | 개발 환경 실행과 검증 안내                       |
| [`progress.md`](progress.md)   | 프로젝트 전체 진행 상황과 검증 이력              |

## 구성 요소 관계

```text
frontend -- /api/v1, Session Cookie/CSRF --> backend
backend  -- JDBC/Flyway -----------------> PostgreSQL + pgvector
backend  -- fixed workflow ports --------> disabled AI gateways (P3)
backend  -- S3 API ----------------------> MinIO (local)
backend  -- optional SMTP ---------------> Mailpit (local)
docs/spec -------------------------------> API·DB·화면·기술 계약
docs/design -----------------------------> 명세 기반 전체 설계·구현 순서·파일 소유권
AGENTS.md + docs/agent-rules + .codex ---> Codex 작업 절차와 역할 위임
.github/workflows -----------------------> backend/frontend/compose 검증
```

공개 HTTP 범위는 P1 인증 5개, P2 프로필·direct evidence 25개와 P3 Agent Run 5개로 총 35 operation이다. Frontend에는 `/agent-runs` 목록·상세와 복구 가능한 SSE UI가 있으며 `/dashboard`는 여전히 shell이다.

## 변경 시 주의사항

- 제품 계약은 [`docs/spec/`](docs/spec/)을 기준으로 하고, 실제 상태는 모듈별 `progress.md`와 코드에서 확인한다.
- 루트 Codex 스레드가 관리자 역할을 맡고 `.codex/agents/*.toml`은 직접 위임받은 전문 역할로만 사용한다.
- 루트 설정 변경은 백엔드, 프론트엔드, 인프라와 CI에 미치는 영향을 함께 확인한다.
- `.env`와 비밀값은 커밋하지 않는다. 예시는 `.env.example`에만 안전한 값으로 관리한다.
- `.git`, `.idea`, `.vscode`, `node_modules`, `build`, `dist`, `target`, `.gradle`, cache/temp, 자동 생성 코드와 외부 의존성 디렉터리는 문서 관리 대상에서 제외한다.
- Gradle Wrapper의 `backend/gradle/`은 외부 도구 부트스트랩 영역이므로 `index.md`와 `progress.md`를 생성하지 않는다.

## 관련 규칙 및 문서

- [Codex 최상위 지침](AGENTS.md)
- [문서 추적 규칙](docs/agent-rules/documentation-tracking.md)
- [개발 작업 절차](docs/agent-rules/workflow.md)
- [기술 스택 명세](docs/spec/tech_stack.md)
- [전체 시스템 설계](docs/design/system-architecture.md)
- [단계별 구현 계획](docs/design/implementation-plan.md)
- [프로젝트 진행 상황](progress.md)
