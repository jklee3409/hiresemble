# Progress

## Overview

P3 canonical Run·Step enum과 전이·projection 불변식이 구현됐다.

## [2026-07-19] Session Summary (Agent Run과 Step 상태 machine 구현)

- What was done:
  - Run 7개, Step 9개 상태와 모든 허용·금지 전이를 구현했다.
  - terminal 불변, 최대 Step attempt 3, stateVersion 증가와 retryable/cancellable 계산을 고정했다.

- Key decisions:
  - cancel request timestamp와 terminal CANCELLED를 분리한다.
  - WAITING_USER resume은 같은 Run·Step attempt를 사용하고 terminal retry는 successor Run attempt를 증가시킨다.

- Issues encountered:
  - None.

- Validation:
  - exhaustive domain test 136개를 포함한 Backend 전체 검증이 통과했다.

- Next steps:
  - 새 상태를 임의 추가하지 않고 계약 변경이 필요하면 명세 결정을 먼저 수행한다.
