# Progress

## Overview

P2 프로필 순수 도메인 정책 테스트가 구현되어 있다.

## [2026-08-07] Session Summary (경험 유사도 정책 회귀)

- What was done:
  - fingerprint 안정성, exact·same·review·numeric conflict·new 판정을 단위 테스트로 고정했다.
- Key decisions:
  - 표현 유사도만으로 자동 병합하지 않고 anchor와 수치 불일치를 함께 검증한다.
- Issues encountered:
  - None.
- Validation:
  - `ProfileDomainTest` 통과.
- Next steps:
  - golden set 기반 false-positive 회귀를 추가한다.

## [2026-07-31] Session Summary (비학력 direct evidence 도메인 회귀)

- What was done:
  - 학력 direct evidence factory expectation을 제거하고 영문·한국어 교육 category 판별을 검증했다.
- Key decisions:
  - 자격증·어학·수상·경력 factory 동작과 content bound는 유지한다.
- Issues encountered:
  - None.
- Validation:
  - `ProfileDomainTest` 6 tests와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-19] Session Summary (P2 프로필 도메인 규칙 검증)

- What was done:
  - 완료 항목당 20%, 날짜·GPA·current career, source mapping과 evidence 재동기화를 검증했다.

- Key decisions:
  - 미완료 profile은 완료도 표시일 뿐 기능 차단 상태가 아님을 테스트 이름과 assertion으로 고정했다.

- Issues encountered:
  - None

- Validation:
  - Backend 전체 check에서 domain test가 통과했다.

- Next steps:
  - 후속 source type은 해당 phase에서 명시적 정책 테스트와 함께 추가한다.
