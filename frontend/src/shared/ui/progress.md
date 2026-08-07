# Progress

## Overview

- 현재 구현 route가 공유하는 브랜드 lockup, icon, page header, text status, loading·empty·error state와 pagination primitive가 있다.
- 공용 component는 domain 판단이나 API 호출을 소유하지 않고 접근 가능한 표현만 제공한다.

## [2026-08-07] Session Summary (StatusBadge notice tone 추가)

- What was done:
  - `StatusBadge`의 tone union에 `notice`를 추가했다. 오류는 아니지만 확인해 두면 좋은 상태에 쓰며 색은 `main.css`의 `--color-notice` 계열을 따른다.
- Key decisions:
  - 마크업과 props 구조는 그대로 두고 tone 값만 늘려 기존 `uiComponents.test.ts` 기대를 유지했다.
- Issues encountered:
  - None.
- Validation:
  - `eslint .`, `prettier --check .`, `vue-tsc -b --force`, `vite build` 통과.
- Next steps:
  - None.

## [2026-08-06] Session Summary (공용 UI 표면 정리)

- What was done:
  - `AppNotifications`의 toast를 무테두리 + 큰 모서리로 바꿨다.
  - `PageHeader`, `StatePanel`, `StatusBadge`, `PaginationNav`는 마크업을 바꾸지 않고 전역 `main.css`의 새 표면 규칙(알약 pagination, 그림자 state panel, 무테두리 badge, pill eyebrow)을 그대로 따르게 했다.
- Key decisions:
  - 공용 component의 props와 DOM 구조를 유지해 `uiComponents.test.ts`의 기대를 그대로 만족시켰다.
- Issues encountered:
  - None.
- Validation:
  - `vite build` 성공, `prettier --check` 통과. Node 20 환경이라 `vitest`는 실행하지 못했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (Dashboard용 자체 제작 icon 8종 추가)

- What was done:
  - `AppIcon.vue`에 `rocket`, `flag`, `trend-up`, `bolt`, `pen`, `bookmark`, `trophy`, `compass`를 추가하고 `index.md`의 icon 설명을 갱신했다.
  - Dashboard 빠른 실행과 가이드 카드 category 매핑에서 실제로 사용하는 이름만 추가했다.
- Key decisions:
  - 기존과 같은 24×24 grid, stroke-width 1.8, `currentColor` 규칙을 유지하고 외부 icon dependency는 추가하지 않는다.
  - 사용처가 없는 이름은 만들지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `prettier --check`, `vite build` 통과. Node 20 환경이라 `vitest`는 실행하지 못했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석용 자체 제작 icon 15종 추가)

- What was done:
  - `AppIcon.vue`에 `target`, `half`, `cross`, `question`, `shield`, `spark`, `lift`, `evidence`, `chart`, `history`, `refresh`, `scale`, `flow`, `info`를 추가했다. 모두 24×24 grid, stroke-width 1.8, `currentColor` 기준이다.
- Key decisions:
  - 외부 icon library를 추가하지 않고 기존 `AppIcon` 단일 진입점을 유지한다.
  - match level 4종(`check`/`half`/`cross`/`question`)은 색을 제거해도 모양만으로 구분되도록 그렸다. 색 단독 인코딩을 피하기 위한 필수 조건이다.
- Issues encountered:
  - None.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `vite build` 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (두 번째 Hiresemble 로고 자산 적용)

- What was done:
  - 기존 inline H network SVG를 제공된 두 번째 Hiresemble 로고의 투명 256px PNG로 교체했다.
  - `BrandMark`의 full·compact·inverse와 이름 표시 옵션을 유지하고 장식 이미지의 빈 대체 text·drag 방지를 고정했다.
- Key decisions:
  - 각 layout에 이미지를 중복 배치하지 않고 공용 `BrandMark` 한 곳에서 승인 자산을 소유한다.
- Issues encountered:
  - None.
- Validation:
  - Shared UI와 Job Analysis 집중 Vitest 12건, Frontend 전체 67 files/279 tests와 production build가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Career person icon·PageHeader title slot)

- What was done:
  - AppIcon에 별도 `person-card` currentColor SVG를 추가하고 PageHeader에 fallback을 보존하는 named title slot을 제공했다.
- Key decisions:
  - Dashboard의 이름 부분 강조만 slot이 소유하며 기존 title prop 호출부는 변경 없이 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Shared UI unit test와 Frontend `pnpm check` 67 files/265 tests·build 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 공용 아이콘 확장)

- What was done:
  - 외부 asset 없이 calendar·guide·sparkle currentColor SVG icon을 `AppIcon`에 추가했다.
- Key decisions:
  - Dashboard 배치는 page가 소유하고 공용 icon은 의미 없는 장식 또는 호출부 accessible label과 함께 사용한다.
- Issues encountered:
  - None.
- Validation:
  - Frontend lint·typecheck·264 tests·build 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (제품 5단계 canonical journey 정의)

- What was done:
  - Landing과 Guide가 공유하는 번호·아이콘·제목·핵심 설명을 `productJourney.ts`에 추가했다.
- Key decisions:
  - 보호 route·action과 page별 preview는 공용 정의에 넣지 않고 각 page가 소유한다.
- Issues encountered:
  - None.
- Validation:
  - Landing·Guide component tests와 전체 typecheck·build가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (PageHeader 책임별 variant)

- What was done:
  - list·detail·editor·compact variant와 h1/h2 level 선택을 지원하도록 PageHeader를 확장했다.
- Key decisions:
  - 모든 페이지에 eyebrow·설명을 강제하지 않고 호출 화면이 필요한 계층만 전달한다.
- Issues encountered:
  - 기존 E2E heading level 기대를 새 semantic hierarchy에 맞췄다.
- Validation:
  - 관련 component·Browser heading assertions와 Frontend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (전역 toast·접근 가능한 확인 모달)

- What was done:
  - singleton notification service와 AppNotifications host를 추가해 성공/error toast와 alertdialog를 제공했다.
- Key decisions:
  - 모달은 취소 초기 focus, Tab trap, ESC/배경 취소, trigger focus 복귀를 보장한다.
- Issues encountered:
  - None.
- Validation:
  - AppNotifications component tests와 실제 삭제 모달 keyboard 흐름 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (면접 준비 navigation icon)

- What was done:
  - AppIcon allowlist에 면접 준비 navigation glyph를 추가했다.
- Key decisions:
  - link text가 접근 가능한 이름을 제공하고 icon은 장식 역할만 유지한다.
- Issues encountered:
  - None.
- Validation:
  - AppLayout component tests와 Frontend production build가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (자기소개서 navigation icon)

- What was done:
  - AppIcon의 현재 제품 allowlist에 자기소개서 navigation용 document-edit glyph를 추가했다.
- Key decisions:
  - 장식 icon은 accessible name을 소유하지 않고 실제 link text가 의미를 제공한다.
- Issues encountered:
  - 없음.
- Validation:
  - AppLayout component와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

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
