# Progress

## Overview

public/private GitHub Backend를 deterministic PostgreSQL, Fake와 WireMock으로 검증한다. 실제 GitHub와 paid AI 호출은 금지한다.

## [2026-08-09] Session Summary (FAILED source refresh 회귀)

- What was done: 실패 source가 동일 captured commit에서도 새 Run으로 `QUEUED`되는 API 통합 회귀를 추가했다.
- Key decisions: 자동 테스트는 Fake만 사용하고 실제 provider 검증 결과는 root/backend 기록에만 남긴다.
- Issues encountered: 새 테스트의 enum import 누락을 compile 단계에서 발견해 보정했다.
- Validation: `GitHubSourceApiIntegrationTest` 포함 focused suite가 통과했다.
- Next steps: None.

## [2026-08-09] Session Summary (GitHub App private security 회귀)

- What was done: connection state/PKCE/owner permission, token downscope/cache, private ingestion와 revocation cleanup 테스트를 추가했다.
- Key decisions: public Authorization 부재와 token 비영속화를 함께 검증한다.
- Issues encountered: 없음.
- Validation: focused GitHub tests와 전체 680 tests가 통과했다.
- Next steps: webhook 테스트는 승인 전 추가하지 않는다.

## [2026-08-07] Session Summary (GitHub Source 통합 검증)

- What was done: domain, REST gateway, canonical provenance와 7개 API operation 통합 fixture를 추가했다.
- Key decisions: 두 사용자·CSRF·idempotency·version·internal value 비노출을 실제 Spring/PostgreSQL 경계에서 검증한다.
- Issues encountered: refresh same SHA와 changed SHA를 하나의 Fake gateway에서 결정적으로 전환했다.
- Validation: 관련 focused tests 통과.
- Next steps: None.
