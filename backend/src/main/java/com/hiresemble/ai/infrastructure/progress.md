# Progress

## Overview

local은 OpenAI Chat·Embedding과 Tavily Search를 실제 adapter로 활성화하고 local-offline/test는 capability별 disabled/Fake를 사용한다.

## [2026-08-01] Session Summary (OpenAI endpoint·요청 옵션·safe rejection 보정)

- What was done:
  - official base URL `/v1`, 빈 tool option 비전송, Chat·Embedding 4xx safe code와 status/code/param/request ID logging을 반영했다.
- Key decisions:
  - Provider body·prompt·secret은 기록하지 않는다.
- Issues encountered:
  - live OpenAI는 `insufficient_quota`, Tavily BASIC은 성공했다.
- Validation:
  - gateway/activation focused test와 Backend 67 suites/427 tests가 통과했다.
- Next steps:
  - OpenAI quota 복구 후 capability smoke를 재실행한다.

## [2026-08-01] Session Summary (OpenAI adapter·Tavily hardening·activation gate)

- What was done:
  - Spring AI Chat/Embedding adapter, JDBC price query, fail-closed validator와 capability별 disabled Bean을 구현했다.
  - Tavily response를 2MB bounded stream으로 전환하고 HTTPS/test HTTP·outbound usage 계약을 보강했다.
- Key decisions:
  - request option은 model override, strict schema, `maxRetries=0`, `store=false`, `n=1`, tool none으로 고정한다.
- Issues encountered:
  - capability 분리 후 기존 Fake fixture의 누락 port를 test configuration에 명시했다.
- Validation:
  - OpenAI mock, Tavily WireMock/bounded stream, local/offline Bean matrix와 전체 Backend check가 통과했다.
- Next steps:
  - bounded live verification은 key/gate 준비 후 실행한다.

## [2026-07-31] Session Summary (P8 runtime·Tavily opt-in wiring)

- What was done:
  - 두 P8 workflow/context/prompt/failure handler와 conditional Tavily adapter를 runtime configuration에 조립했다.
- Key decisions:
  - 기본 provider는 `none`, Tavily 활성화 시 key 누락은 fail-closed이며 중복 search bean을 만들지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 기본 boot와 WireMock success·empty·malformed·429·5xx·timeout 테스트가 통과했다.
- Next steps:
  - 운영 provider 호출은 별도 승인 전까지 비활성으로 유지한다.

## [2026-07-30] Session Summary (P7 Cover Letter runtime wiring)

- What was done:
  - generation/verification context·prompt·workflow·failure handler와 Cover Letter ports를 canonical runtime registry에 조립했다.
- Key decisions:
  - production gateway 기본값은 disabled로 유지하고 Fake AI는 test/E2E scope에서만 사용한다.
- Issues encountered:
  - 없음.
- Validation:
  - Spring context, workflow registry와 Backend 전체 check가 통과했다.
- Next steps:
  - 실제 provider 활성화는 별도 승인 전까지 금지한다.

## [2026-07-29] Session Summary (P6 Job Analysis runtime wiring)

- What was done:
  - P6 context·prompt·workflow와 Job query/command/embedding port를 production registry에 조립했다.
- Key decisions:
  - 실제 provider 기본값과 disabled gateway는 유지한다.
- Issues encountered:
  - 없음.
- Validation:
  - Spring context·전체 Backend check가 통과했다.
- Next steps:
  - 실제 provider 활성화는 별도 승인·가격 정책과 함께 수행한다.

## [2026-07-27] Session Summary (P5 Job runtime contribution 등록)

- What was done:
  - Job context·workflow·failure handler를 canonical registry와 orchestrator runtime에 조립했다.
- Key decisions:
  - production Chat 기본값은 disabled이고 Fake Chat은 test scope에만 둔다.
- Issues encountered:
  - 없음.
- Validation:
  - application context와 Job workflow 통합 테스트가 통과했다.
- Next steps:
  - 실제 provider 활성화 전 가격·policy·timeout 계약을 별도 검증한다.

## [2026-07-19] Session Summary (P4 runtime contribution 등록)

- What was done:
  - provider-independent Document contribution을 runtime에 등록하고 disabled gateway 기본값을 유지했다.
- Key decisions:
  - Fake embedding·Chat은 test configuration에서만 `@Primary`로 등록한다.
- Issues encountered:
  - None.
- Validation:
  - `AI_PROVIDER=none` production-like 안전 실패와 test-scope Fake E2E가 통과했다.
- Next steps:
  - 실제 network adapter를 자동 fallback으로 등록하지 않는다.

## [2026-07-19] Session Summary (실제 provider 기본 비활성화)

- What was done:
  - Chat·Embedding·Search 요청을 외부 호출 없이 `AI_PROVIDER_DISABLED`로 종료하는 adapter를 추가했다.

- Key decisions:
  - production Fake bean과 fallback provider를 두지 않는다.

- Issues encountered:
  - None.

- Validation:
  - disabled gateway unit test와 network/client 정적 검색이 통과했다.

- Next steps:
  - 실제 provider adapter는 명시적 후속 phase와 가격·secret 설정 검증 뒤 추가한다.
