# Hiresemble Codex 작업 지침

이 파일은 저장소에서 Codex가 따라야 하는 최상위 작업 지침이다. Codex는 작업을 시작할 때 이 파일을 먼저 읽고, 아래 라우팅 표의 관련 규칙과 작업 대상 디렉터리의 추적 문서를 직접 열어 확인해야 한다. 링크된 문서는 자동 로드된다고 가정하지 않는다.

## 프로젝트 목적

Hiresemble은 사용자가 승인한 경력 근거를 바탕으로 채용 공고 분석, 자기소개서 작성·검증, 면접 준비와 모의 면접을 지원하는 개인 맞춤형 AI 취업 준비 서비스다. 비즈니스 데이터의 원천은 Spring Boot와 PostgreSQL이며, AI 실행은 자유 루프가 아닌 코드로 통제된 워크플로로 구성한다.

현재 구현 범위와 향후 요구사항을 혼동하지 않는다. 실제 상태는 코드와 각 `progress.md`, 목표 계약은 `docs/spec/`를 기준으로 판단한다.

## 기준 문서와 우선순위

충돌이 있으면 다음 순서로 판단하고, 해소할 수 없는 계약 충돌은 임의로 구현하지 말고 문서에 기록한다.

1. 현재 사용자 요청과 명시적 승인
2. `docs/spec/`의 기능·API·DB·페이지·기술 명세
3. 이 파일과 `docs/agent-rules/`의 작업 규칙
4. 대상 및 상위 디렉터리의 `index.md`, `progress.md`
5. 현재 코드와 설정에서 확인한 구현 사실

명세와 구현이 다르면 구현을 조용히 바꾸지 않는다. 호환성 영향, 마이그레이션 필요성, 선택지를 먼저 기록한다.

## 전체 구조와 기술 스택

| 경로                | 책임                                     | 주요 기술                                                  |
| ------------------- | ---------------------------------------- | ---------------------------------------------------------- |
| `backend/`          | REST API, 인증·인가, 도메인, AI 워크플로 | Java 21, Spring Boot 4.1, Spring AI 2.0, Gradle Kotlin DSL |
| `frontend/`         | SPA 화면, 서버 상태, 사용자 상호작용     | Vue 3, TypeScript 5, Vite, pnpm, Pinia, Vue Query          |
| `docs/spec/`        | 기능·API·DB·페이지·기술 계약             | Markdown                                                   |
| `docs/agent-rules/` | Codex의 세부 개발·문서화 규칙            | Markdown                                                   |
| `.github/`          | CI와 의존성 갱신                         | GitHub Actions, Dependabot                                 |
| `.codex/`           | 저장소 범위 Codex 설정                   | TOML                                                       |
| `compose.yaml`      | 로컬 상태 저장 인프라                    | PostgreSQL 18/pgvector, MinIO, 선택적 Mailpit              |

백엔드와 프론트엔드는 로컬 프로세스로 실행하고, 상태가 필요한 개발 인프라는 Docker Compose로 실행한다. 운영 비밀값은 커밋하지 않으며 `.env.example`에는 안전한 예시만 둔다.

## 반드시 먼저 읽을 규칙

| 작업 유형                                           | 필수 규칙                                                                                                                                                  |
| --------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 모든 파일 생성·수정·삭제                            | [`docs/agent-rules/workflow.md`](docs/agent-rules/workflow.md), [`docs/agent-rules/documentation-tracking.md`](docs/agent-rules/documentation-tracking.md) |
| Spring 서버                                         | [`docs/agent-rules/backend-development.md`](docs/agent-rules/backend-development.md)                                                                       |
| Controller 응답, Service 예외, 검증, 인증·인가 오류 | [`docs/agent-rules/backend-response-exception.md`](docs/agent-rules/backend-response-exception.md)                                                         |
| Vue 프론트엔드                                      | [`docs/agent-rules/frontend-development.md`](docs/agent-rules/frontend-development.md)                                                                     |
| Docker, DB, 환경 변수, CI                           | [`docs/agent-rules/infrastructure.md`](docs/agent-rules/infrastructure.md)                                                                                 |

## 작업 전 확인 절차

파일을 생성하거나 수정하기 전에 순서대로 수행한다.

1. 루트 `AGENTS.md`를 읽는다.
2. 작업 유형에 맞는 `docs/agent-rules/` 문서를 읽는다.
3. 대상 디렉터리와 루트까지의 모든 상위 디렉터리에서 존재하는 `index.md`를 읽는다.
4. 같은 범위의 `progress.md`를 읽는다.
5. 관련 `docs/spec/`, 코드, 테스트, 설정과 사용처를 검색한다.
6. `git status`로 사용자 변경을 확인하고 보존한다.
7. 계약이나 데이터 손실 위험이 있으면 구현 전에 영향과 선택지를 명확히 한다.

추적 문서가 아직 없는 새 디렉터리는 가장 가까운 상위 문서를 먼저 읽고, 디렉터리를 만들 때 `index.md`와 `progress.md`도 함께 생성한다.

## 코드 작성 및 수정 원칙

