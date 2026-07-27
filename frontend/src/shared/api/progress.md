# Progress

## Overview

Backend P1~P5 OpenAPI와 일치하는 TypeScript DTO, Axios·CSRF와 typed 오류 처리를 소유한다.

## [2026-07-27] Session Summary (P5 Job typed contract·client 구현)

- What was done:
  - Job enum·DTO strict Zod schema와 공개 API 7개 typed client·idempotency header를 추가했다.
- Key decisions:
  - P5 projection은 `null`, `false`, 빈 tuple과 `0`만 허용하고 P6 분석 DTO를 정의하지 않는다.
- Issues encountered:
  - 초기 P6 `Eligibility`·analysis schema를 validator 보정에서 제거했다.
- Validation:
  - 잘못된 server response 거부, API request/status와 전체 Frontend 122 tests가 통과했다.
- Next steps:
  - P6 OpenAPI가 구현된 뒤 분석 계약을 별도 추가한다.

## [2026-07-19] Session Summary (P4 Document typed contract·client 구현)

- What was done:
  - 공개 DTO·enum Zod parity와 multipart·idempotency·version·download client를 추가했다.
- Key decisions:
  - storage key·checksum·parser·embedding·provider 내부 field는 type에도 노출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - DTO parity·multipart header·오류 parsing 테스트와 Backend OpenAPI 43/30이 통과했다.
- Next steps:
  - P5 이후 client를 미리 추가하지 않는다.

## [2026-07-19] Session Summary (P3 Agent Run typed contract·client 구현)

- What was done:
  - Run·Step enum, nullable DTO와 6종 SSE payload를 Zod로 고정했다.
  - 목록·상세, retry Idempotency-Key와 cancel stateVersion API client를 추가했다.

- Key decisions:
  - unknown server field는 runtime parsing에서 제거해 내부 field가 UI로 전파되지 않는다.

- Issues encountered:
  - None.

- Validation:
  - enum parity·nullability·repeatable query·mutation header/body tests가 통과했다.

- Next steps:
  - 공개 Run 생성 API는 만들지 않고 domain workflow가 내부 launcher를 사용한다.

## [2026-07-19] Session Summary (P2 profile typed client 구현)

- What was done:
  - 프로필 enum·request·response·page type과 25개 profile/evidence API consumer를 추가했다.
  - HTTP client에 typed PUT·PATCH·DELETE를 추가하고 기존 Cookie·CSRF·401 흐름을 재사용했다.

- Key decisions:
  - Backend 직접 DTO와 enum·nullability를 그대로 반영하고 성공 envelope를 만들지 않는다.

- Issues encountered:
  - None

- Validation:
  - profile API path·method·payload 테스트와 TypeScript 전체 typecheck가 통과했다.

- Next steps:
  - document 연결 성공 type은 P4 Backend 계약 구현 뒤 활성화한다.

## [2026-07-19] Session Summary (P1 typed Axios·CSRF client 구현)

- What was done:
  - baseURL /api/v1, withCredentials, 동적 CSRF와 1회 복구, 공통 오류 normalization을 구현했다.

- Key decisions:
  - signup/login 성공 응답의 새 csrf를 즉시 교체하고 409 mutation은 자동 재시도하지 않는다.

- Issues encountered:
  - None

- Validation:
  - HTTP client unit test와 TypeScript·production build가 통과했다.

- Next steps:
  - Backend OpenAPI 변경 시 contracts와 runtime guard·test를 함께 갱신한다.
