# Progress

## Overview

V12 면접 schema의 JDBC adapter와 비용 설정이 구현되어 있다.

## [2026-07-31] Session Summary (면접 PostgreSQL·비용 설정)

- What was done:
  - question set·question·answer·feedback 저장소와 workflow 비용 속성을 추가했다.
- Key decisions:
  - immutable answer/feedback과 owner 복합 FK를 DB 최종 방어선으로 유지한다.
- Issues encountered:
  - 동적 정렬 SQL 공백 결함은 answer history API 회귀로 고정했다.
- Validation:
  - migration·owner FK·CAS·feedback success-only·history delete 보존 테스트가 통과했다.
- Next steps:
  - None.
