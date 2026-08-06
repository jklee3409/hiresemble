# Progress

## Overview

P3 legacy quality/tier 분리와 disabled provider routing, 자기소개서 exact model routing이 구현됐다.

## [2026-08-06] Session Summary (중앙 OpenAI chat model catalog)

- What was done: 공식 model ID 10개와 표시 metadata·추천 모델을 `OpenAiChatModels`에 모으고 자기소개서 v4 exact route를 구현했다.
- Key decisions: 미지원 문자열은 fail-closed로 거부하고 catalog와 provider 요청에 같은 ID를 사용한다.
- Issues encountered: `gpt-4.5-preview`는 공식 폐기 모델이라 선택지에서 제외했다.
- Validation: catalog·router 단위 테스트와 Backend 전체 `check` 통과.
- Next steps: OpenAI 모델 수명주기 변경 때 catalog·가격 migration·계약 테스트를 한 작업으로 갱신한다.

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
