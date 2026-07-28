# Progress

## Overview

- `main.css`가 Tailwind entry와 제품 design token, base·form·action·status·state pattern을 제공한다.
- Vite의 Tailwind plugin과 `main.ts`의 global import가 연결되어 있다.
- 미사용 PrimeVue Aura theme은 전역 초기화하지 않으며 실제 화면은 공용 token과 scoped style을 사용한다.

## [2026-07-28] Session Summary (Dashboard·Profile Content 폭 체계 보정)

- What was done:
  - 전역 content 최대 폭을 80rem, App sidebar를 15rem, Profile navigation 최대 폭을 14.5rem으로 조정해 1280~1920px에서 과도한 공백과 긴 행을 줄였다.
- Key decisions:
  - 기존 spacing·typography·button·input·focus token을 재사용하고 새 색상 체계나 UI dependency는 추가하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - Prettier, ESLint, production build와 1920·1440·1280·1024·768·390px Chromium fixture가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (Hiresemble Blue Control System 확정)

- What was done:
  - `#3157ff`를 단일 brand로 고정하고 blue 50~900, canvas·surface·ink·muted·border neutral을 명시했다.
  - button·select·checkbox·radio·switch·date·file·filter·profile workspace의 default, hover, focus-visible, disabled, invalid 상태와 44px target을 통합했다.
- Key decisions:
  - native semantics를 유지하고 custom arrow·appearance로 시각만 통일했으며 추가 UI dependency는 설치하지 않았다.
- Issues encountered:
  - 기존 cyan·teal·indigo alias는 page별 primary로 남지 않도록 Hiresemble Blue scale로 정리했다.
- Validation:
  - `pnpm check`, reduced-motion fixture, 네 viewport overflow와 screenshot 검수가 통과했다.
- Next steps:
  - 새 control 유형은 실제 두 화면 이상 사용될 때 같은 token 체계로 확장한다.

## [2026-07-28] Session Summary (Cobalt Brand Token·Motion System 재설계)

- What was done:
  - muted teal token을 electric cobalt, warm off-white, deep ink와 제한된 cyan·lime accent로 교체했다.
  - button·control·section·skeleton·progress의 짧은 motion과 reduced-motion 대안을 정리했다.
- Key decisions:
  - 외부 font·image·dependency 없이 CSS custom property와 transform·opacity 기반 motion만 사용한다.
- Issues encountered:
  - None.
- Validation:
  - Prettier·production build와 390px overflow, focus indicator, reduced-motion E2E가 통과했다.
- Next steps:
  - 새 semantic color는 실제 상태 사용처와 접근성 label이 함께 생길 때만 추가한다.

## [2026-07-27] Session Summary (제품 Design Token과 공용 Style 구축)

- What was done:
  - canvas·surface·ink·border·brand·semantic color, focus, radius, shadow, content width와 한국어 system typography token을 추가했다.
  - control·button·alert·status·section·skeleton·pagination과 reduced-motion 공통 pattern을 구축했다.
- Key decisions:
  - blue-teal 한 가지 brand 축과 저채도 semantic color를 사용하고 page 배치는 각 SFC의 scoped style에 둔다.
  - 모든 interactive element에 visible focus를 제공하고 skeleton animation은 `prefers-reduced-motion`에서 중단한다.
- Issues encountered:
  - 기존 화면에 indigo와 장문의 utility가 반복돼 금지 패턴 검색과 공통 class 사용 여부를 함께 점검했다.
- Validation:
  - 금지된 indigo·purple·glass·과도한 radius 패턴 0건, `git diff --check`와 production CSS build가 통과했다.
  - 핵심 본문·brand·semantic text/background 조합의 계산 대비는 모두 5.13:1 이상이었다.
- Next steps:
  - 새 component가 실제로 반복될 때만 token과 primitive를 확장한다.

## [2026-07-17] Session Summary (Tailwind 및 PrimeVue 전역 스타일 기반 구성)

- What was done:
  - 당시 구현 상태:
    - `main.css`에는 `@import 'tailwindcss';`만 존재한다.
    - Vite의 Tailwind plugin과 `main.ts`의 global import가 연결되어 있다.
    - PrimeVue Aura theme은 등록되어 있지만 제품 UI, 별도 design token, reset 또는 component style은 아직 없다.
  - 완료된 작업:
    - Tailwind CSS 진입점을 만들고 Vue 애플리케이션 bootstrap에서 불러오도록 구성했다.
    - PrimeVue Aura preset과 함께 사용할 수 있는 최소 전역 style 기반을 마련했다.
    - 작업 목적에 따라 `index.md`와 이 문서를 생성해 전역 style의 제한된 책임과 현재 상태를 기록했다.
  - 당시 진행 중인 작업:
    - 현재 작성 중인 design token, reset 또는 공통 style은 없다.
    - Style 영역의 초기 문서 추적 기반은 이번 작업에서 구성됐다.

- Key decisions:
  - 전역 CSS는 token, reset, 접근성 보조 등 애플리케이션 공통 책임으로 제한한다.
  - page/component별 표현은 가까운 component와 Tailwind utility에서 관리한다.
  - 실제 요구가 생기기 전 광범위한 theme override를 선행 구현하지 않는다.

- Issues encountered:
  - 제품 화면이 없어 Tailwind와 PrimeVue theme의 실제 조합, 반응형 동작, 접근성을 시각적으로 검증하지 못했다.
  - 현재 공통 token 정책이 없으므로 향후 화면별 임의 값이 늘지 않도록 기준이 필요하다.

- Validation:
  - 기본 검증 명령: `Set-Location frontend; corepack pnpm check`
  - 이 명령의 Prettier와 production build가 Markdown 형식과 CSS/Vite 처리 가능 여부를 함께 확인한다.
  - 명령은 성공했고 Vite production build에서 CSS asset이 생성됐다. 실제 UI visual/accessibility 검증은 화면이 없어 실행하지 않았다.

- Next steps:
  - 실제 화면 구현 시 필요한 공통 color, spacing, typography token 정책 확정
  - focus, contrast, reduced motion 등 접근성 기준 검증
  - PrimeVue theme과 Tailwind utility 사이의 token·override 경계 정리
  - 주요 layout과 상태 UI에 대한 반응형 style 검증
