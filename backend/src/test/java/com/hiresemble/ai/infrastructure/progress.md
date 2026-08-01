# Progress

## Overview

disabled·실제 Chat·Embedding·Search와 profile activation 테스트가 구현됐다.

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
