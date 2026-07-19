# Progress

## Overview

사용자 기능별 form·상호작용 규칙을 page와 공용 기반에서 분리한다. 현재 P1 auth, P2 profile과 P3 agent-runs feature가 구현되어 있다.

## [2026-07-19] Session Summary (P4 documents feature 추가)

- What was done:
  - user-scoped document query key, filter canonicalization, upload·manual·reparse·delete와 Run monitor를 추가했다.
- Key decisions:
  - SSE disconnect는 실패가 아니며 terminal·WAITING_USER event는 REST query invalidation만 유도한다.
- Issues encountered:
  - WAITING_USER race를 detail invalidation으로 보정했다.
- Validation:
  - targeted 9 tests와 Frontend 전체 95 tests가 통과했다.
- Next steps:
  - P6 retrieval feature는 별도 phase로 남긴다.

## [2026-07-19] Session Summary (P3 agent-runs feature 추가)

- What was done:
  - filter·query·presentation·SSE controller와 detail panel·Progress Drawer를 추가했다.

- Key decisions:
  - Run 상태는 server projection만 사용하고 연결 상태를 별도 UI 안내로 관리한다.

- Issues encountered:
  - None.

- Validation:
  - feature contract·stream·query·component tests가 전체 check에서 통과했다.

- Next steps:
  - resource-specific 동작은 후속 domain feature에 둔다.

## [2026-07-19] Session Summary (P2 profile feature 경계 추가)

- What was done:
  - profile Zod, query key, version conflict와 공용 입력 component를 실제 page 사용처와 함께 추가했다.

- Key decisions:
  - 서버 권한·완료도는 Backend 응답을 사용하고 UI feature는 form·cache·표현 규칙만 소유한다.

- Issues encountered:
  - None

- Validation:
  - schema·query key·conflict 테스트와 frontend 전체 check가 통과했다.

- Next steps:
  - P3 이후 feature는 실제 API·화면 구현 시점에만 추가한다.

## [2026-07-19] Session Summary (P1 auth feature 경계 구성)

- What was done:
  - 인증 Form validation만 실제 사용처와 함께 추가했다.

- Key decisions:
  - P2 feature directory는 해당 화면·API 구현 시점에 생성한다.

- Issues encountered:
  - None

- Validation:
  - Frontend lint·typecheck·feature unit test가 통과했다.

- Next steps:
  - 새 기능은 route page와 API 계약이 함께 생길 때 추가한다.
