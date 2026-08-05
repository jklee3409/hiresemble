# Progress

## Overview

P5 Job 상태·URL 정책과 P6 analysis enum·hash·결정론적 scoring, 자동 분석 request 상태 정책이 구현됐다.

## [2026-08-05] Session Summary (JobPostingHalf 도메인 분류)

- What was done:
  - `FIRST_HALF`·`SECOND_HALF` enum과 서울 기준 등록 시각 분류를 domain record·query에 추가했다.
- Key decisions:
  - 6월까지 상반기, 7월부터 하반기로 경계를 고정했다.
- Issues encountered:
  - None.
- Validation:
  - 6월 30일/7월 1일 migration 경계와 2026 하반기 생성 통합 검증 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (적합도 rubric v2·analysis coverage)

- What was done:
  - `UNKNOWN` criterion을 점수 분모에서 제외하고 weighted analysis coverage를 계산하는 rubric v2를 구현했다.
- Key decisions:
  - `MISSING`은 0점, 전부 `UNKNOWN`이면 fit score null·coverage 0으로 구분한다.
- Issues encountered:
  - 기존 `UNKNOWN=0`은 근거 부족을 실제 불일치와 동일하게 낮은 점수로 만들었다.
- Validation:
  - category 재배분, mixed UNKNOWN, all UNKNOWN과 decimal residual 단위 테스트 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (자동 분석 상태 모델)

- What was done:
  - PENDING·CLAIMED·LAUNCHED·BLOCKED·SUPERSEDED durable 상태 enum을 추가했다.
- Key decisions:
  - 기존 extraction·analysis·Agent Run 상태 enum은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - compile과 persistence state transition 통합 테스트 통과.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 결정론적 점수·snapshot hash)

- What was done:
  - canonical 분석 enum, stable tenant snapshot hash와 category redistribution scoring을 추가했다.
- Key decisions:
  - Eligibility와 점수는 독립이며 MATCHED/PARTIAL/MISSING/UNKNOWN 계수만 서버가 적용한다.
- Issues encountered:
  - decimal residual은 stable criterion order에 cent 단위로 배분했다.
- Validation:
  - scoring fixture 6개와 전체 Backend check가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (Job domain 규칙 구현)

- What was done:
  - 허용/금지 전이, submittedAt 보존, reopen과 canonical URL 규칙을 구현했다.
- Key decisions:
  - percent-encoding은 unreserved 문자만 해제하고 reserved escape는 대문자 escape로 보존한다.
- Issues encountered:
  - 초기 구현의 `%2F`와 `/`, `+`와 `%20` false duplicate 위험을 validator 보정에서 해소했다.
- Validation:
  - 모든 전이와 canonical 동등/비동등 unit test 및 duplicate 통합 테스트가 통과했다.
- Next steps:
  - P6 stale 계산은 현재 content hash를 입력으로 사용한다.
