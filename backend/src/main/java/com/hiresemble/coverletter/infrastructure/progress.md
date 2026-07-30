# Progress

## Overview

P7 V8 schema를 사용하는 owner-scoped JDBC store와 generation·verification 비용 설정이 구현됐다.

## [2026-07-30] Session Summary (P7 PostgreSQL store)

- What was done:
  - active cardinality, 질문 order, current answer, provenance, immutable verification, acknowledgement와 lifecycle 조건부 SQL을 구현했다.
- Key decisions:
  - owner·active·version 조건을 모든 aggregate/child 조회와 mutation에 포함하고 DB 제약과 application CAS를 함께 사용한다.
- Issues encountered:
  - 없음.
- Validation:
  - repository·migration negative/upgrade, application 통합과 actual P7 DB assertions가 통과했다.
- Next steps:
  - None.
