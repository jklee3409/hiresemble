# Progress

## Overview

- `V1__enable_extensions.sql`은 pgvector `vector` extension을 idempotent하게 활성화한다.
- `V2__create_identity_session_idempotency.sql`은 P1의 `users`, 기본 `user_profiles`, JDBC Session, `idempotency_records`를 생성한다.
- `V3__create_structured_profiles_and_direct_evidence.sql`은 P2의 프로필 5종과 direct evidence 및 DB 불변식을 생성한다.
- `V4__create_agent_runtime_and_ai_budget.sql`은 P3 Run·Step과 AI policy·price·budget·usage 11개 table을 생성한다.
- `V5__create_documents_evidence_and_storage_outbox.sql`은 P4 문서·text·chunk·Object outbox·typed Run link와 evidence document FK를 생성한다.
- 공고·자기소개서·면접 등 P5 이후 table은 구현하지 않았다.

## [2026-07-19] Session Summary (P4 Document·evidence·storage V5 migration)

- What was done:
  - `documents`, `document_texts`, `document_chunks`, `object_deletion_outbox`, `agent_run_resource_links`와 owner FK를 단일 V5로 추가했다.
  - profile evidence·자격증·어학·수상 document FK와 active embedding/model policy metadata를 forward migration했다.

- Key decisions:
  - typed link를 authoritative 원천으로 선택하고 V4 legacy resource pair parity를 deferred trigger로 강제했다.
  - embedding은 `vector(1536)`·cosine·generation 1이며 초기 HNSW와 business checksum unique는 만들지 않았다.

- Issues encountered:
  - V4의 prospective nullable document reference는 owner FK를 만족할 실제 row가 없으므로 structured hint는 null, legacy DOCUMENT_CHUNK는 tombstone으로 안전 이관했다.

- Validation:
  - 빈 DB V1→V5와 V1/V2/V3/V4-only upgrade, CHECK·owner FK·typed link·outbox active unique·P5 table 부재가 통과했다.
  - V1–V4 SHA-256은 각각 `9e9b2cfe…191c`, `c43f2d9a…21dcf`, `6ac81b6a…4347`, `706db49c…e01f`로 불변이다.
  - 최종 read-only Validator가 V5 범위와 기존 migration 불변을 `PASS`로 판정했다.

- Next steps:
  - P5 이후 schema는 V5를 수정하지 않고 다음 forward migration으로 추가한다.

## [2026-07-19] Session Summary (P3 Agent runtime·AI budget V4 migration)

- What was done:
  - `agent_runs`, `agent_steps`, immutable policy·price, preference, ledger·reservation·usage 11개 table과 제약·trigger를 추가했다.
  - 기존 사용자 preference를 ECONOMY·1.00 USD·high quality false로 backfill했다.

- Key decisions:
  - V1·V2·V3는 수정하지 않고 V4 단일 forward migration만 추가했다.
  - 실제 provider 가격은 seed하지 않고 Fake 가격은 test fixture에서만 만든다.
  - document/job typed resource link와 FK는 해당 phase로 이관한다.

- Issues encountered:
  - 기존 개발 DB Flyway history 불일치를 수정하지 않고 격리 PostgreSQL upgrade만 수행했다.

- Validation:
  - 빈 DB와 V1/V2/V3-only upgrade, V4의 71개 CHECK 설치, owner FK·unique·immutability·음수 거부·P4 table 부재가 통과했다.
  - 최종 read-only Validator가 V1·V2·V3 불변과 단일 V4 범위를 `PASS`로 판정했다.

- Next steps:
  - P4에서 documents와 typed Agent Run link를 새 migration으로 추가한다.

## [2026-07-19] Session Summary (P2 구조화 프로필·direct evidence V3 migration)

- What was done:
  - `user_profiles` JSON·owner 제약을 보강하고 educations·certifications·language_scores·awards·careers·profile_evidence를 추가했다.
  - 배열 canonical helper, 대표 학력 partial unique, 날짜·GPA·metadata와 source/evidence owner·1:1 trigger를 추가했다.

- Key decisions:
  - V1·V2는 byte 단위로 보존하고 V3 forward migration만 추가했다.
  - nullable document column은 유지하되 documents table과 복합 FK는 P4로 이관했다.

- Issues encountered:
  - 로컬 기존 DB는 Flyway 이력과 Session table이 불일치해 실제 E2E는 별도 빈 DB에서 수행했다.

