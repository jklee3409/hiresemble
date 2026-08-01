# Progress

## Overview

P3 retryable/non-retryable AI 오류 분류와 안전한 projection이 구현됐다.

## [2026-08-01] Session Summary (failure별 automatic attempt 계약)

- What was done:
  - AI execution failure에 validation phase, 최대 automatic attempt와 value-free correction guidance를 추가했다.
- Key decisions:
  - transient는 3, deterministic structured는 1, repairable semantic은 2 attempt 상한이다.
- Issues encountered:
  - None.
- Validation:
  - validator·orchestrator 통합 회귀와 전체 459 tests 통과.
- Next steps:
  - 새 structured reason은 stable 100자 safe code 규칙을 유지한다.

## [2026-08-01] Session Summary (실패 Provider usage 전달)

- What was done:
  - `AiExecutionException`이 secret·raw response 없이 이미 발생한 usage row를 전달하도록 확장했다.
- Key decisions:
  - Orchestrator가 실패·재시도 판정 전에 incurred usage를 먼저 기록한다.
- Issues encountered:
  - None.
- Validation:
  - structured output 실패와 retry accounting을 포함한 Backend 420 tests가 통과했다.
- Next steps:
  - None.

## [2026-07-19] Session Summary (AI 오류 분류 경계 구현)

- What was done:
  - provider transient·structured 오류와 owner·validation·configuration·budget·cancel 분류를 분리했다.

- Key decisions:
  - 최초 포함 최대 3 attempt이며 비재시도 분류는 자동 반복하지 않는다.

- Issues encountered:
  - None.

- Validation:
  - transient/exhausted와 structured/domain validation 테스트가 통과했다.

- Next steps:
  - 실제 adapter가 HTTP/network 오류를 이 분류로 안전하게 변환한다.
