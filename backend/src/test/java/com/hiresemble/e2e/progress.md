# Progress

## Overview

P4 Document, P5 Job과 P6 Job Analysis pipeline을 격리 PostgreSQL 18+pgvector·MinIO·Spring·Vue·Fake gateway·Chromium으로 검증한다.

## [2026-07-29] Session Summary (P6 실제 Backend·Frontend 분석 E2E harness)

- What was done:
  - `p6BrowserE2eTest`가 PostgreSQL·Spring async runtime·Fake Chat/Embedding·Vue·Chromium을 연결하고 분석·reuse·OUTDATED·재분석·근거 부족·owner 격리를 실행하도록 추가했다.
- Key decisions:
  - direct career VERIFIED evidence를 사용해 Object Storage 없이 P6 RAG application 경계를 검증하고 실제 provider 호출은 금지한다.
- Issues encountered:
  - 1차 locator 충돌을 보정한 2차 실행에서 비공개 evidence GET assertion이 500을 반환했다. 공개 PUT endpoint 404 assertion으로 수정했지만 자동 재검증 상한 때문에 세 번째 실행은 하지 않았다.
- Validation:
  - 2차 Playwright에서 근거 부족 test 1/1은 통과했고 정상 test는 마지막 evidence 격리 assertion 전까지 분석·reuse·재분석·공고/분석/Run 404를 통과했다.
- Next steps:
  - 수정된 evidence PUT owner 404와 wrapper DB assertion을 향후 단일 실행으로 확인한다.

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
