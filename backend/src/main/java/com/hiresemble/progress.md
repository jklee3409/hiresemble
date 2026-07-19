# Progress

## Overview

- [`HiresembleApplication.java`](HiresembleApplication.java)와 P1 `common`·`auth`, P2 `profile`, P3 `agentrun`·`ai` package가 구현되어 있다.
- 공통 오류·인증·프로필과 durable Run·Step, fixed workflow·disabled gateway를 제공한다.
- 성공 응답용 `BaseResponseDto`와 P4 이후 domain package는 존재하지 않는다.

## [2026-07-19] Session Summary (P3 agentrun·ai Java 영역 추가)

- What was done:
  - `agentrun` api/application/domain/infrastructure와 `ai` runtime package를 실제 책임별로 추가했다.
  - auth signup과 공통 idempotency·OpenAPI를 P3 preference·retry metadata에 연결했다.

- Key decisions:
  - AI가 repository를 직접 참조하지 않고 application port만 소비한다.

- Issues encountered:
  - None.

- Validation:
  - package boundary 정적 검색과 Backend 243 tests가 통과했다.

- Next steps:
  - P4 이후 package는 실제 기능 계약이 준비될 때만 생성한다.

## [2026-07-19] Session Summary (P2 profile Java 영역 추가)

- What was done:
  - 기본·구조화 프로필과 direct evidence를 api/application/domain/infrastructure 경계로 구현했다.
  - 가입 기본 프로필 소유권을 auth JPA에서 profile 등록 service와 JDBC store로 이동했다.

- Key decisions:
  - 사용자 ID는 Session principal에서만 받고 모든 resource query에 owner를 포함한다.
  - P2 document 연결은 nullable field만 유지하고 table·FK·성공 경로를 선행하지 않는다.

- Issues encountered:
  - None

- Validation:
  - Backend 전체 check 54개 test와 실제 브라우저 두 사용자 owner 404 흐름이 통과했다.
  - 최종 read-only validator가 P2 package 경계를 `PASS`로 판정했다.

- Next steps:
  - P2는 완료 상태이며 P3 이전 package는 추가하지 않는다.

## [2026-07-19] Session Summary (P1 common·auth Java 구현)

- What was done:
  - `common`에 동일 오류 field set을 만드는 DTO·factory·handler·Security writer와 request ID, UTF-8 byte validator, idempotency 기반을 구현했다.
  - `auth`에 다섯 API, application service, JPA entity·repository, 인증 principal과 Security 구성을 구현했다.

- Key decisions:
  - Entity는 API로 반환하지 않고 `AuthSessionDto`, `CurrentUserDto`, `CsrfTokenDto` projection을 사용한다.
  - 외부 request ID는 신뢰하지 않고 서버 UUID를 응답 header·오류 body·MDC 상관키로 공유한다.
  - P1에서 실제 사용하는 오류 code만 정의하고 로그인 오류는 `INVALID_CREDENTIALS`로 통일한다.
  - 만료 idempotency row는 DB에서 원자 reclaim하고 signup Session SQL은 user/profile과 같은 transaction에 참여시킨다.

- Issues encountered:
  - signup의 사용자 저장 오류 변환이 프로필 실패까지 삼키지 않도록 예외 처리 범위를 좁혀 transaction rollback을 보장했다.
  - raw CSRF token을 응답·header에서 일관되게 쓰도록 non-XOR request handler를 명시했다.

- Validation:
  - Auth 15개, OpenAPI 2개, ErrorCode 1개, idempotency 8개, validation 2개, migration 3개 테스트가 모두 통과했다.
  - 오류 field set·Security parity·request ID·민감정보 비노출·두 사용자 Session 분리를 MockMvc와 단위 테스트로 검증했다.
  - Session·DB 양방향 실패 원자성과 만료 record 동시 reclaim을 PostgreSQL 통합 테스트로 검증했다.

- Next steps:
  - P2 resource owner 404와 프로필 use case는 인증 principal에서 현재 사용자 ID만 받아 구현한다.
  - 실제 idempotency 적용 시 현재 최소 service를 transaction 경계에 연결한다.

## [2026-07-17] Session Summary (Hiresemble 실행 패키지 및 응답 예외 기준 준비)

- What was done:
  - 당시 구현 상태:
    - [`HiresembleApplication.java`](HiresembleApplication.java)만 구현되어 Spring Boot를 시작한다.
    - Controller, Service, Domain, Repository, Security 설정과 업무 도메인 package는 아직 없다.
    - `BaseResponseDto`, `ErrorResponseDto`, `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`도 현재 존재하지 않는다.
  - 완료된 작업:
    - `com.hiresemble` 기본 package에 애플리케이션 실행 진입점을 생성했다.
    - 레퍼런스 분석 결과와 현재 API 명세를 조정한 공통 응답·예외 적용 지침을 문서 링크로 연결했다.
    - 현재 파일과 예상 package를 구분하는 `index.md`와 이 진행 문서를 생성했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 Java 비즈니스 코드 작업은 없다. package 문서 체계 초기화는 완료됐다.

- Key decisions:
  - [`../../../../../../docs/agent-rules/backend-response-exception.md`](../../../../../../docs/agent-rules/backend-response-exception.md)에 따라 성공 응답용 `BaseResponseDto`는 만들지 않는다.
  - 오류는 실제 HTTP 상태와 안정적인 application `code`를 분리하고 ControllerAdvice와 Security가 동일 factory를 사용하게 한다.
  - 공통 package와 도메인 package는 사용처 없이 선행 생성하지 않는다.

- Issues encountered:
  - 현재 프로젝트 전용 `SecurityFilterChain`과 공통 오류 변환이 없어 향후 API를 공개하기 전에 인증·인가 계약을 구현해야 한다.
  - 실제 API가 없어 문서화된 응답·예외 구조의 사용처와 호환성을 아직 런타임으로 검증할 수 없다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 성공해 현재 실행 진입점이 컴파일된다.
  - `rg` 검색으로 공통 응답·예외 및 비즈니스 Java 클래스가 존재하지 않음을 확인했다.
  - 응답·예외 MockMvc/Security 테스트는 구현 코드가 없어 미실행이다.

- Next steps:
  - 첫 API use case를 구현할 때 명세에 맞는 도메인 package와 테스트를 추가한다.
  - 오류 기능이 실제로 필요할 때 `ErrorResponseDto`, `FieldErrorDto`, `ErrorResponseFactory`, `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`, `SecurityErrorResponseWriter`를 구현한다.
  - 오류 code uniqueness, HTTP status, validation, Security 401/403와 fallback 500 계약 테스트를 추가한다.
