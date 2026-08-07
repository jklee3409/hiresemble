# Progress

## Overview

GitHub Source infrastructure 구현 상태를 추적한다.

## [2026-08-07] Session Summary (GitHub REST·snapshot·outbox infrastructure)

- What was done: fixed-host REST client, JDBC store, gzip JSON snapshot storage, sanitizer와 전용 deletion outbox worker를 구현했다.
- Key decisions: no redirect/auth, ETag/304, timeout/byte/concurrency cap과 test-only loopback base URL을 적용했다.
- Issues encountered: truncated tree·partial 수집과 object upload 후 DB 실패 보상을 별도 상태로 전달했다.
- Validation: WireMock 404/429/5xx/timeout/ETag/truncation/redirect와 sanitizer/outbox 테스트가 통과했다.
- Next steps: 운영 anonymous GitHub quota는 실제 트래픽 관찰 뒤 평가한다.
