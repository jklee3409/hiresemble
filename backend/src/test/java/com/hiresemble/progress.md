# Progress

## Overview

P1 인증·공통 기반·migration과 공유 Testcontainers fixture 테스트를 기능별로 구성한다. 현재 P1 구현과 검증 상태만 기록한다.

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
