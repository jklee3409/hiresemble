# Progress

## Overview

public/private GitHub Source REST·storage·connection/outbox infrastructure 구현 상태를 추적한다.

## [2026-08-09] Session Summary (GitHub App credential·revocation infrastructure)

- What was done: typed properties, RS256 JWT, OAuth/user/install/token/uninstall gateway, cache, stores와 cleanup workers를 구현했다.
- Key decisions: production host를 고정하고 loopback은 test constructor만 허용하며 token cache는 upstream expiry-skew까지만 유지한다.
- Issues encountered: 401/403 단일 재발급, uninstall 404 성공과 bounded retry/dead를 안전한 code로 통일했다.
- Validation: WireMock security/gateway, worker와 전체 check가 통과했다.
- Next steps: 운영 credential은 runbook의 외부 주입만 사용한다.

## [2026-08-07] Session Summary (GitHub REST·snapshot·outbox infrastructure)

- What was done: fixed-host REST client, JDBC store, gzip JSON snapshot storage, sanitizer와 전용 deletion outbox worker를 구현했다.
- Key decisions: no redirect/auth, ETag/304, timeout/byte/concurrency cap과 test-only loopback base URL을 적용했다.
- Issues encountered: truncated tree·partial 수집과 object upload 후 DB 실패 보상을 별도 상태로 전달했다.
- Validation: WireMock 404/429/5xx/timeout/ETag/truncation/redirect와 sanitizer/outbox 테스트가 통과했다.
- Next steps: 운영 anonymous GitHub quota는 실제 트래픽 관찰 뒤 평가한다.
