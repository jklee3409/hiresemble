# Progress

## Overview

P4 Document와 P5 Job pipeline을 격리 PostgreSQL 18+pgvector·MinIO·Spring·Vue·Fake gateway·Chromium으로 검증한다.

## [2026-07-27] Session Summary (P5 실제 Backend·Frontend Job E2E)

- What was done:
  - 수동 201 상태 수명주기, URL-only 202 추출, WAITING_USER same-run resume, owner 404와 Scheduler 자동 마감 5개를 연결했다.
- Key decisions:
  - 실제 외부 사이트·유료 AI 없이 test-scope Fake URL fetch와 Fake Chat을 사용한다.
- Issues encountered:
  - 생성 직전 CSRF 요청과 mutation 완료를 명시적으로 기다리도록 harness를 안정화했다.
- Validation:
  - `gradlew.bat p5BrowserE2eTest`에서 Playwright 5/5와 JUnit wrapper DB assertion이 통과했다.
- Next steps:
  - P6 분석 E2E는 분석 기능 구현 전 추가하지 않는다.

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
