# Progress

## Overview

P1 실제 오류 code, 비즈니스 예외와 MVC 전역 오류 변환을 관리한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (P1 ErrorCode와 전역 예외 변환 구현)

- What was done:
  - validation, malformed JSON, media type, 인증 관련 비즈니스 오류와 500 변환을 구현했다.

- Key decisions:
  - 로그에는 오류 code 또는 예외 type과 MDC requestId만 남기고 민감 message와 throwable 원문은 기록하지 않는다.

- Issues encountered:
  - None

- Validation:
  - ErrorCode 고유성, malformed·validation·parameter·500 안전 응답 test가 통과했다.

- Next steps:
  - 새 도메인 code는 실제 endpoint 구현과 함께 최소 범위로 추가한다.
