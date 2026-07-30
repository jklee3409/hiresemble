# Progress

## Overview

P3 Registry와 P4~P7 workflow 계약·orchestrator 통합 테스트가 구현됐다.

## [2026-07-30] Session Summary (P7 suggestion 경계 회귀)

- What was done:
  - generation fact-check와 verification check/aggregate에 20/21개·1000/1001자 structured output 경계를 추가했다.
  - facts 20개와 requirements 20개가 모두 유효한 경우 aggregate가 우선순위를 보존한 20개만 반환하는 회귀를 추가했다.
- Key decisions:
  - 경계 위반 output은 domain apply·verification persist 전에 실패해야 한다.
- Issues encountered:
  - 없음.
- Validation:
  - 대상 workflow tests와 Backend 전체 54 suites/380 tests가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 generation·verification workflow 검증)

- What was done:
  - 정확한 8/6단계, bounded fan-out, VERIFIED allowlist, partial success/retry, source deleted, quality/budget, cancel·restart·privacy를 검증했다.
- Key decisions:
  - 실제 provider 없이 typed Fake output과 PostgreSQL application port를 사용한다.
- Issues encountered:
  - verification provenance claim text가 checkpoint 최소 출력에 남지 않는 회귀 assertion을 추가했다.
- Validation:
  - Cover Letter workflow 3 suites와 Backend 전체 377 tests가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (Job Posting Extraction workflow 검증)

- What was done:
  - 고정 순서, override, invalid output, retry 분류, cancel·reconciliation·reuse와 checkpoint privacy를 검증했다.
- Key decisions:
  - 실제 provider·외부 검색 없이 Fake fetch·Chat만 사용한다.
- Issues encountered:
  - 없음.
- Validation:
  - 관련 7개 테스트와 전체 Backend check가 통과했다.
- Next steps:
  - P6 전까지 분석 workflow 테스트를 추가하지 않는다.

## [2026-07-19] Session Summary (Workflow Registry 검증)

- What was done:
  - canonical coverage와 metadata·sequence 거부 조건을 검증했다.

- Key decisions:
  - test contribution version을 canonical version과 분리한다.

- Issues encountered:
  - None.

- Validation:
  - 3 tests가 통과했다.

- Next steps:
  - 실제 contribution마다 registry contract test를 추가한다.
