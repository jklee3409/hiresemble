# Progress

## Overview

현재 인증 사용자와 unknown·authenticated·anonymous 상태, 표시 이름 갱신과 account deletion 완료 cleanup을 Pinia로 관리한다.

## [2026-08-09] Session Summary (Account deletion auth cleanup)

- What was done: 202 account deletion 뒤 공통 session cleanup을 거쳐 auth/user state를 anonymous로 reset하는 action을 추가했다.
- Key decisions: deletionRequestId와 password를 store에 보관하지 않는다.
- Issues encountered: 없음.
- Validation: auth/account page test와 Frontend 전체 check가 통과했다.
- Next steps: logout·401·deletion cleanup 순서를 분기별로 중복 구현하지 않는다.

## [2026-07-31] Session Summary (현재 사용자 닉네임 projection 갱신)

- What was done:
  - Auth store에 display-name update action을 추가하고 성공한 `CurrentUserDto`로 header·sidebar 소비 projection을 즉시 교체했다.
- Key decisions:
  - 닉네임만 store에 별도 복제하지 않고 current user DTO를 원자적으로 갱신한다.
- Issues encountered:
  - None.
- Validation:
  - Store projection test와 Frontend 53 files/214 tests가 통과했다.
- Next steps:
  - None.

## [2026-07-19] Session Summary (P1 auth store 구현)

- What was done:
  - 세 상태 bootstrap, Session 수립·종료, 401와 사용자 전환 cleanup을 구현했다.

- Key decisions:
  - 다른 user가 인증되면 이전 query cache와 draft를 폐기한 뒤 새 Session csrf·사용자를 설정한다.

- Issues encountered:
  - logout 요청 자체가 401인 경우도 방어적으로 사용자 경계를 reset하도록 보완했다.

- Validation:
  - auth store 401·logout·두 사용자 cache/draft 격리 test가 통과했다.

- Next steps:
  - P2 store는 전역 client 상태가 필요한 경우에만 별도 정의한다.
