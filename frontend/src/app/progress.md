# Progress

## Overview

Vue 애플리케이션이 공유하는 Pinia, TanStack Query 인스턴스와 build-time feature flag 구성을 관리한다.

## [2026-08-08] Session Summary (GitHub Source build-time flag)

- What was done:
  - `VITE_GITHUB_SOURCE_ENABLED`가 정확히 `true`일 때만 활성화되는 typed flag를 추가했다.
- Key decisions:
  - 누락·대소문자·공백·다른 값은 모두 false이며 Backend capability endpoint나 runtime store를 만들지 않는다.
- Issues encountered:
  - None.
- Validation:
  - flag on/off 단위 테스트와 Frontend 전체 `corepack pnpm check` 통과.
- Next steps:
  - None.

## [2026-07-19] Session Summary (P1 Pinia·QueryClient bootstrap 구현)

- What was done:
  - 공유 Pinia와 4xx 재시도 금지·mutation retry false QueryClient를 추가했다.

- Key decisions:
  - 서버 상태는 Vue Query, 현재 인증 사용자만 Pinia가 소유한다.

- Issues encountered:
  - None

- Validation:
  - Frontend typecheck와 QueryClient retry test가 통과했다.

- Next steps:
  - P2 query key는 사용자 ID namespace를 포함해 기능별 module에서 정의한다.
