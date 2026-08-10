# Progress

## Overview

Phase 1 Gate 1 GitHub ingestion workflow 통합 테스트가 구현됐다.

## [2026-08-10] Session Summary (한국어·중심 후보 extraction 계약)

- What was done: GitHub extraction prompt v2, repository당 최대 3개와 제목·본문 한글 runtime 검증 회귀를 추가했다.
- Key decisions: prompt 문구와 Java validation을 한 contract test에서 함께 고정한다.
- Issues encountered: 없음.
- Validation: Backend 전체 `check` 696 tests 통과.
- Next steps: None.

## [2026-08-09] Session Summary (validation step reuse 회귀)

- What was done: source validation executor가 재사용 불가임을 workflow contribution 경계에서 고정했다.
- Key decisions: 상태 전이 step만 직접 계약 검증하고 나머지 checkpoint 재사용은 유지한다.
- Issues encountered: 없음.
- Validation: `GitHubIngestionOrchestratorIntegrationTest`가 통과했다.
- Next steps: None.

## [2026-08-07] Session Summary (GitHub workflow orchestration 검증)

- What was done: account wait/resume, direct repository skip, partial, invalid ref, usage, retry·cancel·SSE fixture를 추가했다.
- Key decisions: Fake Chat/Embedding/Storage만 사용하고 model-owned ID를 허용하지 않는다.
- Issues encountered: fan-out 성공 scope와 resume source 상태 회귀를 test로 발견해 보정했다.
- Validation: `GitHubIngestionOrchestratorIntegrationTest` 통과.
- Next steps: None.
