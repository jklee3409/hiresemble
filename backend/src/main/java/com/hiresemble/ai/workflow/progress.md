# Progress

## Overview

P3 canonical 8개 workflow definition과 executable contribution 분리가 구현됐다.

## [2026-07-19] Session Summary (Workflow Registry 계약 구현)

- What was done:
  - workflow version, 고정 step, fan-out·schema·tool·call·retry·weight metadata를 등록했다.
  - test-only 3-step contribution만 실행 가능하게 만들었다.

- Key decisions:
  - canonical definition은 P0 계약을 설명하지만 P4 이전 실행 가능성을 의미하지 않는다.

- Issues encountered:
  - None.

- Validation:
  - canonical coverage, duplicate, weight와 executable sequence unit test 3개가 통과했다.

- Next steps:
  - 각 phase가 실제 domain port와 함께 contribution을 등록한다.
