# Progress

## Overview

P5 Job 등록·추출·상태·Scheduler와 P6 immutable 분석·결정론적 점수·RAG·OUTDATED·typed Agent Run 연결이 구현됐다.

## [2026-08-01] Session Summary (Job v3 retry·WebP 경계)

- What was done:
  - current Job snapshot 기반 v3 retry와 정적 WebP 안전 fetch를 Job 경계에 추가했다.
- Key decisions:
  - generic/resource retry는 predecessor unique successor를 공유하고 waiting manual input은 same-run resume이다.
- Issues encountered:
  - successor 연결로 증가한 Job version을 compatible replay와 실제 사용자 변경에서 구분했다.
- Validation:
  - v1/v2/current retry lineage·latest/QUEUED·budget unique와 WebP magic/decode/pixel 회귀, 전체 check 통과.
- Next steps:
  - animated WebP는 제외한다.

## [2026-08-01] Session Summary (Job 공고 자동 추출 안전 경계 확장)

- What was done:
  - Job page fetch 결과에 safe charset metadata를 추가하고 공고 이미지 fetch port를 연결했다.
- Key decisions:
  - 공개 DTO·Job status enum·DB schema는 변경하지 않고 extraction 내부 계약만 v2로 올렸다.
- Issues encountered:
  - None.
- Validation:
  - Backend 전체 check 69 suites/479 tests와 P5 Chromium 5/5 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Cover Letter용 공고 projection)

- What was done:
  - 공고 상세 DTO에 현재 자기소개서 진입 상태를 연결하고 P7 context가 최신 분석·requirement·OUTDATED projection을 owner scope로 조회하도록 최소 경계를 제공했다.
- Key decisions:
  - P6 immutable 분석 계약은 변경하지 않고 P8 면접·research field를 추가하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Job API 회귀, P7 actual 생성 context와 최종 source P6 actual Chromium 2/2가 통과했다.
- Next steps:
  - None.

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
