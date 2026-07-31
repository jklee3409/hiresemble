# Progress

## Overview

P5 Job API·application·Scheduler·URL 보안 회귀 테스트가 구현됐다.

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
