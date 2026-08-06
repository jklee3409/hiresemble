# Progress

## Overview

P3 ModelRouter policy tests가 구현됐다.

## [2026-08-06] Session Summary (OpenAI model catalog·exact routing 검증)

- What was done: 선택 가능한 model ID 10개, 추천값, allowlist와 자기소개서 v4 exact route 회귀를 추가했다.
- Key decisions: legacy 품질 routing 테스트는 다른 workflow 호환성 검증으로 유지한다.
- Issues encountered: None.
- Validation: Backend 전체 `check` 578 tests 통과.
- Next steps: catalog 변경 시 공식 ID와 가격 version을 함께 검증한다.

## [2026-07-19] Session Summary (Model Router 검증)

- What was done:
  - ECONOMY 승격, HIGH_QUALITY gate, disabled provider를 검증했다.

- Key decisions:
  - 승격은 attempt를 소비하며 HIGH_QUALITY는 자동 승격하지 않는다.

- Issues encountered:
  - None.

- Validation:
  - 3 tests가 통과했다.

- Next steps:
  - DB policy adapter가 추가되면 version selection integration test를 추가한다.
