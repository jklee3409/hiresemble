# Progress

## Overview

P1·P2 운영 코드와 분리된 JUnit·MockMvc·Testcontainers 검증 source set을 관리한다.

## [2026-07-19] Session Summary (P2 profile·migration 테스트 확장)

- What was done:
  - P2 domain·API·migration 테스트와 공유 table cleanup을 추가했다.

- Key decisions:
  - 운영 DB·유료 외부 provider 없이 PostgreSQL Testcontainers를 사용한다.

- Issues encountered:
  - None

- Validation:
  - Backend check 54개 test가 통과했다.

- Next steps:
  - 후속 phase 테스트는 해당 운영 code와 함께 추가한다.

## [2026-07-19] Session Summary (P1 백엔드 테스트 source set 구성)

- What was done:
  - 인증, 공통 오류, idempotency와 migration 테스트 계층을 처음 추가했다.

- Key decisions:
  - Repository 통합은 PostgreSQL 18/pgvector Testcontainers를 사용하고 Spring Session·Flyway·JPA validate를 함께 검증한다.

- Issues encountered:
  - 초기 Jackson 2/3 bean 차이와 CSRF 형식, JDBC 시간 binding 실패를 테스트로 발견하고 production 구현을 보정했다.

- Validation:
  - gradlew check에서 26 tests, 0 failures·errors·skips로 통과했다.

- Next steps:
  - 새 기능은 가장 가까운 단위 test와 PostgreSQL 경계 test를 함께 추가한다.
