# Progress

## Overview

P1~P8 Spring Boot 통합 테스트가 공유하는 PostgreSQL Testcontainer와 V16까지의 table cleanup을 제공한다.

## [2026-08-02] Session Summary (V16 자동 분석 test 격리)

- What was done:
  - 자동 분석 scheduler를 test profile에서 끄고 `job_auto_analysis_requests`를 FK 역순 cleanup에 포함했다.
- Key decisions:
  - 각 통합 테스트가 coordinator를 명시적으로 실행해 race 없는 반복 가능성을 유지한다.
- Issues encountered:
  - None.
- Validation:
  - 자동 분석 통합 suites 반복 실행 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Cover Letter table 격리 지원)

- What was done:
  - acknowledgement·verification·evidence link·answer version·question·cover letter와 typed resource cleanup 순서를 추가했다.
- Key decisions:
  - immutable trigger가 운영 계약을 유지하도록 FK 역순 TRUNCATE test 격리를 사용한다.
- Issues encountered:
  - 없음.
- Validation:
  - Backend 전체 377 tests가 반복 가능한 격리 상태로 통과했다.
- Next steps:
  - 새 migration table은 FK 역순 cleanup에 함께 반영한다.

## [2026-07-27] Session Summary (P5 Job table 격리 지원)

- What was done:
  - Job·Company·status history와 typed resource link cleanup 순서를 공통 PostgreSQL fixture에 추가했다.
- Key decisions:
  - FK 의존 순서를 지키고 테스트마다 사용자 소유 row를 완전히 격리한다.
- Issues encountered:
  - 없음.
- Validation:
  - 전체 Backend 322 tests가 반복 가능한 격리 상태로 통과했다.
- Next steps:
  - P6 table 추가 시 cleanup 순서를 함께 갱신한다.

## [2026-07-19] Session Summary (P4 Document table 격리 cleanup 확장)

- What was done:
  - V5 document·chunk·outbox·typed link와 Fake catalog fixture cleanup 순서를 추가했다.
- Key decisions:
  - 운영·기존 개발 datasource는 사용하지 않는다.
- Issues encountered:
  - immutable policy·price table은 TRUNCATE 기반 test 격리로 정리한다.
- Validation:
  - 전체 287 tests가 반복 가능한 Testcontainers 환경에서 통과했다.
- Next steps:
  - 새 migration table은 FK 역순으로 격리 cleanup에 반영한다.

## [2026-07-19] Session Summary (P3 runtime table cleanup 확장)

- What was done:
  - usage·reservation·ledger·step·run·preference·policy·price table을 FK 역순으로 정리하도록 확장했다.

- Key decisions:
  - 각 test는 격리된 PostgreSQL row를 사용하며 운영·개발 DB를 참조하지 않는다.

- Issues encountered:
  - None.

- Validation:
  - 전체 Backend integration suite가 반복 실행에서 통과했다.

- Next steps:
  - 새 migration table은 FK 역순 cleanup에 함께 추가한다.

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
