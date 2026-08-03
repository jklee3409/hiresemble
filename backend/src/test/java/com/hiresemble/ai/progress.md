# Progress

## Overview

P3 runtime과 P4 Document·P5 Job workflow 통합 검증 및 전체 Chat strict output 자동 검사가 구현됐다.

## [2026-08-03] Session Summary (Job Analysis normalization·retry·deadline 회귀)

- What was done:
  - source-only schema, 복합 정규화, retry 순서, usage hard cap, 단계별 prompt와 wall-clock deadline 회귀를 Fake 기반으로 추가했다.
- Key decisions:
  - 실제 Provider·검색 network는 사용하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Backend 전체 78 suites/535 tests 통과, 외부 호출 0회.
- Next steps:
  - None.

## [2026-08-01] Session Summary (partial terminal·document filtering 회귀)

- What was done:
  - workflow policy completeness, Orchestrator terminal decision과 문서 일부·전체 candidate rejection 회귀를 추가했다.
- Key decisions:
  - 자기소개서 partial failure와 Provider/structured/domain 실패는 계속 전체 실패로 검증한다.
- Issues encountered:
  - None.
- Validation:
  - Backend 68 suites/466 tests, 실패·오류·skip 0, 외부 호출 0회.
- Next steps:
  - bounded live 검증과 offline 회귀 결과를 분리한다.

## [2026-08-01] Session Summary (phase별 structured retry 회귀)

- What was done:
  - validation·gateway·orchestration·document contract의 deterministic/repairable 호출 수와 usage 회귀를 확장했다.
- Key decisions:
  - 실제 Provider 대신 Fake/수기 synthetic JSON만 사용한다.
- Issues encountered:
  - test-only price row가 migration 불변성을 오염시켜 기존 V13 item 참조로 보정했다.
- Validation:
  - 전체 68 suites/459 tests 통과, 외부 호출 0회.
- Next steps:
  - live gate는 운영 runbook에서 별도 수행한다.

## [2026-08-01] Session Summary (strict schema와 document regression)

- What was done:
  - runtime schema 전체 열거, Gateway payload, document metadata mapping·저장 회귀를 Fake/SDK capture로 추가했다.
- Key decisions:
  - test와 runtime은 같은 schema generator/registry를 사용한다.
- Issues encountered:
  - 없음.
- Validation:
  - focused test와 Backend 68 suites/452 tests 통과, 외부 호출 0회.
- Next steps:
  - live 결과는 test 결과와 별도로 P8.5-V에 기록한다.

## [2026-07-27] Session Summary (P5 Job workflow 테스트 추가)

- What was done:
  - Job 고정 순서·structured output·override·waiting·retry·cancel·restart·reuse·privacy 테스트를 추가했다.
- Key decisions:
  - 실제 외부 provider 없이 Fake fetch·Chat만 사용한다.
- Issues encountered:
  - 없음.
- Validation:
  - 관련 7개와 전체 Backend 322 tests가 통과했다.
- Next steps:
  - P6 분석 workflow가 생길 때 별도 fixture를 추가한다.

## [2026-07-19] Session Summary (AI runtime Fake 검증 구현)

- What was done:
  - registry 3, router 3, structured output 2, disabled gateway 1, PostgreSQL orchestrator 10 tests를 추가했다.
  - lease보다 긴 blocking gateway 중 주기 heartbeat와 stale reconciliation 경쟁을 검증했다.

- Key decisions:
  - Fake contribution, prompt, gateway와 apply port는 모두 test scope에 둔다.

- Issues encountered:
  - cancellation resource-null 경계와 JSONB field order 독립 hash를 보정했다.

- Validation:
  - AI targeted 19/19와 Backend 전체 243/243 tests가 통과했다.

- Next steps:
  - 실제 workflow별 contract test는 해당 phase에서 추가한다.
