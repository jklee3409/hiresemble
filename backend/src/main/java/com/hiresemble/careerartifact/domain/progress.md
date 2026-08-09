# Progress

## Overview

RESUME·PORTFOLIO content와 deterministic validator 계약이 구현됐다.

## [2026-08-09] Session Summary (실제 provider content canonicalization)

- What was done:
  - 선택 profile snapshot을 metric/date·named fact grounding material에 포함하고 승인 evidence UUID의 title·usage type을 서버 snapshot으로 정규화하는 `CareerArtifactContentCanonicalizer`를 추가했다.
  - nullable blank를 canonical `null`로 바꾸고 Resume item heading/subheading은 조직·직무 전용 필드가 아닌 일반 claim으로 검증하도록 수정했다. `role/역할` 책임 설명과 canonical job title label도 구분했다.
- Key decisions:
  - canonicalizer는 기존 승인 ID의 metadata와 값 없음 표현만 정규화하며 unknown ID나 사용자 claim을 수리하지 않는다.
- Issues encountered:
  - exact substring 검증이 편집용 제목과 책임 설명까지 canonical named fact로 오인해 실제 저비용 모델 출력을 반복 거부했다.
- Validation:
  - 실제 Portfolio·Resume 성공과 canonical metadata, nullable absence, editorial heading, unknown ID·invented fact 거부 unit regression을 확인했다.
- Next steps:
  - None.

## [2026-08-08] Session Summary (Strict artifact content validation)

- What was done:
  - grounded bullet/case-study reference와 title identity, global headline/summary/skill/slide title의 invented metric·date·role·organization, slide/section count와 외부 layout 지시를 record 기반으로 검증한다.
- Key decisions:
  - fact-check 결과도 renderer가 소비하는 최종 typed draft이며 bare map이나 승인 boolean을 사용하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - unknown/mismatched reference와 창작 claim, 허용/금지 content 경계 unit test를 통과했다.
- Next steps:
  - None.
