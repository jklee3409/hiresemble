# Progress

## Overview

P3 local/production 기본 gateway는 network-free disabled 상태다.

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
