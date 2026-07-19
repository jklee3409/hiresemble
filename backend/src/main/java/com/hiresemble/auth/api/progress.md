# Progress

## Overview

P1 인증 Controller와 request·response DTO의 공개 HTTP·Swagger 계약을 소유한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (인증 Controller Swagger metadata 보강)

- What was done:
  - 다섯 endpoint에 안정적 operationId, 설명, 실제 응답 schema와 Session·CSRF requirement를 추가했다.
  - request DTO에 validation을 통과하는 가짜 email·password·동의 example과 UTF-8 byte 설명을 추가했다.
  - Servlet request/response, principal과 `CsrfToken` framework 인자를 Swagger 입력에서 숨겼다.

- Key decisions:
  - signup/login 성공 뒤 회전된 Session과 새 CSRF token으로 Swagger Authorize 값을 교체하는 흐름을 operation description에 기록했다.
  - API path·status·DTO field와 인증 동작은 변경하지 않았다.

- Issues encountered:
  - None

- Validation:
  - 생성 OpenAPI에서 operationId·response·example·hidden parameter와 security requirement가 통과했다.
  - Backend 전체 33개 테스트가 통과했다.

- Next steps:
  - 후속 인증·계정 Controller도 같은 metadata와 안전한 example 규칙을 적용한다.

## [2026-07-19] Session Summary (P1 인증 HTTP 계약 구현)

- What was done:
  - P1 다섯 경로와 OpenAPI 응답 schema, 입력 validation을 실제 Controller와 record DTO로 구현했다.

- Key decisions:
  - signup은 201, logout은 204이며 모든 성공 응답은 공통 envelope 없이 계약 DTO를 직접 반환한다.

- Issues encountered:
  - OpenAPI의 JSON media type을 명시해 AuthSessionDto schema reference가 안정적으로 생성되도록 했다.

- Validation:
  - OpenAPI path set·schema test와 MockMvc 인증 통합 테스트가 통과했다.

- Next steps:
  - P1 범위를 유지하고 계정 변경 endpoint는 후속 단계 승인 범위에서만 추가한다.
