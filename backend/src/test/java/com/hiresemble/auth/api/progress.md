# Progress

## Overview

P1 다섯 인증 endpoint와 OpenAPI의 실제 Spring 통합 계약을 검증한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (P1 인증·OpenAPI 통합 테스트 구현)

- What was done:
  - UTF-8 경계, BCrypt, transaction, Session rotation/logout, 두 사용자, 오류 parity와 Session 실패 원자성을 검증했다.

- Key decisions:
  - profile·Session SQL·deferred commit 실패는 test-only PostgreSQL trigger로 유발하고 production test endpoint는 추가하지 않는다.

- Issues encountered:
  - 초기 disabled CSRF token 형식과 Jackson bean 차이를 실제 context test에서 발견해 수정했다.
  - 1차 validator의 Session transaction 지적을 일회성 persistence 실패와 Session mutation 뒤 commit 실패로 각각 재현했다.

- Validation:
  - AuthIntegrationTest 15개와 OpenApiContractTest 2개가 통과했다.

- Next steps:
  - P2 owner 404는 실제 resource endpoint가 생긴 뒤 별도 두 사용자 fixture로 추가한다.
