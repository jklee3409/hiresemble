# Progress

## Overview

P2 공개 프로필·direct evidence 25개 operation과 DTO·validation 경계가 구현되어 있다.

## [2026-07-19] Session Summary (P2 프로필 HTTP·OpenAPI 계약 구현)

- What was done:
  - 기본 프로필, 다섯 구조화 resource CRUD, evidence 조회·편집·검토 API를 구현했다.
  - pagination 기본·최대, sort allowlist, nullable field와 enum을 공개 DTO에 고정했다.

- Key decisions:
  - Controller에는 도메인 규칙과 owner 판정을 두지 않고 application 결과만 직접 DTO로 반환한다.

- Issues encountered:
  - None

- Validation:
  - MockMvc·OpenAPI 테스트에서 status, CSRF·401, validation, owner 404, version 409와 정확히 30개 전체 operation을 검증했다.

- Next steps:
  - P4 document 구현 전까지 document 선택·filter를 공개 사용 흐름으로 활성화하지 않는다.
