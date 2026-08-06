# Progress

## Overview

Backend atomic budget port와 immutable 가격 catalog를 사용하는 호출별 비용 guard가 구현됐다.

## [2026-08-06] Session Summary (가격 catalog 기반 호출별 최악 비용 예약)

- What was done: chat token ceiling, embedding input ceiling, web search query 수로 외부 호출 1회의 최악 비용을 산정하는 `PriceCatalogAiCallCostEstimator`를 추가했다.
- Key decisions: 분야별 USD 상수 없이 Agent Run에 고정된 price version만 사용하고 Fake/disabled provider는 0원으로 처리한다.
- Issues encountered: web search는 한 step에서 query별 provider 호출을 수행하므로 payload query 개수만큼 ADVANCED 단가를 보수적으로 예약했다.
- Validation: 구현 중 메인·테스트 소스 컴파일과 budget 통합 테스트가 통과했다. 최종 호출 1회 기준 산정 보정 후 재검증은 요청에 따라 생략했다.
- Next steps: 새 tool gateway 추가 시 estimator unit 계약과 price item을 함께 추가한다.

## [2026-07-19] Session Summary (AI 호출 비용 guard 구현)

- What was done:
  - 다음 호출 coverage top-up과 success settle, failure·waiting·interruption release를 연결했다.

- Key decisions:
  - ledger lock·한도 계산은 persistence port가 소유하고 AI 모듈은 중복 구현하지 않는다.

- Issues encountered:
  - None.

- Validation:
  - zero-cost Fake usage와 orchestration terminal 경로 테스트가 통과했다.

- Next steps:
  - 실제 adapter에서 price item 기반 worst-case estimate를 공급한다.
