# Progress

## Overview

P3 Registry와 P4~P8 workflow 계약·orchestrator 통합 테스트가 구현됐다.

## [2026-08-02] Session Summary (Job Analysis Provider mapping·safe reason 회귀)

- What was done:
  - Provider 타입 field 경계, 신규 `false/null`·재사용 Provider 0회, nullable·trim mapping과 requirement/match 세부 safe reason 테스트를 추가했다.
- Key decisions:
  - correction guidance는 사용자 값 없이 최대 2 attempts만 허용하고 allowlist 밖 evidence는 기존 non-repairable domain 실패를 유지한다.
- Issues encountered:
  - None.
- Validation:
  - `*JobAnalysis*` 집중 테스트는 통과했다. Backend 전체 check는 범위 밖 Object Deletion Outbox 2건 실패로 미통과했다.
- Next steps:
  - None.

## [2026-08-01] Session Summary (Job extraction v3 aggregate 회귀)

- What was done:
  - imageRef 재정렬/누락/invalid, aggregate 경계·noise·mixed DOM과 v1·v2 legacy definition 테스트를 추가했다.
- Key decisions:
  - synthetic text와 Fake image gateway만 사용한다.
- Issues encountered:
  - 첫 fixture의 JSON escape assertion과 mixed DOM line 조건을 실제 payload 계약에 맞췄다.
- Validation:
  - focused와 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (terminal partial policy completeness 회귀)

- What was done:
  - contribution policy 누락 fail-fast와 Document 성공·Cover Letter 실패 정책을 검증했다.
- Key decisions:
  - 새 workflow가 공용 기본 partial code를 우연히 상속하지 못하게 한다.
- Issues encountered:
  - None.
- Validation:
  - focused workflow tests와 전체 check 통과.
- Next steps:
  - executable contribution 추가 시 policy assertion을 유지한다.

## [2026-08-01] Session Summary (다른 workflow structured 정책 회귀)

- What was done:
  - Job Analysis malformed JSON을 deterministic으로, Job extraction semantic-invalid output을 repair-once로 갱신했다.
- Key decisions:
  - 모든 structured failure를 일괄 non-retryable로 만들지 않고 phase/reason에 따라 분리한다.
- Issues encountered:
  - 기존 generic structured 3회 기대를 새 의미 계약과 구분해야 했다.
- Validation:
  - AI workflow 113-test focused run 보정 뒤 전체 check 통과.
- Next steps:
  - workflow별 typed repair reason을 필요한 범위에서만 추가한다.

## [2026-08-01] Session Summary (Cover Letter Provider TipTap 경계 회귀)

- What was done:
  - `WRITE_ANSWER`의 Provider 전용 recursive TipTap output이 기존 domain/application DTO와 동일한 의미로 mapping되는 generation 회귀를 갱신했다.
- Key decisions:
  - 공개 TipTap DTO annotation이 Provider schema에 직접 유입되지 않게 한다.
- Issues encountered:
  - 전수 keyword 검사에서 공개 DTO의 `default` keyword가 추가 strict finding으로 검출됐다.
- Validation:
  - strict schema와 Cover Letter generation focused test 통과.
- Next steps:
  - 전체 Backend check로 P7 후속 단계와 저장 회귀를 확인한다.

## [2026-08-01] Session Summary (다중 usage workflow 회귀)

- What was done:
  - Fake response를 다중 usage port에 맞추고 각 workflow의 price version 전달을 검증했다.
- Key decisions:
  - Fake는 test source에만 두며 실제 network client를 생성하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - workflow tests와 P4~P8 actual이 모두 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 workflow 계약·경계)

- What was done:
  - exact step/version, BASIC/ADVANCED 상한, privacy, coverage, provenance, feedback 제약과 atomic restart 테스트를 추가했다.
- Key decisions:
  - 모든 provider 장애와 정상 empty를 분리하고 checkpoint 원문·provider 응답 부재를 검증한다.
- Issues encountered:
  - 1차 self-audit 보정으로 top-level output 전용 `FOLLOW_UP` 허용 회귀를 추가했다.
- Validation:
  - P8 workflow contract 3 tests, workflow integration 4 tests와 제한 보정 후 Backend 전체 check가 통과했다.
- Next steps:
  - None.

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
