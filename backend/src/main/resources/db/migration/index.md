# Flyway migration 안내

## 디렉터리 목적

`backend/src/main/resources/db/migration/`은 PostgreSQL schema의 순차적 변경 이력을 Flyway naming 규칙으로 관리한다.

## 주요 파일 및 하위 디렉터리

| 경로                                                                                                               | 역할                                                 |
| ------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| [`V1__enable_extensions.sql`](V1__enable_extensions.sql)                                                           | pgvector의 `vector` PostgreSQL extension 활성화      |
| [`V2__create_identity_session_idempotency.sql`](V2__create_identity_session_idempotency.sql)                       | users·기본 profile·Spring Session·idempotency schema |
| [`V3__create_structured_profiles_and_direct_evidence.sql`](V3__create_structured_profiles_and_direct_evidence.sql) | P2 기본·구조화 프로필과 직접 근거 schema·불변식      |

현재 하위 디렉터리는 없다. 향후 migration도 특별한 분리 요구가 없으면 이 위치에 순차적으로 둔다.

## 구성 요소 역할

- V1은 `CREATE EXTENSION IF NOT EXISTS vector`만 실행하며 P1에서도 byte 단위로 보존한다.
- V2는 P1에 필요한 users, user_profiles, Spring Session과 idempotency_records만 추가한다.
- V3는 `user_profiles` JSON·owner 제약을 보강하고 다섯 구조화 source와 `profile_evidence`를 추가한다.
- 문서 aggregate와 `documents` table·복합 FK는 P4 forward migration으로 남긴다.

## 다른 디렉터리와의 의존 관계

- [`../../application.yml`](../../application.yml)이 Flyway를 활성화하고 JPA schema 검증보다 먼저 migration을 적용한다.
- 로컬 PostgreSQL image는 루트 [`../../../../../../compose.yaml`](../../../../../../compose.yaml)에서 pgvector를 제공한다.
- migration 설계의 기준은 [`../../../../../../docs/spec/db.md`](../../../../../../docs/spec/db.md)다.

## 변경 시 주의사항

- 한 번 적용된 migration은 내용이나 순서를 수정·삭제하지 않고 새 버전 파일로 보정한다.
- 빈 DB 적용과 기존 DB upgrade를 모두 검증하고 destructive 변경에는 명시적 migration·복구 계획을 둔다.
- `vector` extension 생성 권한과 대상 PostgreSQL 환경의 extension 지원 여부를 확인한다.
- 이 디렉터리의 Markdown도 classpath에 포함될 수 있으며 패키징 이슈는 [`../../progress.md`](../../progress.md)에서 추적한다.

## 관련 규칙 및 문서

- [DB 리소스 안내](../index.md)
- [Spring 백엔드 개발 규칙](../../../../../../docs/agent-rules/backend-development.md)
- [DB 명세](../../../../../../docs/spec/db.md)
- [Migration 진행 상황](progress.md)
