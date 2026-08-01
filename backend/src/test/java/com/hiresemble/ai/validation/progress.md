# Progress

## Overview

P3 structured output validation 순서·분류와 OpenAI strict request schema 전수 검사가 구현됐다.

## [2026-08-01] Session Summary (parse부터 domain까지 phase 검증)

- What was done:
  - invalid JSON, shape, binding, record, workflow, domain phase와 generic fallback·safe 비노출을 각각 검증했다.
- Key decisions:
  - Jackson property description이 실제 strict schema에 반영되고 unsupported keyword를 만들지 않는지 함께 고정한다.
- Issues encountered:
  - Swagger description annotation은 converter에서 unsupported `default`를 만들 수 있어 사용하지 않았다.
- Validation:
  - validator/schema registry focused와 전체 check 통과.
- Next steps:
  - strict subset 변경은 공식 문서와 runtime generator 양쪽 근거로 갱신한다.

## [2026-08-01] Session Summary (strict schema 전수·completeness 회귀)

- What was done:
  - bare arbitrary object 실패 fixture, 등록 output 자동 parameterized 검사, workflow/prompt completeness, nullable warning과 deterministic fingerprint를 검증했다.
- Key decisions:
  - 수기 output 목록 대신 canonical registry에서 자동 열거한다.
- Issues encountered:
  - 수정 전 evidence metadata와 warning schema가 회귀 test에서 재현 가능하게 실패했다.
- Validation:
  - 현재 14개 Chat output schema가 중앙 validator를 통과했다.
- Next steps:
  - 새 output type이 추가되면 동일 test가 자동 포함한다.

## [2026-07-19] Session Summary (Structured Output 검증 테스트)

- What was done:
  - 5단계 순서와 structured retry/domain non-retry를 검증했다.

- Key decisions:
  - raw validation detail 대신 safe error만 assert한다.

- Issues encountered:
  - None.

- Validation:
  - 2 tests가 통과했다.

- Next steps:
  - workflow별 schema test를 해당 phase에서 추가한다.
