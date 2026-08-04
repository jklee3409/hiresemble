# Progress

## Overview

P5 Job domain과 URL canonicalization 단위 테스트가 구현됐다.

## [2026-08-04] Session Summary (Job fit coverage rubric 회귀)

- What was done:
  - mixed/all UNKNOWN의 분모 제외, coverage와 nullable fit score 단위 테스트를 추가했다.
- Key decisions:
  - MISSING은 평가 완료 0점으로 coverage에 포함하고 UNKNOWN만 제외한다.
- Issues encountered:
  - decimal residual 때문에 mixed 결과는 50.01처럼 criterion별 반올림 합으로 계산된다.
- Validation:
  - `JobFitScoringPolicyTest` 통과.
- Next steps:
  - None.

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
