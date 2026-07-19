# Progress

## Overview

여러 화면이 공유하는 P1 인증·P2 프로필·P3 Agent Run HTTP 계약과 Session cleanup port를 관리한다.

## [2026-07-19] Session Summary (P3 Agent Run 공용 API·Session 경계 연결)

- What was done:
  - Agent Run DTO/client를 추가하고 SSE controller를 기존 Session cleanup port에 연결했다.

- Key decisions:
  - transport와 lifecycle만 shared에 두고 화면 상태는 feature/page가 소유한다.

- Issues encountered:
  - None.

- Validation:
  - typed API와 logout·401·사용자 전환 cleanup tests가 통과했다.

- Next steps:
  - 후속 resource API도 user-scoped query key를 사용한다.

## [2026-07-19] Session Summary (P2 profile 공용 API 확장)

- What was done:
  - 공용 API 영역에 profile TypeScript 계약과 25개 endpoint consumer를 추가했다.

- Key decisions:
  - 기존 logout·401·사용자 전환 cleanup 순서를 profile Vue Query cache에도 적용한다.

- Issues encountered:
  - None

- Validation:
  - API·cache 분리 테스트와 frontend 전체 check, 실제 사용자 전환 E2E가 통과했다.

- Next steps:
  - P3 이후 transport는 실제 사용처와 함께 추가한다.

## [2026-07-19] Session Summary (P1 공용 API·Session 기반 구성)

- What was done:
  - typed 인증 client와 사용자 경계 cleanup coordinator를 추가했다.

- Key decisions:
  - 직접 성공 DTO를 사용하고 401/403/409를 안정적 code로 구분한다.

- Issues encountered:
  - None

- Validation:
  - HTTP adapter와 cleanup ordering unit test가 통과했다.

- Next steps:
  - 새 공용 책임은 두 실제 사용처가 생긴 뒤 최소 범위로 추가한다.
