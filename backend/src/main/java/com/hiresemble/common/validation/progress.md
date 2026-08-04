# Progress

## Overview

회원가입 비밀번호 조합과 Java 문자열 길이와 다른 UTF-8 byte 기반 credential 상한을 Bean Validation으로 제공한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-08-04] Session Summary (회원가입 PasswordPolicy validation)

- What was done:
  - Unicode code point 10자 이상과 문자·숫자·특수문자 각 1개 이상을 검사하는 `PasswordPolicy` annotation과 validator를 추가했다.
- Key decisions:
  - BCrypt 72-byte 상한은 기존 `Utf8ByteLength`와 조합하고 null 검증은 `@NotNull`에 맡긴다.
- Issues encountered:
  - None.
- Validation:
  - Validation 단위 테스트, Auth 통합 테스트와 Backend 전체 `check`가 통과했다.
- Next steps:
  - None.

## [2026-07-19] Session Summary (UTF-8 비밀번호 byte validation 구현)

- What was done:
  - null 처리를 다른 constraint에 위임하는 1..72 및 10..72 byte validator를 추가했다.

- Key decisions:
  - 문자 수가 아니라 StandardCharsets.UTF_8 byte 길이를 계산한다.

- Issues encountered:
  - None

- Validation:
  - 한글을 포함한 9·10·72·73 byte 경계 test가 통과했다.

- Next steps:
  - 다른 byte 제한 입력이 실제 도입될 때만 annotation을 재사용한다.
