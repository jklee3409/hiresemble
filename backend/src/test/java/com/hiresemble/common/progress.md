# Progress

## Overview

P1 ErrorCode, UTF-8 validation과 durable idempotency 테스트를 책임별로 구성한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (공통 기반 테스트 영역 구성)

- What was done:
  - 오류 code, validation과 idempotency 전용 test package를 추가했다.

- Key decisions:
  - idempotency 적용 전 실패 순서는 test application fixture로 검증한다.

- Issues encountered:
  - None

- Validation:
  - 공통 테스트 8개가 Backend check에서 통과했다.

- Next steps:
  - 새 공통 기능은 실제 두 사용처가 생길 때 해당 회귀 test를 추가한다.
