# Progress

## Overview

form 우선 익명 인증 shell과 desktop 상단 navigation·mobile bottom navigation 보호 shell을 분리하고 계정 메뉴, Job child tab과 lazy Agent Run Progress Drawer를 제공한다.

## [2026-08-06] Session Summary (app shell 알약 navigation과 계정 dropdown 개편)

- What was done:
  - `AppLayout.vue` 상단 탐색을 채움면 위 알약 tab 묶음으로 바꾸고 활성 링크 밑줄 표식을 흰 알약 + 그림자로 대체했다.
  - 계정 trigger에 원형 avatar를 추가하고, 계정 menu를 공용 `.menu-panel` 위에 identity 행, brand gradient 대표 진입점(이용 가이드), 아이콘이 붙은 menu 항목, 구분선, danger tone 로그아웃 순서로 재구성했다. `내 정보` 항목을 추가했다.
  - mobile bottom navigation을 화면 하단에 떠 있는 둥근 bar로 바꾸고 현재 여정을 brand 채움 알약으로 표시했다.
  - 좁은 화면에서 계정 이름만 숨기도록 selector를 좁혀 avatar가 함께 사라지던 문제를 막고, 존재하지 않는 class를 가리키던 `.progress-drawer-trigger` 규칙을 실제 `.run-progress__trigger-label`로 고쳤다.
- Key decisions:
  - menu 항목 순서와 DOM 종류(link/button)를 유지해 `.account-menu button[role="menuitem"]`을 첫 번째 button으로 찾는 기존 테스트를 깨지 않았다.
  - bottom navigation 항목 수(4개 링크 + 더보기)는 그대로 두었다. 레퍼런스의 가운데 FAB는 좁은 화면에서 6칸이 되어 터치 영역을 해쳐 도입하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - `vite build` 성공, `prettier --check` 통과. Node 20 환경이라 `vitest`는 실행하지 못했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 상세 모바일 tab 문구 축약)

- What was done:
  - 35rem 이하에서 `면접 준비` tab의 시각 문구를 `면접`으로 축약하고 기존 접근성 이름과 route 계약은 유지했다.
- Key decisions:
  - DOM text와 accessible name은 유지하고 CSS generated content로 작은 화면의 표시만 줄인다.
- Issues encountered:
  - None.
- Validation:
  - Frontend check와 Job Analysis·visual fixture Chromium 회귀 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 상세 tab·제목 위계 보정)

- What was done:
  - `JobDetailLayout.vue`에서 활성 tab의 채움 배경을 제거하고 밑줄과 brand 색만 남겼다. 공고명 `h1` 상한을 2.2rem에서 1.875rem으로 낮췄다.
- Key decisions:
  - tab이 button처럼 보이지 않도록 선택 상태는 밑줄로만 표시한다.
  - 분석 화면의 적합도 hero 숫자(52px)와 공고명이 경쟁하지 않도록 `h1`을 30px 상한으로 둔다. route, `aria-current`, sticky 동작은 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `prettier --check .`, `vite build` 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 resource header 두 줄 clamp·mobile 밀도 보정)

- What was done:
  - 긴 공고 제목의 ResizeObserver·hover slide·내부 가로 scroll을 제거하고 최대 두 줄 clamp와 native title 안내로 단순화했다.
  - mobile header의 metadata·status·source 배치를 압축하고 35rem 이하에서 보조 metadata를 생략해 분석 판단을 첫 viewport에 우선했다.
- Key decisions:
  - desktop navigation과 mobile bottom navigation, sticky Job tab 동작은 유지한다.
- Issues encountered:
  - None.
- Validation:
  - `JobDetailLayout.test.ts`, Job Analysis 1440/390px geometry·horizontal overflow와 전체 Frontend check 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 resource title 80% 축소)

- What was done:
  - 공고 상세 제목의 responsive font 범위를 `1.75–2.75rem`에서 `1.4–2.2rem`으로 축소했다.
- Key decisions:
  - 58rem 제목 영역, overflow 측정, hover·focus slide와 직접 가로 scroll은 유지했다.
