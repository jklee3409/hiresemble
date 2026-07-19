# Progress

## Overview

P1 Spring Boot 통합 테스트가 공유하는 PostgreSQL Testcontainer와 table cleanup을 제공한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (PostgreSQL 통합 테스트 기반 구현)

- What was done:
  - pgvector PostgreSQL 18 container와 Spring dynamic properties, P1 table cleanup을 구성했다.

- Key decisions:
  - Spring Session schema는 runtime 생성하지 않고 Flyway V2만 사용한다.

- Issues encountered:
  - None

- Validation:
  - Auth·OpenAPI·idempotency SpringBootTest가 공유 기반에서 통과했다.

- Next steps:
  - P2 table cleanup은 해당 통합 test 도입 시 최소 범위로 확장한다.
