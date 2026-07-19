# Progress

## Overview

익명 인증 화면과 보호 화면의 공통 shell을 PublicLayout·AppLayout으로 분리한다. 현재 P1 구현과 검증 상태만 기록한다.

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
