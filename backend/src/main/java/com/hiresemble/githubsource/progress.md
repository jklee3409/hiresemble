# Progress

## Overview

Gate 1 public GitHub Source와 Gate 5 GitHub App private repository Backend가 같은 bounded ingestion·canonical pipeline에 구현됐다. Gate 5는 최종 browser 재검증 전 `IMPLEMENTED_NOT_VERIFIED`다.

## [2026-08-09] Session Summary (GitHub App private repository lifecycle)

- What was done:
  - one-time setup/OAuth+PKCE connection, read-only repository downscope token, additive access mode/visibility, private ingestion, refresh/disconnect와 revocation/snapshot cleanup worker를 구현했다.
- Key decisions:
  - installation ID는 user OAuth 접근 검증 전 신뢰하지 않고 token/code/state/JWT를 영속화하지 않는다. ACTIVE connection과 UI 선택 repository만 token scope에 포함한다.
- Issues encountered:
  - public gateway의 무인증 동작과 private 401/403 단일 token 재발급을 같은 interface에서 호환되게 유지해야 했다.
- Validation:
  - GitHub App connection/security/gateway/pipeline focused test와 Backend 전체 102 suites/680 tests가 통과했다.
- Next steps:
  - 실제 installation은 local UAT `USER_MANUAL_UI_VALIDATION_PENDING`이며 webhook은 deferred다.

## [2026-08-07] Session Summary (GitHub Source vertical 구현)

- What was done:
  - URL/source lifecycle, command/query, REST API, JDBC, public GitHub gateway, bounded snapshot·sanitizer와 deletion outbox를 구현했다.
- Key decisions:
  - production 기본 비활성, public-only, no credential/clone/code execution, 별도 snapshot lifecycle을 적용했다.
- Issues encountered:
  - account discovery와 repository direct 흐름을 하나의 source aggregate와 workflow에서 안전하게 분기했다.
- Validation:
  - domain·gateway·canonical·workflow·API·migration 집중 테스트가 통과했다.
- Next steps:
  - Gate 2 Frontend가 이 모듈의 7개 operation을 소비한다.
