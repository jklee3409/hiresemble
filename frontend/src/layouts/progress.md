# Progress

## Overview

form 우선 익명 인증 shell과 desktop sidebar·mobile drawer 보호 shell을 분리하고 `AI 작업 내역` navigation, Job Analysis tab과 lazy Agent Run Progress Drawer를 제공한다.

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
