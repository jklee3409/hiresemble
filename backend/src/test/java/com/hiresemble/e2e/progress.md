# Progress

## Overview

P4 실제 Document pipeline을 격리 PostgreSQL 18+pgvector·MinIO·Spring·Vue·Fake AI·Chromium으로 검증한다.

## [2026-07-19] Session Summary (P4 실제 Backend·Frontend·SSE E2E)

- What was done:
  - 성공·manual same-run resume·AI partial failure·두 사용자 404의 Playwright 4개 시나리오를 Backend Gradle task로 연결했다.
- Key decisions:
  - production Fake endpoint 없이 test-scope `@Primary` gateway와 immutable Fake price catalog만 사용한다.
- Issues encountered:
  - 고정 Frontend port 충돌과 Vite 인자 전달을 random validated port와 직접 `--host/--port/--strictPort` command로 해결했다.
  - Fake usage price pair 제약은 Chat·Embedding price item을 seed해 해결했다.
- Validation:
  - `.\gradlew.bat p4BrowserE2eTest --rerun-tasks --info --no-daemon --console=plain`에서 Playwright 4/4가 통과했다.
- Next steps:
  - GitHub-hosted runner의 신규 P4 job 결과는 첫 push/PR에서 확인한다.