- Issues encountered:
  - None.
- Validation:
  - 1440px computed 최대 크기 35.2px와 390px 문서 overflow 없음, Frontend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (긴 공고 제목 한 줄 slide)

- What was done:
  - 공고 resource header의 제목 영역을 58rem까지 넓히고 실제 overflow를 측정해 hover·focus 시 한 줄로 slide하도록 했다.
- Key decisions:
  - 제목은 keyboard focus와 직접 가로 scroll을 지원하고 `prefers-reduced-motion`에서는 transform을 비활성화한다.
- Issues encountered:
  - 비동기 Job 조회 뒤 생성되는 제목 element를 ResizeObserver에 연결하도록 query 갱신 watch에서 재관찰했다.
- Validation:
  - JobDetailLayout unit test와 긴 한국어 제목 Desktop·mobile Chromium overflow 회귀 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 폭·route focus outline 보정)

- What was done:
  - Dashboard route에만 88rem workspace 폭을 적용하고 programmatic focus main의 outline·box-shadow를 억제했다.
- Key decisions:
  - `tabindex=-1` main에만 selector를 제한하고 link·button·input의 전역 keyboard focus ring은 유지했다.
- Issues encountered:
  - JSDOM은 box-shadow computed value를 빈 문자열로 반환해 component source selector와 실제 Chromium computed style을 함께 검증했다.
- Validation:
  - AppLayout 5 tests와 Chromium workspace `outline:none`, `box-shadow:none` assertion 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (인증 shell Landing 복귀·title 책임 분리)

- What was done:
  - PublicLayout의 desktop·mobile 브랜드가 공개 Landing으로 돌아가는 기존 link를 회귀 고정하고 인증 canvas copy를 Landing과 구분했다.
  - AppLayout에서 route title 갱신을 제거해 Router 공통 hook이 모든 layout의 제목을 관리하게 했다.
- Key decisions:
  - PublicLayout은 login/signup form 전용 두 column 구조를 유지하고 Landing layout으로 확장하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - PublicLayout·AppLayout component test와 desktop/mobile 인증 shell Playwright가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (제품 navigation·공고 상세 shell 재설계)

- What was done:
  - sidebar·initial avatar·하단 profile card를 제거하고 desktop journey navigation, header account menu와 accessible mobile more sheet를 구현했다.
  - Job resource header와 sticky horizontal tab을 독립 layer로 만들고 body spacing token을 적용했다.
- Key decisions:
  - desktop 70rem 미만은 bottom navigation을 사용하고 route·닉네임·로그아웃·AI 작업 접근성은 유지한다.
- Issues encountered:
  - 390px gutter보다 큰 negative tab margin이 만든 4px overflow를 breakpoint별 bleed로 수정했다.
- Validation:
  - AppLayout·JobDetailLayout tests와 desktop/mobile Chromium navigation·focus·overflow 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 navigation·Job tab)

- What was done:
  - desktop/mobile 주요 navigation에 면접 준비를, Job detail tab에 면접 준비 route를 추가했다.
- Key decisions:
  - 기존 닉네임 Modal focus trap·Escape·body overflow와 route focus `preventScroll` 정책을 보존한다.
- Issues encountered:
  - None.
- Validation:
  - layout/router tests와 P8 actual desktop·mobile·200% scale focus/overflow 검증이 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (상단 닉네임 수정 Modal)

- What was done:
  - header 닉네임과 mobile drawer 사용자 영역을 닉네임 수정 Modal trigger로 연결했다.
- Key decisions:
  - Modal은 validation, Escape·Tab focus trap, body scroll lock과 trigger focus 복원을 소유한다.
- Issues encountered:
  - CSS media query 닫힘 누락을 production build에서 발견해 보정했다.
- Validation:
  - `AppLayout.test.ts`의 Modal 저장·focus 회귀와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (Route focus scroll 간섭 제거)

- What was done:
  - 보호 route 전환 뒤 main focus가 페이지 상단 scroll 결정을 덮어쓰지 않도록 `focus({ preventScroll: true })`를 적용했다.
