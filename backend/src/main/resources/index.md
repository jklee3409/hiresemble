# 백엔드 리소스 안내

## 디렉터리 목적

`backend/src/main/resources/`는 Spring Boot가 classpath에서 읽는 운영 설정과 Flyway DB migration을 관리한다.

## 주요 파일 및 하위 디렉터리

| 경로                                 | 역할                                                                      |
| ------------------------------------ | ------------------------------------------------------------------------- |
| [`application.yml`](application.yml) | datasource, Flyway/JPA, Session·AI·Document·Object Storage와 OpenAPI 설정 |
| [`db/`](db/)                         | DB 관리 리소스의 상위 경계                                                |
| [`db/migration/`](db/migration/)     | Flyway가 순서대로 적용하는 SQL migration                                  |

## 구성 요소 역할

- `application.yml`은 선택적 `.env`를 읽고 Session·Cookie, versioned HMAC, bounded Agent worker와 Document parser·outbox·Object Storage override를 정의한다.
- `db/migration`은 PostgreSQL/pgvector schema 변경의 유일한 버전 관리 경로다.
- 이 디렉터리의 파일은 Gradle `main` resources로 처리되어 런타임 classpath에 놓인다.

## 다른 디렉터리와의 의존 관계

- [`../java/com/hiresemble/`](../java/com/hiresemble/)의 Spring component가 설정을 소비한다.
- datasource와 Object Storage 기본 endpoint는 루트 [`../../../../compose.yaml`](../../../../compose.yaml)의 로컬 서비스와 연결된다.
- DB 구조 계약은 [`../../../../docs/spec/db.md`](../../../../docs/spec/db.md)에 의존한다.

## 변경 시 주의사항

- 비밀값과 운영 자격 증명을 저장하지 않고 환경 변수 이름과 안전한 기본값만 관리한다.
- JPA `ddl-auto=validate`, Flyway 활성화와 AI provider 기본 비활성화 동작을 임의로 깨지 않는다.
- 이 계층의 `index.md`, `progress.md`도 기본 resource 처리에서는 classpath와 패키징 결과에 포함될 수 있다. 운영 패키징 전 Gradle exclusion 또는 문서 위치 정책을 검토한다.
- 설정 key를 변경하면 `.env.example`, Compose, CI, README와 Java binding 사용처를 함께 확인한다.

## 관련 규칙 및 문서

- [Spring 백엔드 개발 규칙](../../../../docs/agent-rules/backend-development.md)
- [인프라 규칙](../../../../docs/agent-rules/infrastructure.md)
- [기술 스택 명세](../../../../docs/spec/tech_stack.md)
- [리소스 진행 상황](progress.md)
- [DB 리소스 안내](db/index.md)
