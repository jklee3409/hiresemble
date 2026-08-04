# Progress

## Overview

Signup password의 조합·문자 수와 signup·login UTF-8 byte 길이 Bean Validation 경계를 검증한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-08-04] Session Summary (Signup 조합·문자 수 경계 회귀)

- What was done:
  - 9/10자, 조합 누락, Unicode 72/73바이트와 login 기존 경계를 검증하도록 회귀를 확장했다.
- Key decisions:
  - 안전한 합성 fixture만 사용하고 실패 결과에 평문 password가 포함되지 않는 통합 검증을 병행한다.
- Issues encountered:
  - None.
- Validation:
  - `Utf8ByteLengthValidatorTest`와 Backend 전체 `check`가 통과했다.
- Next steps:
  - None.

## [2026-07-19] Session Summary (UTF-8 byte 경계 테스트 구현)

- What was done:
  - signup 9·10·72·73 byte와 login 1·72·73 byte를 검증했다.

- Key decisions:
  - 한글 3-byte 문자를 사용해 byte 기준임을 명시적으로 고정한다.

- Issues encountered:
  - None

- Validation:
  - Utf8ByteLengthValidatorTest 2개가 통과했다.

- Next steps:
  - password 계약 변경 시 Backend와 Frontend 경계를 함께 갱신한다.
