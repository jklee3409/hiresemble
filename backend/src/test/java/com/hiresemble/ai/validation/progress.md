# Progress

## Overview

P3 structured output validation 순서·분류와 OpenAI strict request schema 전수 검사가 구현됐다.

## [2026-08-05] Session Summary (Cover Letter v1/v2 strict schema registry)

- What was done:
  - canonical v2와 durable v1 Cover Letter Chat 단계 모두 중앙 strict schema registry에 존재하는지 검사 범위를 확장했다.
- Key decisions:
  - non-canonical이라도 실제 실행 가능한 durable v1 Chat prompt는 strict registry에서 제외하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - OpenAI strict schema compatibility 전체 parameterized test와 Backend check 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Requirements source v6 최소 schema)

- What was done:
  - 실제 registry schema의 requirement item이 sourceBlockId·sourceText·sourceOrdinal만 소유하고 sourceSection/sourceLocation이 없음을 고정했다.
- Key decisions:
  - property 순서가 아니라 정확한 field 집합과 strict schema 호환성을 검증한다.
- Issues encountered:
  - 최초 assertion이 schema generator의 정렬 순서를 불필요하게 고정해 field 집합 검증으로 교정했다.
- Validation:
  - `JobAnalysisStrictStructuredOutputContractTest`와 전체 `check` 통과.
- Next steps:
  - None.

## [2026-08-03] Session Summary (requirements source v4 strict schema)

- What was done:
  - sourceSection/sourceLocation nullable union과 sourceText/sourceOrdinal required, canonical·reuse 필드 부재를 실제 registry schema에서 검증했다.
- Key decisions:
  - strict JSON Schema subset validator를 그대로 사용한다.
- Issues encountered:
  - Jackson 3 API의 field 이름 조회를 `propertyNames()`로 맞췄다.
- Validation:
  - Job Analysis strict contract와 전체 check 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Job Analysis 실제 registry schema 회귀)

- What was done:
  - Job Analysis 전용 strict contract test를 추가해 세 최종 registry schema의 서버 필드 부재와 required nullable union을 검증했다.
- Key decisions:
  - `sourceLocation|missingReason`만 `string|null`, explanation·analysisSummary 등 필수 모델 필드는 non-null string으로 고정한다.
- Issues encountered:
  - None.
- Validation:
  - `*StrictStructuredOutput*`, `*OpenAiStrictSchema*`는 통과했다. 전체 Backend check는 범위 밖 Object Deletion Outbox 2건 실패로 미통과했다.
- Next steps:
  - None.

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
