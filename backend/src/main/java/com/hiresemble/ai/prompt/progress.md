# Progress

## Overview

P3 versioned PromptRegistry에 P4 Document와 P5 Job structured prompt metadata가 구현됐다.

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
