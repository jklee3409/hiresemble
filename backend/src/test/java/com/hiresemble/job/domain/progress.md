# Progress

## Overview

P5 Job domain과 URL canonicalization 단위 테스트가 구현됐다.

## [2026-07-27] Session Summary (상태·canonical URL 회귀 고정)

- What was done:
  - 모든 상태 전이와 canonical 동등성·reserved escape 비동등성을 검증했다.
- Key decisions:
  - 실제 URL 차이를 false duplicate로 축약하지 않는 assertion을 포함한다.
- Issues encountered:
  - 초기 validator가 `%2F`와 `/`, query `+`와 `%20` 축약을 발견했다.
- Validation:
  - Job domain/canonicalizer 단위 테스트가 전체 Backend check에서 통과했다.
- Next steps:
  - 없음.