- 요청 범위에 필요한 최소 변경만 수행하고 관련 없는 리팩터링을 섞지 않는다.
- 기존 구현과 호출부를 확인한 뒤 수정한다. 근거 없이 삭제하거나 덮어쓰지 않는다.
- 외부 API, 공개 DTO, DB 스키마, 이벤트 형식의 호환성을 임의로 깨지 않는다.
- 패키지와 모듈 경계를 지키고 도메인 규칙을 Controller나 UI에 중복 구현하지 않는다.
- 타입 안전한 DTO/record와 명시적인 상태 전이를 우선한다.
- 비밀값, 개인정보, 문서 원문, 전체 LLM 프롬프트·응답을 코드나 로그에 남기지 않는다.
- 실제 유료 AI·검색 API는 명시적 승인 없이 테스트에서 호출하지 않는다.
- Flyway 적용 이력이 있는 migration은 수정하지 않고 새 버전 파일을 추가한다.
- 의존성 추가와 버전 변경은 필요성, 라이선스, 호환성, 잠금 파일 영향을 확인한다.

Spring 공통 응답·예외 처리는 레퍼런스 `E:\dev_factory\side-project\orchestrator-module-hardening`에서 확인한 중앙 응답 생성, `ErrorCode`, 커스텀 예외, 전역 변환 구조를 참고하되 현재 API 명세를 우선한다. 특히 실패를 항상 HTTP 200으로 반환하는 방식과 레퍼런스의 숫자 코드를 그대로 복사하지 않는다. 상세 규칙은 `backend-response-exception.md`를 따른다.

## 테스트 및 검증 원칙

변경 범위에 비례해 가장 가까운 검증부터 실행하고, 최종적으로 관련 모듈의 표준 명령을 실행한다.

```powershell
# Backend (Windows)
Set-Location backend
.\gradlew.bat check

# Frontend
Set-Location frontend
corepack pnpm check

# Infrastructure
docker compose config --quiet
```

- 버그 수정에는 가능하면 실패를 재현하는 테스트를 먼저 추가한다.
- Repository 통합 테스트는 PostgreSQL/Testcontainers를 사용하며 운영 DB에 연결하지 않는다.
- 외부 서비스는 Fake 또는 WireMock으로 대체한다.
- 테스트를 실행할 수 없거나 실패하면 명령, 실패 지점, 원인, 미검증 범위를 숨기지 않고 `progress.md`에 남긴다.

## 문서 관리

- 각 관리 대상 디렉터리는 책임을 설명하는 `index.md`와 상태를 추적하는 `progress.md`를 가진다.
- 상위 문서는 전체 책임과 모듈 관계, 하위 문서는 세부 책임과 구현 상태만 다룬다.
- 같은 내용을 여러 문서에 복사하지 않고 상대 경로 링크로 연결한다.
- 구조나 책임이 바뀌면 `index.md`, 구현 상태나 검증 결과가 바뀌면 `progress.md`를 같은 작업에서 갱신한다.
- `.git`, IDE 설정, 의존성, build 결과, cache/temp, 자동 생성 코드, 외부 도구 디렉터리에는 추적 문서를 만들지 않는다.

세부 대상과 작성 형식은 `docs/agent-rules/documentation-tracking.md`를 따른다.

## 작업 후 확인 절차

1. 변경 파일과 호출부를 다시 읽고 명세·규칙 위반과 불필요한 변경을 확인한다.
2. 관련 lint, format check, type check, test, build 또는 설정 검증을 실행한다.
3. 영향받은 각 디렉터리의 `progress.md`에 목적, 주요 파일, 결정, 명령, 결과, 후속 작업을 기록한다.
4. 구조나 책임이 달라졌다면 해당 `index.md`를 갱신한다.
5. 여러 디렉터리에 영향이 있으면 각각 갱신하고, 프로젝트 전체 영향은 루트 `progress.md`에 요약한다.
6. `git status`와 diff를 확인해 비밀값, 생성물, 사용자 변경 훼손이 없는지 검사한다.

## 금지 사항

- 확인하지 않은 레퍼런스 구조를 추측해 구현하지 않는다.
- 실패를 성공으로 보고하거나 테스트 결과를 생략하지 않는다.
- `.env`, 자격 증명, 실제 사용자 데이터, 개인정보를 커밋하지 않는다.
- 사용자 변경을 `git reset --hard`, 무단 checkout, 대량 삭제로 되돌리지 않는다.
- 요청 없이 실제 외부 시스템에 배포, 데이터 삭제, 메시지 전송, 비용 발생 호출을 하지 않는다.
- API 오류를 무조건 `200 OK`로 감싸거나 Security 오류만 별도 JSON 문자열로 작성하지 않는다.
- 검증 오류의 내부 예외 메시지, 스택 트레이스, 민감한 값을 클라이언트에 노출하지 않는다.
- `index.md`와 `progress.md`에 동일한 장문 설명을 반복하지 않는다.

## 완료 조건

다음을 모두 만족해야 작업 완료로 보고한다.

- 사용자 요청의 인수 조건이 충족되고 관련 없는 변경이 없다.
- 현재 명세, 호환성, 보안, 디렉터리 규칙을 확인했다.
- 변경 범위의 테스트·검증을 실행했고 결과를 설명할 수 있다.
- 영향받은 `index.md`와 `progress.md`가 실제 상태와 일치한다.
- 실패나 미확인 사항, 후속 작업이 명시되어 있다.
- 최종 `git status`에 비밀값과 추적하면 안 되는 생성물이 없다.
