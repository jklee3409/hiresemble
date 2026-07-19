# Progress

## Overview

- Flyway 기본 경로인 `migration` 하위 디렉터리만 존재한다.
- V1 extension, P1 identity·Session·idempotency V2와 P2 profile·direct evidence V3가 있으며 별도 운영 seed·fixture 리소스는 없다.

## [2026-07-19] Session Summary (P2 V3 DB 리소스 추가)

- What was done:
  - migration 경로에 V3 profile schema를 추가했다.

- Key decisions:
  - 운영 seed·fixture나 임의 SQL 실행 경로는 추가하지 않는다.

- Issues encountered:
  - None

- Validation:
  - Flyway migration·hash·upgrade 테스트와 Compose 설정 검증이 통과했다.

- Next steps:
  - 후속 schema 변경은 versioned migration만 사용한다.

## [2026-07-19] Session Summary (P1 Flyway DB 리소스 확장)

- What was done:
  - Flyway 기본 경로에 `V2__create_identity_session_idempotency.sql`을 추가했다.
  - 운영 migration과 test-source fixture를 분리하고 P1 밖 table을 생성하지 않았다.

- Key decisions:
  - 적용 이력이 있는 V1은 수정하지 않고 모든 schema 변경을 새 V2 파일로 표현했다.
  - JDBC Session runtime 자동 생성을 사용하지 않고 application과 Flyway의 schema ownership을 단일화했다.

- Issues encountered:
  - V1-only upgrade 경로와 빈 DB 전체 적용을 서로 다른 Testcontainers scenario로 검증해야 했다.
  - DB 리소스 Markdown의 classpath 포함은 기존 상위 추적 이슈로 남아 있다.

- Validation:
  - migration 3개 테스트가 빈 DB V1→V2, V1-only upgrade, constraint·index·unique와 JPA validate를 확인하고 통과했다.
  - V1 Git blob과 SHA-256 hash가 작업 전 값과 동일함을 확인했다.

- Next steps:
  - P2 schema는 승인된 use case별 새 버전 migration으로만 추가한다.
  - 운영 적용 전 backup·rollback 전략과 실제 배포 DB 권한을 별도로 검증한다.

## [2026-07-17] Session Summary (Flyway DB 리소스 경계 구성)

- What was done:
  - 당시 구현 상태:
    - Flyway 기본 경로인 `migration` 하위 디렉터리만 존재한다.
    - 업무 schema나 별도 seed/fixture 리소스는 없다.
  - 완료된 작업:
    - PostgreSQL schema 변경 파일을 `db/migration` 표준 classpath 위치에 배치했다.
    - DB 리소스와 migration 계층의 책임·상태 문서를 생성했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 SQL migration 작업은 없다. DB 리소스 문서 체계 초기화는 완료됐다.

- Key decisions:
  - Flyway 기본 위치를 유지하고 schema 변경을 애플리케이션 기동과 일관되게 관리한다.
  - 문서만을 위해 별도 DB 리소스 유형을 만들지 않는다.

- Issues encountered:
  - 현재 업무 table schema가 없어 도메인 데이터는 저장할 수 없다.
  - 이 계층의 Markdown도 resource 처리 결과에 포함되는 문제는 상위 [`../progress.md`](../progress.md)에 기록되어 있다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 성공했다.
  - `rg --files backend/src/main/resources/db`로 migration SQL과 두 계층의 추적 문서만 존재함을 확인했다.

- Next steps:
  - 도메인 구현과 함께 DB 명세를 검토하고 새 버전 migration을 순차적으로 추가한다.
  - seed나 테스트 전용 데이터가 필요하면 운영 migration과 분리된 실행 정책을 먼저 정한다.
