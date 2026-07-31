# Progress

## Overview

V12 조사 schema의 owner-scoped JDBC adapter가 구현되어 있다.

## [2026-07-31] Session Summary (조사 PostgreSQL 저장소)

- What was done:
  - run·source 조회와 topic/source provenance atomic 저장을 추가했다.
- Key decisions:
  - canonical URL uniqueness와 owner 복합 FK를 DB 최종 방어선으로 유지한다.
- Issues encountered:
  - nullable enum parameter에 명시적 PostgreSQL cast가 필요했다.
- Validation:
  - 빈 DB·V11 upgrade·cross-user FK·source dedupe 통합 테스트가 통과했다.
- Next steps:
  - None.
