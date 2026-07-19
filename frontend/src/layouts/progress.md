# Progress

## Overview

익명 인증 화면과 보호 화면의 공통 shell을 분리하고 구현된 profile navigation을 제공한다.

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
