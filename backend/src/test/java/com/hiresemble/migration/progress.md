# Progress

## Overview

Flyway V1 보존과 V2 P1 schema의 빈 DB·upgrade 경로를 실제 PostgreSQL에서 검증한다. 현재 P1 구현과 검증 상태만 기록한다.

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
