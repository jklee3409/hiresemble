# Progress

## Overview

PostgreSQL에 저장되는 idempotency reservation·hash·원 status/응답 replay 계약을 검증한다.

## [2026-07-28] Session Summary (로컬 HMAC 기본 설정 회귀 추가)

- What was done:
  - 전역 default profile이 없고 local profile 리소스에 비어 있지 않은 개발 전용 HMAC 키가 있는지 검증했다.
  - active key가 비어 있으면 startup property 초기화가 실패하는지 검증했다.
- Key decisions:
  - Document 테스트의 동적 test key와 별도로 실제 기본 설정 파일을 직접 검사한다.
- Issues encountered:
  - None.
- Validation:
  - IdempotencyLocalConfigurationTest, IdempotencyPropertiesTest와 Backend 전체 `check`가 통과했다.
- Next steps:
  - profile 또는 환경 변수 구조 변경 시 비로컬 secret 요구 계약을 함께 검토한다.

## [2026-07-27] Session Summary (원 HTTP status replay 회귀 추가)

- What was done:
  - application callback이 반환한 201/202 status와 응답을 동일 key에서 보존하는 테스트를 추가했다.
- Key decisions:
  - replay는 새 resource나 Run을 생성하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Idempotency 통합 테스트와 Job 생성 replay 테스트가 통과했다.
- Next steps:
  - 없음.

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
