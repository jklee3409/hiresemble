# Progress

## Overview

PostgreSQL에 저장되는 P1 idempotency reservation·hash·replay 계약을 검증한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (Durable idempotency 통합 테스트 구현)

- What was done:
  - 같은 hash replay, IN_PROGRESS 충돌, 다른 hash 409, 새 service instance replay와 만료 reclaim 검증을 구현했다.

- Key decisions:
  - validation·authentication·ownership 실패는 test application fixture에서 reservation 전 발생하도록 검증한다.
  - 만료 동시성은 첫 operation을 latch로 고정해 두 번째 요청이 새 operation에 진입하지 못함을 확인한다.

- Issues encountered:
  - Instant JDBC binding 실패를 UTC OffsetDateTime으로 교정했다.
  - 1차 validator가 24시간 뒤 key 재사용 test 공백을 발견해 만료 row를 SQL로 backdate한 회귀 test를 추가했다.

- Validation:
  - IdempotencyIntegrationTest 8개가 통과했다.

- Next steps:
  - 첫 실제 적용 endpoint에서 replay header와 business transaction 통합 test를 추가하고 linked run terminal reconciliation을 연결한다.
