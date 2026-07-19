# Progress

## Overview

Backend P1 OpenAPI와 일치하는 TypeScript DTO, Axios·CSRF와 typed 오류 처리를 소유한다. 현재 P1 구현과 검증 상태만 기록한다.

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
