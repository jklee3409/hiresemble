# Progress

## Overview

Career Artifact deterministic content validator의 허용·거부 경계가 검증된다.

## [2026-08-09] Session Summary (실제 provider grounding regression)

- What was done:
  - selected profile grounding, evidence metadata canonicalization, nullable blank, responsibility label, editorial heading과 기존 unknown/invented fact 거부 회귀를 추가했다.
- Key decisions:
  - server normalization 허용 범위와 claim 검증 완화 범위를 서로 다른 assertion으로 고정했다.
- Issues encountered:
  - None.
- Validation:
  - `CareerArtifactContentValidatorTest`와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-08] Session Summary (Content validator test)

- What was done:
  - unknown/mismatched evidence ref, ungrounded claim과 global·item의 invented metric·role·organization·date를 거부하는 회귀를 추가했다.
- Key decisions:
  - Provider 승인 여부와 별개로 서버 validator가 최종 판정한다.
- Issues encountered:
  - None.
- Validation:
  - `CareerArtifactContentValidatorTest` 7 tests 통과.
- Next steps:
  - None.
