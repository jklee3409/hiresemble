# Progress

## Overview

disabled·실제 Chat·Embedding·Search와 profile activation 테스트가 구현됐다.

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
