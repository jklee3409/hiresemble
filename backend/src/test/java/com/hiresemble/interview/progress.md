# Progress

## Overview

P8 migration·API·DB 불변식 통합 검증이 구현되어 있다.

## [2026-08-08] Session Summary (Interview timestamp fixture 안정화)

- What was done:
  - research completion 시각을 DB의 started/created 시각 이상으로 갱신해 timestamp CHECK와 application clock 오차를 제거했다.
- Key decisions:
  - 운영 동작은 바꾸지 않고 test fixture SQL만 결정적으로 유지한다.
- Issues encountered:
  - 전체 회귀에서 약 40ms DB/application clock 차이로 한 건이 간헐 실패했다.
- Validation:
  - 해당 Interview 통합 test focused 재실행 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (terminal timestamp fixture 안정화)

- What was done:
  - DB와 애플리케이션 clock 경계에서도 terminal research fixture가 check constraint를 만족하도록 완료 시각을 시작 시각 이상으로 고정했다.
- Key decisions:
  - 제품 동작은 바꾸지 않고 테스트 fixture의 시간 불변식만 명시한다.
- Issues encountered:
  - 전체 check 첫 실행에서 약 93ms 시계 차로 기존 fixture가 간헐 실패했다.
- Validation:
  - Backend 전체 69 suites/469 tests 재실행 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 migration·API 통합 테스트)

- What was done:
  - V12 fresh/upgrade/digest와 준비·answer CAS·feedback·retry·cancel·history delete 테스트를 추가했다.
- Key decisions:
  - 실제 PostgreSQL 제약과 MockMvc 공개 응답을 함께 검증한다.
- Issues encountered:
  - 전체 suite의 JVM/container 수 ms clock skew를 fixture `GREATEST` terminal 시각으로 보정했다.
  - 1차 self-audit 보정으로 foreign `HIGH_QUALITY` feedback·research retry가 모두 404인지 검증했다.
- Validation:
  - 제한 보정 후 Backend `check` 61 suites/407 tests, failure·error·skip 0으로 통과했다.
- Next steps:
  - None.
