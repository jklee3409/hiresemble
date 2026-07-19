# Progress

## Overview

P1에서 여러 HTTP 기능이 공유하는 오류, 보안, validation과 idempotency 기반을 관리한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (P1 공통 HTTP·보안·idempotency 기반 구현)

- What was done:
  - 동일 오류 field set, Request ID, Security/CSRF, UTF-8 validation과 만료 reclaim을 포함한 durable idempotency 서비스를 구현했다.

- Key decisions:
  - 실제 HTTP status와 직접 성공 DTO를 사용하고 auth endpoint에는 Idempotency-Key를 적용하지 않는다.

- Issues encountered:
  - Spring Boot 4.1 Jackson 3 bean과 PostgreSQL Instant binding 차이를 통합 테스트에서 발견해 호환 타입으로 수정했다.
  - 1차 validator에서 드러난 영구 replay 가능성을 PostgreSQL 조건부 upsert로 보정했다.

- Validation:
  - 공통 오류 parity, Request ID/log, validation과 idempotency replay·TTL·동시 reclaim 통합 테스트가 통과했다.

- Next steps:
  - 다음 idempotent endpoint가 도입될 때 현재 최소 service를 transaction 경계와 함께 확장한다.
