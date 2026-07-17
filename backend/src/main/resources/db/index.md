# DB 리소스 안내

## 디렉터리 목적

`backend/src/main/resources/db/`는 Spring/Flyway가 사용하는 데이터베이스 관련 classpath 리소스를 구분하는 상위 경계다.

## 주요 파일 및 하위 디렉터리

| 경로                       | 역할                                      |
| -------------------------- | ----------------------------------------- |
| [`migration/`](migration/) | 버전이 부여된 PostgreSQL schema migration |

## 구성 요소 역할

- 이 계층은 DB 리소스의 namespace를 제공한다.
- 실제 schema 변경은 `migration` 하위의 SQL 파일이 소유한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`../application.yml`](../application.yml)의 Flyway 설정이 기본 위치 `classpath:db/migration`을 사용한다.
- PostgreSQL/pgvector 서비스는 루트 [`../../../../../compose.yaml`](../../../../../compose.yaml)이 제공한다.
- 목표 schema는 [`../../../../../docs/spec/db.md`](../../../../../docs/spec/db.md)를 기준으로 한다.

## 변경 시 주의사항

- Flyway가 인식하지 않는 임의 SQL 실행 경로를 추가하지 않는다.
- seed, fixture, 운영 migration의 책임이 달라지면 별도 위치와 실행 정책을 명시한다.
- 이 디렉터리의 Markdown도 classpath 리소스에 포함될 수 있으므로 상세 이슈는 [`../progress.md`](../progress.md)를 확인한다.

## 관련 규칙 및 문서

- [상위 리소스 안내](../index.md)
- [Spring 백엔드 개발 규칙](../../../../../docs/agent-rules/backend-development.md)
- [DB 명세](../../../../../docs/spec/db.md)
- [DB 리소스 진행 상황](progress.md)
- [Migration 안내](migration/index.md)
