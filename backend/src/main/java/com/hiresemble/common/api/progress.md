# Progress

## Overview

ControllerAdvice와 Security가 함께 사용하는 공개 오류 DTO와 생성 규칙을 소유한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (공통 오류 DTO와 factory 구현)

- What was done:
  - timestamp, status, code, message, fieldErrors, requestId의 정확한 field set을 구현했다.

- Key decisions:
  - requestId는 서버 filter가 생성한 UUID만 사용하며 fieldErrors가 없으면 빈 배열을 반환한다.

- Issues encountered:
  - None

- Validation:
  - 정확한 JSON field set과 Security/MVC parity test가 통과했다.

- Next steps:
  - 후속 오류 code도 같은 factory를 사용하고 공개 field set은 호환성을 유지한다.
