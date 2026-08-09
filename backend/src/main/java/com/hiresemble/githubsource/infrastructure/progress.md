# Progress

## Overview

public/private GitHub Source REST·storage·connection/outbox infrastructure 구현 상태를 추적한다.

## [2026-08-09] Session Summary (bounded public codeload archive gateway)

- What was done: 공개 HEAD archive redirect를 검증하고 ZIP entry를 기존 tree/blob 모델로 변환하는 gateway를 추가했다.
- Key decisions: 고정 GitHub/codeload origin, 40자리 commit SHA, path traversal·크기·entry 수 제한과 실제 Git blob SHA-1을 적용했다. retrieval policy는 `github-snapshot-v2`로 올렸다.
- Issues encountered: 대상 archive가 기존 8 MiB REST response 한도를 소폭 초과해 archive 전용 16 MiB compressed 한도를 분리했다.
- Validation: 정상 archive, 외부 redirect 거부, compressed oversize 회귀 테스트와 실제 8.43 MB repository 수집이 통과했다.
- Next steps: None.

## [2026-08-09] Session Summary (private off blank App ID binding)

- What was done:
  - `GITHUB_APP_ID=`인 실제 로컬 환경에서 private repository 기능이 꺼져 있으면 nullable `Long`으로 binding되도록 보정했다.
- Key decisions:
  - private 기능이 켜진 경우 App ID non-null·positive 검증과 fail-closed 경계는 그대로 유지한다.
- Issues encountered:
  - primitive `long` binding이 비활성 기능의 빈 선택 설정까지 애플리케이션 시작 전에 실패시켰다.
- Validation:
  - blank binding focused test, 실제 local Spring context와 전체 Backend check가 통과했다.
- Next steps:
  - None.

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