- Key decisions:
  - Keyboard focus 이동은 유지하고 scroll 위치는 Router가 단독으로 결정한다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 전체 check와 실제 profile 첫 진입 `scrollY=0` 검수가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 자기소개서 navigation·Job tab)

- What was done:
  - AppLayout desktop/mobile navigation에 자기소개서를 추가하고 JobDetailLayout에 공고 정보·공고 분석·자기소개서 세 tab을 연결했다.
- Key decisions:
  - AI 작업 내역은 별도 보조 메뉴로 유지하고 P8 면접 준비 tab은 만들지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - layout/router component tests와 P7 actual 1440/390px overflow·keyboard navigation이 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 Job tab·AI 작업 내역 navigation)

- What was done:
  - Job detail에 공고 정보·공고 분석 두 tab을 추가하고 sidebar·mobile navigation 용어를 `AI 작업 내역`으로 바꿨다.
- Key decisions:
  - active tab과 `aria-current`는 route로 계산하며 자기소개서·면접 가짜 tab은 만들지 않는다.
- Issues encountered:
  - None.
- Validation:
  - layout/router unit test와 1440px·390px Chromium keyboard/overflow 검증이 통과했다.
- Next steps:
  - P7/P8 tab은 각 phase 구현 뒤 추가한다.

## [2026-07-28] Session Summary (지원 홈 Navigation 명칭 통합)

- What was done:
  - Sidebar의 `오늘의 준비`를 `지원 홈`으로 바꾸고 desktop·mobile brand link의 접근성 이름과 Dashboard context를 같은 목적어로 정리했다.
- Key decisions:
  - route path와 navigation 구조는 유지하고 표시 명칭과 설명만 서비스의 지원 관리 목적에 맞췄다.
- Issues encountered:
  - None.
- Validation:
  - AppLayout unit test와 Chromium desktop·mobile navigation fixture가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (초대형 화면 인증 레이아웃 간격 보정)

- What was done:
  - `PublicLayout`의 소개·인증 column 최대 폭과 grid 정렬을 제한해 넓은 화면에서 두 영역이 과도하게 멀어지지 않게 했다.
- Key decisions:
  - mobile·tablet breakpoint와 인증 form 동작은 유지하고 desktop grid constraint만 조정했다.
- Issues encountered:
  - None.
- Validation:
  - 2500×1160 로그인 화면 직접 캡처와 fixture UI shell 3/3, Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (지원 준비 중심 App navigation 통합)

- What was done:
  - 보호 navigation을 오늘의 준비·내 지원 정보·이력서·자료·관심 공고·분석 기록으로 통일하고 brand accent를 Hiresemble Blue로 정리했다.
- Key decisions:
  - route path, drawer focus trap·Escape·trigger focus 복원과 active Run count 계약은 유지했다.
- Issues encountered:
  - None.
- Validation:
  - layout test와 1440·1024·768·390px drawer·overflow fixture가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (분석 기록 navigation 용어 적용)

- What was done:
  - 보호 shell navigation과 progress drawer accessible name을 분석 기록·진행 중인 분석으로 변경했다.
- Key decisions:
  - drawer focus trap, Escape, trigger 복원과 responsive layout은 유지했다.
- Issues encountered:
  - None.
- Validation:
  - Layout tests와 UI shell Playwright 3/3가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (B2C Brand Canvas·App Shell 재구성)

- What was done:
  - PublicLayout을 비대칭 form surface와 움직이는 node·orbit canvas로 바꾸고 AppLayout에 같은 mark·palette·AI 작업 용어를 적용했다.
- Key decisions:
  - 모바일은 form을 먼저 읽고 artwork를 compact section으로 유지하며 drawer focus trap·Escape·trigger 복원을 그대로 보존한다.
- Issues encountered:
  - desktop sidebar brand 이름이 좁아지는 문제를 After 캡처에서 확인해 lockup과 context를 세로로 분리했다.
- Validation:
  - layout component test, fixture Playwright 1440·1024·768·390px와 headed 로그인 1440·390px 검수가 통과했다.
- Next steps:
  - 실제 긴 사용자 이름·이메일은 cross-stack 데이터로 추가 시각 검수한다.

