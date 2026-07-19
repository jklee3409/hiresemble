# Progress

## Overview

P1·P2 실제 오류 code, 비즈니스 예외와 MVC·DB 불변식 오류 변환을 관리한다.

## [2026-07-19] Session Summary (P2 resource·version 오류 변환 추가)

- What was done:
  - `RESOURCE_NOT_FOUND`, `RESOURCE_VERSION_CONFLICT`와 profile DB 불변식 충돌의 안전한 변환을 추가했다.

- Key decisions:
  - constraint 이름·SQL message·rejected value는 공개 응답에 포함하지 않는다.

- Issues encountered:
  - None

- Validation:
  - API 테스트에서 owner 404, version·대표 학력 409와 내부 정보 비노출을 검증했다.

- Next steps:
  - 후속 domain code는 활성 API 명세에 실제 사용처가 생길 때만 추가한다.

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
