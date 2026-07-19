# Progress

## Overview

ControllerAdvice와 Security가 함께 사용하는 공개 오류 DTO·생성 규칙 및 P1·P2 공통 OpenAPI metadata를 소유한다.

## [2026-07-19] Session Summary (P2 프로필 OpenAPI schema 보강)

- What was done:
  - 프로필 enum·schema와 Session·CSRF 요구를 생성 OpenAPI에 반영했다.

- Key decisions:
  - P1 성공 DTO 직접 반환과 여섯 field 오류 envelope 계약을 그대로 유지한다.

- Issues encountered:
  - None

- Validation:
  - 생성 OpenAPI에서 인증 5개와 프로필 25개, 총 30개 operation 및 금지 경로 부재를 검증했다.

- Next steps:
  - P3 이후 operation은 실제 구현 phase에서만 추가한다.

## [2026-07-19] Session Summary (공통 OpenAPI·Swagger security 설정 추가)

- What was done:
  - `OpenApiConfiguration`에 API info, Authentication tag와 `sessionCookie`·`csrfToken` scheme를 추가했다.
  - 같은-origin Swagger UI의 CSRF bootstrap, Authorize 입력, Session rotation 뒤 token 교체 절차를 tag 설명에 기록했다.

- Key decisions:
  - logout의 Session Cookie와 CSRF header는 같은 security requirement 객체의 AND로 생성한다.
  - JSON CSRF token 계약과 맞지 않는 Springdoc 내장 Cookie·storage 자동화는 활성화하지 않는다.

- Issues encountered:
  - 두 annotation이 별도 security 배열 항목으로 생성되면 OR 의미가 되므로 최소 customizer로 AND를 명시했다.

- Validation:
  - 생성 JSON에서 두 scheme와 logout 단일 AND requirement를 확인하는 통합 테스트가 통과했다.
  - 전체 Backend check 33개 테스트가 통과했다.

- Next steps:
  - 새 인증 방식이나 Cookie/header 이름 변경 시 configuration·Controller requirement·contract test를 함께 갱신한다.

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