- Validation:
  - 빈 DB·V1-only·V2-only upgrade와 모든 P2 CHECK·unique·cross-user·rollback 경계를 PostgreSQL 18에서 통과했다.

- Next steps:
  - P4 document migration에서 owner 복합 FK를 새 version으로 추가한다.

## [2026-07-19] Session Summary (P1 identity·Session·idempotency V2 migration)

- What was done:
  - 사용자 UUID·정규화 email unique·역할 USER·상태 ACTIVE와 기본 프로필을 위한 schema를 추가했다.
  - Spring Session JDBC table·index와 scope/state/HMAC request hash/response replay/24시간 TTL을 저장하는 idempotency table을 추가했다.

- Key decisions:
  - V1은 변경하지 않고 V2에서 필요한 enum constraint·foreign key·unique·조회 index를 선언했다.
  - idempotency에는 요청 원문·비밀번호를 저장하지 않고 hash와 replay용 status·JSON만 저장한다.
  - P1 제외 table과 미래 migration placeholder를 만들지 않았다.

- Issues encountered:
  - PostgreSQL 시간 값의 JDBC 타입 추론은 repository에서 UTC `OffsetDateTime`으로 명시해 해결했다.
  - extension 권한이 필요한 V1을 포함하므로 pgvector PostgreSQL Testcontainers image로 실제 적용했다.

- Validation:
  - 빈 DB V1부터 전체 적용, V1만 적용된 DB의 V2 upgrade, schema constraint·index·unique와 JPA validate가 모두 통과했다.
  - V1은 Git blob `0aa0fc22558644b6dec3f8f24e90d6523c8d12a6`, SHA-256 `9e9b2cfec47519f49ee73cb533c459e22f8ca54fe5ba1cbec59f3d5883fe191c`로 작업 전과 동일하다.

- Next steps:
  - P2 실제 idempotent endpoint에서는 validation·인증·소유권 통과 뒤에만 record를 생성하도록 현재 service를 사용한다.
  - 이후 schema는 기존 migration을 수정하지 않고 새 Flyway version으로 확장한다.

## [2026-07-17] Session Summary (pgvector 확장 V1 migration 구성)

- What was done:
  - 당시 구현 상태:
    - `V1__enable_extensions.sql` 하나만 존재하며 pgvector `vector` extension을 idempotent하게 활성화한다.
    - 사용자, 문서, 공고, 자기소개서, 면접과 Agent Run 업무 table은 아직 구현되지 않았다.
  - 완료된 작업:
    - pgvector 기능을 후속 schema에서 사용할 수 있도록 첫 Flyway migration을 추가했다.
    - migration 디렉터리의 목적, 불변 규칙과 현재 상태를 추적하는 문서를 생성했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 migration 작업은 없다. 문서 체계 초기화는 완료됐고 기존 V1 SQL은 변경하지 않았다.

- Key decisions:
  - 기존 V1은 적용 이력이 생길 수 있는 migration으로 간주해 수정하지 않는다.
  - schema 변경은 Hibernate 자동 생성이 아니라 새로운 Flyway 버전 파일로만 수행한다.
  - 로컬 DB는 pgvector 포함 PostgreSQL image를 사용한다.

- Issues encountered:
  - V1은 대상 DB에 extension 생성 권한과 pgvector 설치가 필요하다.
  - 업무 table이 전혀 없어 현재 migration만으로는 제품 데이터를 저장할 수 없다.
  - 이 위치의 추적 Markdown도 기본 Gradle resource 처리에서 classpath에 복사된다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 성공했다.
  - `V1__enable_extensions.sql`이 `CREATE EXTENSION IF NOT EXISTS vector;`만 포함함을 직접 확인했다.
  - 이번 문서 작업에서는 빈 DB와 기존 DB upgrade migration 실행을 새로 수행하지 않았다.

- Next steps:
  - [`../../../../../../docs/spec/db.md`](../../../../../../docs/spec/db.md)의 우선순위에 따라 V2 이상의 업무 schema migration을 설계한다.
  - 새 migration마다 빈 DB 적용, 기존 DB upgrade, JPA validate와 DB constraint를 검증한다.
  - pgvector column에는 embedding model/dimension 추적과 사용자 격리 index 전략을 함께 설계한다.
