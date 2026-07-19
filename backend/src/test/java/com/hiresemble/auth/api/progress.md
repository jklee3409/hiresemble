# Progress

## Overview

P1 다섯 인증 endpoint 회귀와 P1·P2 OpenAPI·Swagger UI의 실제 Spring 통합 계약을 검증한다.

## [2026-07-19] Session Summary (P2 포함 30-operation OpenAPI 검증)

- What was done:
  - exact operation 수를 30으로 확장하고 프로필 path·schema·enum·nullability와 P2 밖 endpoint 부재를 검증했다.

- Key decisions:
  - 인증 공개 endpoint는 기존 5개로 고정하고 프로필·direct evidence 25개만 추가한다.

- Issues encountered:
  - None

- Validation:
  - OpenAPI contract test와 Swagger UI 회귀가 Backend check에서 통과했다.

- Next steps:
  - 후속 phase 공개 operation은 해당 계약·Controller가 함께 구현될 때만 snapshot에 추가한다.

## [2026-07-19] Session Summary (Swagger metadata·security·UI 계약 테스트 보강)

- What was done:
  - OpenAPI info/tag, stable operationId, response code/schema, 안전한 request example과 hidden framework parameter를 검증했다.
  - 두 security scheme와 endpoint별 requirement, logout의 단일 AND 객체를 검증했다.
  - 익명 Swagger UI redirect·HTML과 swagger-config의 Try It Out 설정을 검증했다.

- Key decisions:
  - exact five-path와 직접 DTO/no-envelope assertion을 유지하면서 문서·UI 계약을 같은 integration class에 확장했다.

- Issues encountered:
  - logout security 배열이 여러 객체면 OR라는 의미 오류를 명시적 assertion으로 차단했다.

- Validation:
  - `OpenApiContractTest` 4개와 Backend 전체 33개 테스트가 통과했다.

- Next steps:
  - 후속 Controller 추가 시 path·operation·schema·security·UI 계약을 함께 확장한다.

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
