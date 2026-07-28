# Progress

## Overview

- 현재 구현 route가 공유하는 icon, page header, text status, loading·empty·error state와 pagination primitive가 있다.
- 공용 component는 domain 판단이나 API 호출을 소유하지 않고 접근 가능한 표현만 제공한다.

## [2026-07-28] Session Summary (공용 검증 Focus·Control 언어 통합)

- What was done:
  - 첫 invalid control로 focus를 이동하는 `focusFirstInvalidControl` helper와 회귀 test를 추가했다.
  - BrandMark의 보조 accent를 Hiresemble Blue scale 안으로 정리하고 전역 control class와 결합했다.
- Key decisions:
  - native input semantics와 page의 label·error 연결을 유지하고 helper는 domain validation을 소유하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - helper unit test, 전체 149 tests와 keyboard Playwright가 통과했다.
- Next steps:
  - dialog·drawer의 기존 focus trap 계약은 layout·feature 소유로 유지한다.

## [2026-07-28] Session Summary (조립형 Hiresemble BrandMark 추가)

- What was done:
  - H, node, orbit와 연결선을 결합한 inline SVG `BrandMark`를 Public·App shell과 404에서 재사용하도록 추가했다.
- Key decisions:
  - mark 자체는 장식으로 숨기고 실제 link에는 문맥별 accessible name을 제공하며 외부 asset은 사용하지 않는다.
- Issues encountered:
  - desktop sidebar에서 긴 lockup이 줄어드는 현상을 실제 캡처로 찾아 context를 아래로 배치했다.
- Validation:
  - 공용 component test와 1440·390px 직접 캡처에서 full·compact·inverse 사용처를 확인했다.
- Next steps:
  - favicon 적용은 별도 asset·제품 범위가 승인될 때 검토한다.

## [2026-07-27] Session Summary (제품 공용 UI primitive 구축)

- What was done:
  - `AppIcon`, `PageHeader`, `StatusBadge`, `StatePanel`, `PaginationNav`와 component test를 추가했다.
  - layout·profile·documents·jobs·Agent Run page가 반복하던 header, 상태 label, 조회 state와 pagination 표현을 공용화했다.
- Key decisions:
  - domain enum과 두 상태 축 판단은 호출부에 유지하고 공용 component는 visible label·tone·accessible role만 렌더링한다.
  - emoji·외부 icon dependency 없이 currentColor SVG를 사용하고 실제 반복 책임만 component로 분리했다.
- Issues encountered:
  - 초기 status component patch 문맥이 맞지 않아 파일의 실제 상태를 다시 확인한 뒤 단일 구현으로 정리했다.
- Validation:
  - `uiComponents.test.ts`, layout component test를 포함한 Vitest 35 files/128 tests와 production build가 통과했다.
- Next steps:
  - 새 domain 화면이 실제 구현되기 전 미래 status·component를 선행 추가하지 않는다.
