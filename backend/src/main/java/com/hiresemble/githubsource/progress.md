# Progress

## Overview

Gate 1 public GitHub Source와 Gate 5 GitHub App private repository Backend가 같은 bounded ingestion·canonical pipeline에 구현됐다. Gate 5는 최종 browser 재검증 전 `IMPLEMENTED_NOT_VERIFIED`다.

## [2026-08-09] Session Summary (공개 source quota-safe refresh 복구)

- What was done: 실패 source 재실행과 공개 repository archive snapshot 경로를 연결해 실제 실패 요청을 복구했다.
- Key decisions: 공개 content만 archive를 우선하고 private token REST, API·DB 계약과 10단계 workflow 순서는 유지한다.
- Issues encountered: 익명 REST quota와 stateful validation checkpoint 재사용이 연속으로 드러났다.
- Validation: 실제 Run 성공, focused GitHub integration 통과, 전체 suite의 비관련 timing test는 격리 재실행 통과다.
- Next steps: metadata discovery quota 용량 계획은 남아 있다.

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
