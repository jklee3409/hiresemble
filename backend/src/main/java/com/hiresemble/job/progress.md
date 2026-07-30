# Progress

## Overview

P5 Job 등록·추출·상태·Scheduler와 P6 immutable 분석·결정론적 점수·RAG·OUTDATED·typed Agent Run 연결이 구현됐다.

## [2026-07-29] Session Summary (P6 immutable 공고 분석 도메인·API)

- What was done:
  - `job_analyses` application boundary, scoring/hash domain, JDBC store와 분석 접수·목록·최신 상세 API를 추가했다.
  - verified profile evidence snapshot·exact cosine/lexical retrieval와 reuse/force reanalysis, 분석 Run resource 연결을 구현했다.
  - 분석 뒤 근거가 `REJECTED` 또는 `SOURCE_DELETED`로 바뀌어도 immutable link·결과를 유지하고 현재 상태와 `EVIDENCE_CHANGED`를 projection한다.
- Key decisions:
  - Eligibility와 점수는 독립이며 40/30/15/10/5 category weight·1/0.5/0/0 계수는 Java 정책만 계산한다.
  - 기존 분석은 삭제·수정하지 않고 job/profile/evidence hash 차이를 OUTDATED reason으로 projection한다.
- Issues encountered:
  - V6→V7 populated upgrade와 기존 migration fingerprint 검증은 루트 통합 단계에서 보강했다.
  - 1차 validator의 historical evidence 유지 finding을 current metadata owner join과 상태 전환 통합 회귀로 고정했다.
- Validation:
  - Job Analysis integration 4개, scoring 6개, P6 migration 3개와 Backend 전체 352 tests가 통과했다.
  - 최종 validator가 historical evidence 유지 finding 해소를 확인했지만 P6 전체는 actual E2E 미검증으로 `FAIL`이다.
- Next steps:
  - 새 검증 주기에서 current P6 wrapper를 통과시켜야 하며 P7 domain/API는 이번 범위에 포함하지 않는다.

## [2026-07-27] Session Summary (P5 Job 수직 기능 구현)

- What was done:
  - Job 공개 API 7개와 application/domain/infrastructure 계층을 구현했다.
- Key decisions:
  - 업무 상태와 추출 상태를 분리하고 owner·version·soft delete 조건을 모든 SQL에 적용한다.
- Issues encountered:
  - 초기 validator의 DNS rebinding·stream timeout 지적을 검증된 IP socket 고정과 절대 deadline으로 보정했다.
- Validation:
  - Backend 37 suites/322 tests와 P5 Browser E2E 5/5가 통과했다.
- Next steps:
  - P6에서 현재 `content_hash` 경계를 소비하되 분석 API와 table은 새 forward 변경으로 추가한다.
