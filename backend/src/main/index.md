# 백엔드 운영 소스 안내

## 디렉터리 목적

`backend/src/main/`은 백엔드 배포물에 포함되는 Java 코드와 classpath 리소스를 함께 관리하는 운영 source set이다.

## 주요 파일 및 하위 디렉터리

| 경로                       | 역할                                                |
| -------------------------- | --------------------------------------------------- |
| [`java/`](java/)           | P1 auth, P2 profile과 P3 agentrun·ai 운영 package   |
| [`resources/`](resources/) | Session·Agent runtime 설정과 V1~V4 Flyway migration |

## 구성 요소 역할

- Java 코드는 HTTP, application/domain, persistence와 외부 연동 동작을 구현한다.
- resources는 Java 코드가 런타임에 읽는 환경 설정과 DB schema 변경을 제공한다.

## 다른 디렉터리와의 의존 관계

- [`java/com/hiresemble/`](java/com/hiresemble/)의 애플리케이션이 [`resources/application.yml`](resources/application.yml)을 로드한다.
- Flyway는 [`resources/db/migration/`](resources/db/migration/)을 읽어 PostgreSQL schema를 검증·변경한다.
- 빌드와 source set 동작은 [`../../build.gradle.kts`](../../build.gradle.kts)에 의존한다.

## 변경 시 주의사항

- Java 코드와 설정의 이름·profile·환경 변수 계약을 함께 확인한다.
- resources의 파일은 기본적으로 classpath와 패키징 결과에 포함되므로 운영에 불필요한 내용이나 비밀값을 두지 않는다.
- 새로운 업무 영역은 `com.hiresemble` 아래에 도메인 책임 기준으로 추가한다.

## 관련 규칙 및 문서

- [상위 소스 루트](../index.md)
- [Spring 백엔드 개발 규칙](../../../docs/agent-rules/backend-development.md)
- [공통 응답·예외 처리 규칙](../../../docs/agent-rules/backend-response-exception.md)
- [운영 소스 진행 상황](progress.md)
