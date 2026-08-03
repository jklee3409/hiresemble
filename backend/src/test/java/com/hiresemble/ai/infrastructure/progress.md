# Progress

## Overview

disabled·실제 Chat·Embedding·Search와 profile activation 테스트가 구현됐다.

## [2026-08-03] Session Summary (Chat 실제 deadline 회귀)

- What was done:
  - interrupt 가능한 5초 지연 model double로 75ms wall-clock timeout, `FailureKind.TIMEOUT`, worker 종료를 검증했다.
- Key decisions:
  - 실제 network와 Provider는 호출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `SpringAiOpenAiGatewayTest`와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (OpenAI reasoning·verbosity mapping 회귀)

- What was done:
  - request-scoped low reasoning effort와 low verbosity가 Spring AI OpenAI options에 전달되는 assertion을 추가했다.
- Key decisions:
  - 실제 network 없이 native option mapping만 검증한다.
- Issues encountered:
  - None.
- Validation:
  - `SpringAiOpenAiGatewayTest`가 단일-use Gradle 실행에서 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (image reference 실제 직렬화 회귀)

- What was done:
  - Prompt 구조에서 이미지별 reference text·media 단일 결합을 검증하고 실제 Spring AI→OpenAI SDK 요청의 content parts까지 capture했다.
  - unsafe·duplicate reference가 model call 전에 차단되는 회귀를 추가했다.
- Key decisions:
  - `Media.id` 존재만 확인하는 테스트는 제거하고 Provider가 실제 받는 text/data URL 결합을 기준으로 삼는다.
- Issues encountered:
  - None.
- Validation:
  - `SpringAiOpenAiGatewayTest` 포함 집중 테스트 통과, 실제 network 0회.
- Next steps:
  - Spring AI upgrade 시 native serialization test를 유지한다.

## [2026-08-01] Session Summary (OpenAI Chat/image parity 회귀)

- What was done:
  - 공통 status matrix, refusal·finish·cardinality·tool call·usage와 WebP Media 테스트를 추가했다.
- Key decisions:
  - 실제 Provider 대신 mock model/service fixture만 사용한다.
- Issues encountered:
  - None.
- Validation:
  - focused tests와 Backend 전체 491 tests 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (finish reason·usage 회귀)

- What was done:
  - length finish reason의 safe truncation 분류와 incurred usage 보존을 검증했다.
- Key decisions:
  - raw partial response는 safe message에 포함하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Spring AI mock과 strict request tests 통과.
- Next steps:
  - 새 Spring AI version에서는 metadata API 호환성을 재확인한다.

## [2026-08-01] Session Summary (strict Chat request payload와 오류 분류 회귀)

- What was done:
  - 실제 Spring OpenAI SDK 요청 객체를 capture해 strict/schema/store/tool 설정을 확인하고 schema rejection과 generic 400을 분리 검증했다.
- Key decisions:
  - WireMock용 별도 schema가 아니라 runtime registry의 exact schema를 사용한다.
- Issues encountered:
  - 없음.
- Validation:
  - focused gateway/schema test와 전체 check 통과, 실제 network 0회.
- Next steps:
  - 사용자가 기존 `codexRealOpenAiChatTest`를 한 번 실행한다.

## [2026-08-01] Session Summary (OpenAI 연결 회귀와 bounded smoke)

- What was done:
  - 빈 tool option, `/v1` activation gate, 400 safe mapping과 canonical embedding 정책 회귀를 추가했다.
- Key decisions:
  - live task는 성공 capability를 반복하지 않고 실패 보정도 capability별 최대 2회로 제한한다.
- Issues encountered:
  - Chat·Embedding은 `insufficient_quota`, Tavily는 성공했다.
- Validation:
  - focused test와 Backend 전체 check 67 suites/427 tests가 통과했다.
- Next steps:
  - quota 복구 뒤 Chat·Embedding만 재검증한다.

## [2026-08-01] Session Summary (OpenAI·Tavily·Bean matrix 검증)

- What was done:
  - Spring AI mock, Tavily bounded stream/HTTPS/usage, local real Bean과 local-offline disabled Bean 검증을 추가했다.
- Key decisions:
  - Codex live test는 일반 test에서 제외하고 retry·loop 없이 capability별 최대 한 번만 호출한다.
- Issues encountered:
  - None.
- Validation:
  - infrastructure focused tests와 Backend 전체 check가 통과했고 live task는 gate/key 부재로 호출 없이 skip됐다.
- Next steps:
  - 실제 key가 준비된 승인 환경에서 bounded live verification을 수행한다.

## [2026-07-31] Session Summary (Tavily WireMock 경계)

- What was done:
  - success·empty·malformed·429·5xx·timeout과 key 누락 fail-closed를 WireMock으로 검증했다.
- Key decisions:
  - 실제 Tavily network·key를 사용하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `TavilyWebSearchGatewayTest` 3 tests와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-19] Session Summary (Disabled gateway 검증)

- What was done:
  - 세 gateway가 동일한 안전한 configuration 오류로 종료되는지 검증했다.

- Key decisions:
  - 외부 network fixture를 사용하지 않는다.

- Issues encountered:
  - None.

- Validation:
  - 1 test가 통과했다.

- Next steps:
  - 실제 adapter는 후속 phase에서 별도 contract test를 갖는다.
