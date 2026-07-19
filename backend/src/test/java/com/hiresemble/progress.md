# Progress

## Overview

P1 인증·공통, P2 profile과 P3 Agent Run·AI runtime·migration 테스트를 기능별로 구성한다.

## [2026-07-19] Session Summary (P3 Agent Run·AI 테스트 추가)

- What was done:
  - Agent Run domain/runtime/API/SSE, AI registry·router·orchestrator와 V4 migration tests를 추가했다.
  - 공용 PostgreSQL cleanup을 P3 table까지 확장했다.

- Key decisions:
  - 실제 provider와 기존 개발 DB 대신 Testcontainers를 사용한다.

- Issues encountered:
  - P3 owner FK에 맞춰 공통 idempotency fixture를 확장했다.

- Validation:
  - 21 suites/243 tests가 모두 통과했다.

- Next steps:
  - 실제 typed resource integration은 P4 이후 추가한다.

## [2026-07-19] Session Summary (P2 프로필·migration 테스트 추가)

- What was done:
  - profile api/domain과 V3 migration 테스트를 추가하고 공유 cleanup을 P2 table까지 확장했다.

- Key decisions:
  - AC-02 HTTP·transaction은 실제 PostgreSQL에서, 순수 완료도·validation은 domain 단위로 검증한다.

- Issues encountered:
  - None

- Validation:
  - 9개 test class, 54개 test가 failure·error·skip 0으로 통과했다.

- Next steps:
  - P4 문서 경계는 실제 aggregate 구현 뒤 별도 테스트로 추가한다.

## [2026-07-19] Session Summary (P1 기능별 백엔드 테스트 구성)

- What was done:
  - auth, common, migration, support 테스트 영역을 추가했다.

- Key decisions:
  - 공유 context는 support에 제한하고 각 계약 assertion은 담당 기능 package에 둔다.

- Issues encountered:
  - None

- Validation:
  - Backend 전체 check와 Testcontainers migration 검증이 통과했다.

- Next steps:
  - P2 테스트도 사용자 소유권과 cross-user 실패를 기능별로 추가한다.
