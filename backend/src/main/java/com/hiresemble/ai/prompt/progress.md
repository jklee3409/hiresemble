# Progress

## Overview

P3 versioned PromptRegistry에 P4 Document부터 P7 Cover Letter까지 structured prompt metadata가 구현됐다.

## [2026-07-31] Session Summary (문서 학력 근거 추출 금지)

- What was done:
  - Document evidence extraction prompt에 학력·교육 이력 후보를 만들지 않는 instruction을 추가했다.
- Key decisions:
  - prompt만 신뢰하지 않고 application validation과 DB CHECK를 함께 적용한다.
- Issues encountered:
  - None.
- Validation:
  - Document workflow 통합 12 tests와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 생성·검증 structured prompt)

- What was done:
  - question plan/analysis, evidence allocation, answer/fact-check와 verification fact/requirement/aggregate record schema를 등록했다.
- Key decisions:
  - evidence ID는 typed field로 전달하고 모델이 source·createdBy·finalization을 지정하지 못하게 한다.
- Issues encountered:
  - 없음.
- Validation:
  - schema version·invalid output·timeout/retry와 workflow contract 테스트가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 분석 prompt 계약)

- What was done:
  - requirement·eligibility·matching record schema, call cap와 untrusted job content instruction 경계를 등록했다.
- Key decisions:
  - 모델에 final score·owner·persist 권한을 주지 않고 공고 내부 instruction을 실행하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - prompt metadata contract와 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (Job Posting Extraction prompt 계약 추가)

- What was done:
  - P5 추출 prompt version·output schema와 호출/token 제한을 registry에 추가했다.
- Key decisions:
  - Chat 호출은 추출 step에만 attempt당 1회 허용하고 tool 호출은 허용하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - schema version·structured invalid·provider timeout 분류 테스트가 통과했다.
- Next steps:
  - P6 prompt는 이번 registry에 선행 등록하지 않는다.

## [2026-07-19] Session Summary (Document evidence structured prompt 추가)

- What was done:
  - masked chunk와 source reference만 받는 Document evidence prompt·schema definition을 추가했다.
- Key decisions:
  - candidate는 source chunk에 grounded되어야 하고 전체 prompt·response를 저장하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - structured validation과 존재하지 않는 chunk·중복·부분 성공 경계가 통과했다.
- Next steps:
  - 실제 provider별 prompt tuning은 별도 version으로 추가한다.

## [2026-07-19] Session Summary (Prompt Registry 기반 구현)

- What was done:
  - workflow·step key, prompt/schema version, DTO type, tool allowlist와 call/token cap을 정의했다.

- Key decisions:
  - production에는 P4 이후 workflow prompt 파일을 생성하지 않았다.

- Issues encountered:
  - None.

- Validation:
  - Fake 3-step이 test resource prompt를 정확히 조회하는 통합 테스트가 통과했다.

- Next steps:
  - 실제 prompt는 해당 domain schema와 함께 versioned asset으로 추가한다.
