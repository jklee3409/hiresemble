# Progress

## Overview

Phase 1 GitHub Backend를 deterministic PostgreSQL, Fake와 WireMock으로 검증한다. 실제 GitHub와 paid AI 호출은 금지한다.

## [2026-08-07] Session Summary (GitHub Source 통합 검증)

- What was done: domain, REST gateway, canonical provenance와 7개 API operation 통합 fixture를 추가했다.
- Key decisions: 두 사용자·CSRF·idempotency·version·internal value 비노출을 실제 Spring/PostgreSQL 경계에서 검증한다.
- Issues encountered: refresh same SHA와 changed SHA를 하나의 Fake gateway에서 결정적으로 전환했다.
- Validation: 관련 focused tests 통과.
- Next steps: None.
