# Progress

## Overview

Flyway V1~V3 보존과 V4 P3 schema의 빈 DB·upgrade 경로를 실제 PostgreSQL에서 검증한다.

## [2026-07-19] Session Summary (P3 V4 migration·불변식 검증)

- What was done:
  - 빈 DB V1→V4와 V1/V2/V3-only upgrade, 11개 table과 P4 table 부재를 검증했다.
  - V1~V3 Git blob 기준 SHA-256과 owner·retry·step·ledger·preference unique를 고정했다.
  - V4에 선언된 71개 CHECK constraint가 실제 PostgreSQL schema에 모두 설치되는지 대조했다.

- Key decisions:
  - 실제 PostgreSQL과 test fixture price version을 사용한다.

- Issues encountered:
  - None.

- Validation:
  - P3 migration 8 tests가 전체 check에서 통과했다.

- Next steps:
  - V5 이후에도 V1~V4를 수정하지 않는다.

## [2026-07-19] Session Summary (P2 V3 migration·불변식 검증)

- What was done:
  - 빈 DB V1→V2→V3, V1-only·V2-only upgrade와 P2 table·constraint·trigger 검증을 추가했다.
  - V1·V2 Git blob 기준 hash 불변과 source/evidence rollback을 고정했다.

- Key decisions:
  - JSON 배열·대표 학력·날짜·GPA·metadata·owner 불변식은 H2가 아닌 PostgreSQL에서 검증한다.

- Issues encountered:
  - 초기 assertion 두 개가 PostgreSQL의 먼저 발생한 constraint message와 달라 테스트 기대값만 실제 제약 순서에 맞췄다.

- Validation:
  - Backend 전체 check에서 P1·P2 migration test가 모두 통과했다.

- Next steps:
  - 적용 이력 V1~V3는 수정하지 않고 후속 변경은 V4 이후로 추가한다.

## [2026-07-19] Session Summary (P1 Flyway migration 검증 구현)

- What was done:
  - 빈 DB 전체 적용, V1 target 적용 뒤 upgrade와 table·constraint·index 범위를 검증했다.

- Key decisions:
  - pgvector PostgreSQL 18 image를 사용하고 account_deletion_tasks·P2 table 부재도 확인한다.

- Issues encountered:
  - None

- Validation:
  - P1MigrationTest 3개와 V1 SHA-256 고정 assertion이 통과했다.

- Next steps:
  - 새 migration마다 동일한 빈 DB·직전 version upgrade 검증을 추가한다.
