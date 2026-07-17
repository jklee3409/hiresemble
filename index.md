# Hiresemble 저장소 안내

## 디렉터리 목적

이 저장소는 Hiresemble의 프론트엔드, Spring 백엔드, 로컬 인프라, 명세와 Codex 작업 규칙을 하나의 모노레포에서 관리한다. 현재는 초기 개발 환경과 계약 문서만 준비되어 있으며 실제 비즈니스 기능은 아직 구현되지 않았다.

## 주요 파일 및 하위 디렉터리

| 경로                           | 역할                                            |
| ------------------------------ | ----------------------------------------------- |
| [`AGENTS.md`](AGENTS.md)       | Codex가 모든 작업 전에 읽어야 하는 최상위 지침  |
| [`.codex/`](.codex/)           | 저장소 범위 Codex 설정                          |
| [`.github/`](.github/)         | CI와 Dependabot 설정                            |
| [`backend/`](backend/)         | Java/Spring Boot 모듈                           |
| [`frontend/`](frontend/)       | Vue/TypeScript SPA 모듈                         |
| [`docs/`](docs/)               | 제품 명세와 에이전트 세부 규칙                  |
| [`compose.yaml`](compose.yaml) | PostgreSQL/pgvector, MinIO, 선택적 Mailpit 구성 |
| [`.env.example`](.env.example) | 로컬 환경 변수의 안전한 예시                    |
| [`README.md`](README.md)       | 개발 환경 실행과 검증 안내                      |
| [`progress.md`](progress.md)   | 프로젝트 전체 진행 상황과 검증 이력             |

## 구성 요소 관계

```text
frontend -- /api/v1, Session Cookie/CSRF --> backend
backend  -- JDBC/Flyway -----------------> PostgreSQL + pgvector
backend  -- S3 API ----------------------> MinIO (local)
backend  -- optional SMTP ---------------> Mailpit (local)
docs/spec -------------------------------> API·DB·화면·기술 계약
AGENTS.md + docs/agent-rules ------------> Codex 작업 절차
.github/workflows -----------------------> backend/frontend/compose 검증
```

## 변경 시 주의사항

- 제품 계약은 [`docs/spec/`](docs/spec/)을 기준으로 하고, 실제 상태는 모듈별 `progress.md`와 코드에서 확인한다.
- 루트 설정 변경은 백엔드, 프론트엔드, 인프라와 CI에 미치는 영향을 함께 확인한다.
- `.env`와 비밀값은 커밋하지 않는다. 예시는 `.env.example`에만 안전한 값으로 관리한다.
- `.git`, `.idea`, `.vscode`, `node_modules`, `build`, `dist`, `target`, `.gradle`, cache/temp, 자동 생성 코드와 외부 의존성 디렉터리는 문서 관리 대상에서 제외한다.
- Gradle Wrapper의 `backend/gradle/`은 외부 도구 부트스트랩 영역이므로 `index.md`와 `progress.md`를 생성하지 않는다.

## 관련 규칙 및 문서

- [Codex 최상위 지침](AGENTS.md)
- [문서 추적 규칙](docs/agent-rules/documentation-tracking.md)
- [개발 작업 절차](docs/agent-rules/workflow.md)
- [기술 스택 명세](docs/spec/tech_stack.md)
- [프로젝트 진행 상황](progress.md)
