# Progress

## Overview

- `main.css`가 Tailwind entry와 제품 design token, base·form·action·status·state pattern을 제공한다.
- Vite의 Tailwind plugin과 `main.ts`의 global import가 연결되어 있다.
- 미사용 PrimeVue Aura theme은 전역 초기화하지 않으며 실제 화면은 공용 token과 scoped style을 사용한다.

## [2026-08-06] Session Summary (목록 pagination 여백 보정)

- What was done:
  - `.pagination`에 `margin-top: var(--space-5)`를 넣었다. 테두리를 없애면서 예전 `border-top` + `padding-top`이 만들던 목록과의 간격이 사라져 카드에 붙어 보였다.
- Key decisions:
  - 화면마다 여백을 다시 넣지 않도록 공용 class에서 한 번에 해결했다.
- Issues encountered:
  - None.
- Validation:
  - `vite build`, `eslint .`, `prettier --check` 통과.
- Next steps:
  - None.

## [2026-08-06] Session Summary (soft surface design system 전환)

- What was done:
  - `main.css`의 시각 언어를 테두리 중심에서 "부드러운 표면"으로 바꿨다. canvas를 `#eef0f6`으로 낮추고 `--radius-*`를 키웠으며, `--shadow-xs|sm|md|panel|lift`를 2겹 그림자로 재정의하고 `--shadow-brand`·`--shadow-brand-hover`, 누락되어 있던 `--radius-xl`, `--font-display`를 추가했다.
  - `.section-surface`, `.data-card`, `.data-list`, `.filter-toolbar`, `.state-panel`, `.alert`, `.status-badge`, `.pagination`, `.back-link`에서 외곽 1px 테두리를 없애고 그림자 또는 채움면으로 표면을 표현했다.
  - `.button`을 알약(999px) 형태로 바꾸고 primary에 brand glow를 부여했다. `.control`은 채움면 + 투명 테두리로 시작해 focus에서만 흰 배경과 brand ring으로 올라온다.
  - 레퍼런스에서 가져온 공용 class `.icon-tile(--sm|lg|neutral|success|warning|danger|solid)`, `.menu-panel`·`.menu-panel__item(--danger)`·`.menu-panel__divider`, `.pill-tabs`·`.pill-tab`을 추가했다.
- Key decisions:
  - 기존 brand hue(`#3157ff`)와 token 이름을 모두 유지하고 값만 조정해, token을 쓰는 화면이 자동으로 새 언어를 따르게 했다.
  - 1px 실선은 카드 외곽이 아니라 카드 내부 구분선에만 허용한다는 규칙을 파일 상단 주석에 남겼다.
  - `--radius-xl`은 그동안 제품 token이 아니라 Tailwind 기본 theme 값(0.75rem)에 의존하고 있었다. Dashboard가 이 이름을 쓰고 있어 제품 token으로 명시 선언했다.
  - 본문 font는 이미 의존성에 있던 `@fontsource-variable/noto-sans-kr`를 `main.ts`에서 전역 import하고, 사용자 환경에 있으면 Pretendard가 먼저 잡히도록 stack 앞에 두었다. 신규 의존성은 추가하지 않았다.
- Issues encountered:
  - 로컬 Node가 20.18.0이라 `corepack pnpm`(Node 22.13+ 요구)과 `vitest`(jsdom ESM)를 실행할 수 없었다.
- Validation:
  - `node node_modules/vite/bin/vite.js build` 성공, `node node_modules/prettier/bin/prettier.cjs --check "src/**/*.{vue,css,ts}"` 통과. 실행 중인 dev server에서 token·button·control의 computed style을 직접 확인했다. `vitest`와 `vue-tsc`는 Node 버전 제약으로 실행하지 못했다.
- Next steps:
  - Node 22 이상 환경에서 `corepack pnpm check` 전체를 재실행한다.

## [2026-08-05] Session Summary (Dashboard 개편용 motion·강조 token 추가)

- What was done:
  - `main.css` `:root`에 `--motion-slow`, `--ease-emphasized`, `--shadow-lift`, 어두운 brand surface 전용 `--color-onbrand-accent`·`--color-onbrand-accent-strong`를 추가했다.
- Key decisions:
  - Dashboard가 SFC에 하드코딩하던 `#dff9b8`을 token으로 승격하고, 밝은 surface 본문에는 쓰지 않는다는 제약을 주석으로 남겼다.
  - 기존 token 값과 이름은 그대로 두고 additive로만 확장했다.
- Issues encountered:
  - None.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `prettier --check`, `vite build` 통과. Node 20 환경이라 `vitest`는 실행하지 못했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석 개편용 design token 추가)

- What was done:
  - `main.css` `:root`에 채움면 체계(`--color-fill`, `--color-fill-strong`, `--color-ink-title`), `--radius-pill`, `--radius-panel`, `--font-size-3xl`, `--font-size-hero`, `--shadow-panel`과 차트 전용 status 팔레트(`--chart-matched|partial|missing|unknown|brand|track|grid`)를 추가했다.
- Key decisions:
  - 기존 token은 값과 이름을 모두 유지하고 additive로만 확장해 다른 화면 회귀를 만들지 않는다.
  - 차트 마크 채움에는 기존 semantic token을 재사용하지 않는다. 색각 검증에서 적-주황 쌍이 deutan ΔE 1.7로 실패했기 때문이며, 사유를 주석으로 남겼다.
- Issues encountered:
  - None.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `prettier --check .`, `vite build` 통과. Node 20 환경이라 `vitest`는 실행하지 못했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (B2C design token·수직 리듬 정비)

- What was done:
  - 기존 blue hue를 유지하며 brand·neutral·semantic color, typography, spacing, radius, elevation, focus, control height와 page/tab/section gap token을 정리했다.
- Key decisions:
  - primary는 CTA·active·link에 제한하고 문서형 정보는 divider·spacing으로 계층을 만든다.
- Issues encountered:
  - reduced-motion과 390px overflow를 함께 회귀 검증했다.
- Validation:
  - lint·Prettier·production build, desktop/mobile Chromium visual·overflow 통과.
- Next steps:
  - None.

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
