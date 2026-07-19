# Progress

## Overview

P1·P2 Spring Boot 통합 테스트가 공유하는 PostgreSQL Testcontainer와 table cleanup을 제공한다.

## [2026-07-19] Session Summary (P2 table 통합 테스트 cleanup 확장)

- What was done:
  - 다섯 구조화 source와 profile_evidence를 참조 순서에 맞춰 정리하도록 공유 fixture를 확장했다.

- Key decisions:
  - 운영 datasource나 로컬 개발 DB 대신 기존 Testcontainers PostgreSQL 격리를 유지한다.

- Issues encountered:
  - None

- Validation:
  - 전체 Backend check에서 test context 재사용과 데이터 격리가 통과했다.

- Next steps:
  - 후속 table이 실제 생길 때만 cleanup 순서를 갱신한다.

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
