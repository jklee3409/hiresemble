# Progress

## Overview

Gate 2 공개 GitHub source의 owner-scoped query, mutation, focused Run 표시 기반을 관리한다.

## [2026-08-08] Session Summary (GitHub Source query·mutation·Run monitor)

- What was done:
  - source list/detail/repository key, server query, create/selection/refresh/delete mutation, 상태·URL presentation과 focused Agent Run monitor를 구현했다.
  - 같은 pending action의 idempotency key 재사용, 성공·입력 identity 변경 시 교체, delete stream 종료와 source/experience cache 정리를 검증했다.
- Key decisions:
  - mutation retry는 모두 false이고 409는 자동 재시도하지 않는다. refresh unchanged는 source snapshot만 갱신하며 changed run만 monitor한다.
- Issues encountered:
  - None.
- Validation:
  - `src/features/github` 단위·component test와 전체 Frontend 80 files/369 tests 통과.
- Next steps:
  - None.
