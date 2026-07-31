# Progress

## Overview

P1 다섯 인증 endpoint와 계정 닉네임 변경 회귀, P1~P7 및 Agent Run history delete OpenAPI·Swagger UI의 실제 Spring 통합 계약을 검증한다.

## [2026-07-31] Session Summary (73-operation Agent Run 삭제 OpenAPI)

- What was done:
  - 개별 DELETE와 bulk-delete operation, response code, Session+CSRF requirement를 exact path 기준선에 추가했다.
- Key decisions:
  - 공개 Spring mapping과 생성 OpenAPI를 정확히 73 operations/53 paths로 고정했다.
- Issues encountered:
  - None.
- Validation:
  - `OpenApiContractTest` 5 tests와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (닉네임 변경·71-operation OpenAPI)

- What was done:
  - 닉네임 trim·DB 저장·두 Session 최신 projection과 validation·401·403 회귀를 추가했다.
  - 실제 Spring mapping과 생성 OpenAPI를 71 operations/52 paths, request·response·security schema로 고정했다.
- Key decisions:
  - Session principal이 과거 닉네임을 보유한 조건을 두 번째 login으로 재현하고 `/auth/me` DB projection을 검증한다.
- Issues encountered:
  - 새 operation description 누락으로 첫 OpenAPI test가 실패했고 annotation 보완 뒤 재검증에 통과했다.
- Validation:
  - `.\gradlew.bat check --console=plain --no-daemon`: 54 suites/382 tests, failure·error·skip 0.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 VerificationDto suggestion schema 경계)

- What was done:
  - 생성 OpenAPI의 `VerificationDto.suggestions`가 `maxItems=20`, item `minLength=1`, `maxLength=1000`을 노출하는지 직접 검증한다.
- Key decisions:
  - 공개 schema의 array/item 제약도 DTO field·operation count와 같은 안정 계약으로 취급한다.
- Issues encountered:
  - 1차 validator가 누락된 array/item schema 제약을 MAJOR 계약 불일치로 식별했다.
- Validation:
  - OpenAPI integration test와 전체 Backend 54 suites/380 tests가 통과했고 70 operations/51 paths를 유지한다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 포함 70-operation OpenAPI 검증)

- What was done:
  - Cover Letter 17개 operationId·request·response·enum·security와 P8 경로 부재를 고정했다.
- Key decisions:
  - 실제 Spring mapping과 생성 문서 path/operation 수를 모두 직접 계산한다.
- Issues encountered:
  - 없음.
- Validation:
  - OpenAPI 70 operations/51 paths와 Swagger UI 회귀가 Backend check에서 통과했다.
- Next steps:
  - P8 공개 API가 구현되기 전 경로를 추가하지 않는다.

## [2026-07-27] Session Summary (P5 포함 50-operation OpenAPI 검증)

- What was done:
  - Job 7개 operationId·request·response·security와 P6 분석 path 부재를 고정했다.
- Key decisions:
  - 기존 43 operations/30 paths를 보존하며 Job 7개로 50/34만 확장한다.
- Issues encountered:
  - 없음.
- Validation:
  - 실제 Spring mapping·생성 OpenAPI 계약 테스트가 통과했다.
- Next steps:
  - P6 전까지 analysis path assertion은 부재 상태를 유지한다.

## [2026-07-19] Session Summary (P4 포함 43-operation OpenAPI 검증)

- What was done:
  - 문서 8개를 포함한 실제 Spring mapping과 생성 OpenAPI의 전체 path·operationId·security·response·DTO 계약을 고정했다.
- Key decisions:
  - 기존 auth 5·profile 25·Agent Run 5 operation을 유지한다.
- Issues encountered:
  - MVC와 actuator mapping bean이 함께 있어 기본 `requestMappingHandlerMapping`을 qualifier로 명시했다.
- Validation:
  - 실제 Spring mapping과 생성 OpenAPI가 각각 정확히 43 operations/30 paths로 통과했다.
- Next steps:
  - P5 전에는 operation 수를 늘리지 않는다.

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
