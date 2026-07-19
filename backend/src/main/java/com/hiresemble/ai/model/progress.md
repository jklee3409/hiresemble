# Progress

## Overview

P3 quality/tier 분리와 disabled provider routing이 구현됐다.

## [2026-07-19] Session Summary (Model Router 품질·승격 정책 구현)

- What was done:
  - ECONOMY·BALANCED·HIGH_QUALITY와 LOW_COST·BALANCED·HIGH_QUALITY tier를 분리했다.
  - structured failure에서 LOW_COST→BALANCED 한 번만 승격하고 disabled provider를 안전하게 거부한다.

- Key decisions:
  - HIGH_QUALITY는 세 조건과 workflow allowlist를 모두 통과해야 한다.

- Issues encountered:
  - None.

- Validation:
  - router unit test 3개가 통과했다.

- Next steps:
  - model ID와 availability는 immutable DB policy adapter에서 공급한다.
