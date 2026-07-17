# 백엔드 모듈 안내

## 디렉터리 목적

`backend/`는 Hiresemble의 REST API, 인증·인가, 도메인 처리, AI 워크플로와 영속성 연동을 담당할 Spring Boot 애플리케이션 모듈이다. 현재는 애플리케이션을 실행할 수 있는 초기 환경만 있으며 실제 비즈니스 기능은 구현되지 않았다.

## 주요 파일 및 하위 디렉터리

| 경로                                               | 역할                                                                      |
| -------------------------------------------------- | ------------------------------------------------------------------------- |
| [`build.gradle.kts`](build.gradle.kts)             | Java 21, Spring Boot, Spring AI, 데이터·문서·S3·테스트 의존성과 빌드 규칙 |
| [`settings.gradle.kts`](settings.gradle.kts)       | Gradle 프로젝트 이름 설정                                                 |
| [`gradlew`](gradlew), [`gradlew.bat`](gradlew.bat) | Unix/Windows용 Gradle Wrapper 진입점                                      |
| [`src/`](src/)                                     | 프로젝트가 직접 관리하는 백엔드 소스 루트                                 |
| `gradle/`                                          | Gradle Wrapper 도구 파일이며 문서 관리 대상에서 제외                      |

## 구성 요소 역할

- [`src/main/java/`](src/main/java/)는 실행 가능한 Java 애플리케이션과 향후 도메인 코드를 소유한다.
- [`src/main/resources/`](src/main/resources/)는 Spring 설정과 Flyway migration을 소유한다.
- `build/`와 `.gradle/`은 재생성 가능한 빌드·캐시 영역이므로 소스나 추적 문서를 두지 않는다.

## 다른 디렉터리와의 의존 관계

- API·DB 계약은 [`../docs/spec/`](../docs/spec/)을 기준으로 한다.
- 로컬 PostgreSQL/pgvector와 Object Storage는 루트 [`../compose.yaml`](../compose.yaml)에서 제공한다.
- 프론트엔드는 향후 이 모듈의 `/api/v1` 계약과 Session Cookie/CSRF 정책에 의존한다.
- CI는 `.\gradlew.bat check`에 대응하는 백엔드 검증을 실행한다.

## 변경 시 주의사항

- 백엔드 파일 변경 전 [`../AGENTS.md`](../AGENTS.md)와 관련 에이전트 규칙, 대상 디렉터리의 추적 문서를 읽는다.
- 성공 응답을 임의의 공통 envelope로 감싸지 않고 API 명세의 실제 HTTP 상태와 DTO 계약을 유지한다.
- 적용된 Flyway migration은 수정하지 않고 새 버전 파일을 추가한다.
- `backend/gradle/`, `build/`, `.gradle/`은 외부 도구·생성물 영역이므로 `index.md`와 `progress.md`를 만들지 않는다.

## 관련 규칙 및 문서

- [Spring 백엔드 개발 규칙](../docs/agent-rules/backend-development.md)
- [공통 응답·예외 처리 규칙](../docs/agent-rules/backend-response-exception.md)
- [공통 작업 절차](../docs/agent-rules/workflow.md)
- [API 명세](../docs/spec/api.md)
- [DB 명세](../docs/spec/db.md)
- [백엔드 진행 상황](progress.md)
