# Progress

## Overview

P5 Job API·application·Scheduler·URL 보안과 P6 자동 분석 orchestration 회귀 테스트가 구현됐다.

## [2026-08-02] Session Summary (공고 자동 분석 내구성 회귀)

- What was done:
  - manual·URL·본문 보완 뒤 BALANCED 1회 접수, replay·restart deterministic reuse, quota 보존과 owner 격리를 검증했다.
- Key decisions:
  - Fake workflow와 PostgreSQL Testcontainers만 사용하고 외부 Provider network를 호출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `JobAutoAnalysisIntegrationTest` 2건, `JobIntegrationTest` 7건, `JobAnalysisIntegrationTest` 4건 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (legacy Job retry upgrade 회귀)

- What was done:
  - v1 FAILED/INTERRUPTED, v2 FAILED, current retry와 endpoint 중복 방지를 검증했다.
- Key decisions:
  - old row 불변·current Job snapshot·latest/QUEUED·budget 1건을 DB로 확인한다.
- Issues encountered:
  - PostgreSQL JSON existence 검사는 JDBC placeholder와 충돌하지 않는 `jsonb_exists`를 사용했다.
- Validation:
  - focused와 전체 check, P5 Chromium 5/5 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (Terminal retry fixture budget 불변식 보정)

- What was done:
  - FAILED 상태를 직접 구성하는 retry fixture가 terminal 전 예약 비용을 release하도록 보정했다.
- Key decisions:
  - production DB의 non-active reserved cost CHECK를 test fixture도 준수한다.
- Issues encountered:
  - 전체 check 1차에서 fixture가 CHECK를 위반했다.
- Validation:
  - JobIntegrationTest와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (근거 검토 source 경계 회귀)

- What was done:
  - 과거 분석 provenance 상태 변화 검증을 공개 직접 입력 검토 API가 아닌 legacy DB 상태 전이 fixture로 바꿨다.
- Key decisions:
  - 공개 승인·거절은 DOCUMENT_CHUNK 전용으로 검증하고, 이 테스트는 기존 분석 이력의 상태 projection 보존만 담당한다.
- Issues encountered:
  - None.
- Validation:
  - 집중 31 tests와 Backend 전체 54 suites/385 tests 통과.
- Next steps:
  - None.

## [2026-07-27] Session Summary (P5 Job 자동화 검증)

- What was done:
  - 생성 201/202, replay, 상태·history, owner, version, retry/resume, delete와 Scheduler를 검증했다.
- Key decisions:
  - 네트워크 경계는 Fake transport/socket으로 검증하고 PostgreSQL 불변식은 Testcontainers로 검증한다.
- Issues encountered:
  - DNS pinning과 slow body regression test를 validator 보정에서 추가했다.
- Validation:
  - 전체 Backend 37 suites/322 tests, 실패·오류·skip 0이다.
- Next steps:
  - P6 분석 테스트는 별도 phase에서 추가한다.
