# Progress

## Overview

사용자 기능별 form·상호작용 규칙을 page와 공용 기반에서 분리한다. 현재 P1 auth부터 P5 jobs feature까지 구현되어 있다.

## [2026-07-27] Session Summary (현재 Feature 상태 표현 통일)

- What was done:
  - profile navigation·chip·conflict, Document evidence/Run monitor, Job conflict/Run monitor와 Agent Run detail/drawer의 정보 계층을 개선했다.
- Key decisions:
  - query·mutation·SSE state machine은 유지하고 visible label, action priority와 responsive presentation만 변경했다.
- Issues encountered:
  - 동일 색상처럼 보일 수 있는 상태는 공용 label과 보조 설명을 함께 사용하도록 조정했다.
- Validation:
  - feature unit/component test와 fixture Agent Run E2E가 통과했다.
- Next steps:
  - 미구현 P6 feature를 디자인 목적으로 선행 생성하지 않는다.

## [2026-07-27] Session Summary (P5 jobs feature 추가)

- What was done:
  - Job filter·query·mutation·Run monitor·version conflict feature를 추가했다.
- Key decisions:
  - REST Job 상태를 원천으로 삼고 SSE는 terminal·WAITING_USER invalidation 신호로만 사용한다.
- Issues encountered:
  - P6 분석 DTO 선행 구현과 NEEDS_MANUAL_INPUT retry 노출을 validator 보정에서 제거했다.
- Validation:
  - Frontend 32 files/122 tests와 P5 Browser E2E 5/5가 통과했다.
- Next steps:
  - P6 analysis feature는 새 계약 이후 추가한다.

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
