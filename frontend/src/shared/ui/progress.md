# Progress

## Overview

- 현재 구현 route가 공유하는 icon, page header, text status, loading·empty·error state와 pagination primitive가 있다.
- 공용 component는 domain 판단이나 API 호출을 소유하지 않고 접근 가능한 표현만 제공한다.

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
