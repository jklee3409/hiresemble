# Progress

## Overview

P1 인증 Controller와 request·response DTO의 공개 HTTP 계약을 소유한다. 현재 P1 구현과 검증 상태만 기록한다.

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