## [2026-07-27] Session Summary (Responsive 제품 App Shell 개선)

- What was done:
  - `AppLayout`을 desktop sticky sidebar·compact header와 mobile modal drawer로 재구성하고 active route, 사용자, logout과 Run count를 정리했다.
  - `PublicLayout`을 mobile form 우선·desktop 2열 인증 구조로 통일하고 Job detail에는 현재 overview만 표시했다.
- Key decisions:
  - navigation은 dashboard·profile·documents·jobs·Agent Run만 제공하고 onboarding은 사용자 영역의 보조 동선으로 유지한다.
  - drawer는 Escape·Tab focus trap, close 후 trigger focus 복원과 body scroll 제어를 제공한다.
- Issues encountered:
  - 기존 Agent Run drawer의 접근성 이름이 변경된 것을 루트 통합 검토에서 발견해 기존 이름으로 복구했다.
- Validation:
  - `AppLayout.test.ts`, `PublicLayout.test.ts`와 1440·1024·768·390px Playwright shell 검증이 통과했다.
- Next steps:
  - P6 navigation은 실제 route가 구현된 뒤에만 추가한다.

## [2026-07-27] Session Summary (Jobs navigation·detail layout 추가)

- What was done:
  - AppLayout에 Job 목록 진입점을 추가하고 `/jobs/:jobId` child route용 `JobDetailLayout`을 추가했다.
- Key decisions:
  - P6 분석 tab은 route와 화면이 구현될 때 추가한다.
- Issues encountered:
  - 없음.
- Validation:
  - Router unit test와 Frontend production build가 통과했다.
- Next steps:
  - P6에서 현재 outlet 아래 analysis child route를 추가한다.

## [2026-07-19] Session Summary (Documents navigation 추가)

- What was done:
  - 보호 layout navigation에 문서 목록 진입점을 추가했다.
- Key decisions:
  - 기존 Progress Drawer와 auth cleanup 경계를 유지한다.
- Issues encountered:
  - None.
- Validation:
  - route·layout component 회귀와 Frontend 전체 check가 통과했다.
- Next steps:
  - P5 이후 navigation은 해당 page 구현 뒤 추가한다.

## [2026-07-19] Session Summary (P3 Agent Run Progress Drawer 추가)

- What was done:
  - AppLayout header에 dynamic import Drawer와 작업 기록 navigation을 추가했다.

- Key decisions:
  - count는 owner-scoped active Agent Run 목록의 `totalElements`를 그대로 사용하고 최근 항목만 최대 5개로 제한해 Dashboard 집계처럼 추정하지 않는다.

- Issues encountered:
  - None.

- Validation:
  - production build에서 Drawer가 2.33 kB/gzip 1.30 kB lazy chunk로 분리됐다.

- Next steps:
  - 전체 system count가 필요하면 P10 Dashboard API 계약 뒤 구현한다.

## [2026-07-19] Session Summary (AppLayout profile navigation 추가)

- What was done:
  - 보호 layout에 profile 진입 링크를 추가하고 기존 logout action을 유지했다.

- Key decisions:
  - page별 프로필 데이터와 form state는 layout이 소유하지 않는다.

- Issues encountered:
  - None

- Validation:
  - layout을 사용하는 router·auth flow 회귀와 frontend 전체 check가 통과했다.

- Next steps:
  - 미구현 기능 메뉴는 해당 phase 전까지 노출하지 않는다.

## [2026-07-19] Session Summary (Public·App Layout 구현)

- What was done:
  - 인증 Form shell과 현재 사용자·logout을 제공하는 보호 shell을 구현했다.

- Key decisions:
  - App navigation은 P1에서 실제 존재하는 dashboard·onboarding route만 노출한다.

- Issues encountered:
  - logout 401 후 보호 shell 잔류 가능성을 router auth reset 구독으로 해소했다.

- Validation:
  - Route·logout 401 component test와 Frontend check가 통과했다.

- Next steps:
  - P2 navigation은 실제 route가 구현될 때 점진적으로 추가한다.
