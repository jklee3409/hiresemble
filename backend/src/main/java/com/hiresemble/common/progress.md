# Progress

## Overview

P1~P3 HTTP 기능이 공유하는 오류, OpenAPI, 보안, validation과 idempotency 기반을 관리한다.

## [2026-07-19] Session Summary (P4 prepared idempotency transaction 확장)

- What was done:
  - Document Object preparation과 DB mutation 사이에 atomic idempotency completion·compensation 경계를 추가했다.

- Key decisions:
  - 기존 호출자 계약을 바꾸지 않고 P4 upload가 명시적으로 선택하는 별도 실행 경계로 한정했다.

- Issues encountered:
  - None

- Validation:
  - 전체 Backend 30 suites/287 tests와 실제 Browser E2E 4/4가 통과했다.

- Next steps:
  - P5 이후 외부 side effect mutation은 적용 전 보상 가능성을 별도로 검토한다.

## [2026-07-19] Session Summary (P3 Agent Run 공통 오류·OpenAPI·idempotency 확장)

- What was done:
  - budget 429와 retry conflict 오류, Agent Run OpenAPI tag·CSRF security를 추가했다.
  - idempotency completion에 safe resource·agentRun metadata를 저장하도록 확장했다.

- Key decisions:
  - 기존 공통 오류 6개 field와 실제 HTTP status를 유지한다.

- Issues encountered:
  - V4 owner FK에 맞춰 idempotency integration fixture를 보정했다.

- Validation:
  - OpenAPI 35 operation/24 path와 기존 P1·P2 오류 회귀가 통과했다.

- Next steps:
  - 후속 idempotent domain mutation도 동일 metadata 경계를 사용한다.

## [2026-07-19] Session Summary (P2 profile 공통 오류·OpenAPI 확장)

- What was done:
  - profile resource·version 오류와 P2 OpenAPI schema를 공통 기반에 연결했다.

- Key decisions:
  - P1 여섯 field 오류·Session·CSRF 계약을 변경하지 않는다.

- Issues encountered:
  - None

- Validation:
  - P1 회귀와 30-operation OpenAPI 테스트가 통과했다.

- Next steps:
  - 도메인 정책은 각 기능 영역에 유지한다.

## [2026-07-19] Session Summary (공통 OpenAPI·Swagger 보안 scheme 추가)

- What was done:
  - 공통 OpenAPI 설정에 API 정보와 `sessionCookie`·`csrfToken` scheme를 정의하고 인증 Controller가 이를 재사용하도록 구성했다.
  - 상위 공통 인덱스가 새 OpenAPI 책임을 안내하도록 갱신했다.

- Key decisions:
  - logout은 두 scheme를 같은 security requirement 객체에 넣어 AND로 표현하고 기존 Security runtime은 변경하지 않는다.

- Issues encountered:
  - OpenAPI security 배열에서 별도 requirement 객체는 OR이므로 최소 customizer로 logout 계약을 고정해야 했다.

- Validation:
  - Backend 33개 테스트와 read-only validator 판정이 공통 scheme, logout AND와 기존 runtime 보존을 확인했다.

- Next steps:
  - 후속 공개 Controller는 백엔드 개발 규칙에 따라 같은 scheme와 계약 테스트를 재사용한다.

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
