# Hiresemble 애플리케이션 package 진행 상황

## 현재 구현 상태

- [`HiresembleApplication.java`](HiresembleApplication.java)만 구현되어 Spring Boot를 시작한다.
- Controller, Service, Domain, Repository, Security 설정과 업무 도메인 package는 아직 없다.
- `BaseResponseDto`, `ErrorResponseDto`, `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`도 현재 존재하지 않는다.

## 완료된 작업

- `com.hiresemble` 기본 package에 애플리케이션 실행 진입점을 생성했다.
- 레퍼런스 분석 결과와 현재 API 명세를 조정한 공통 응답·예외 적용 지침을 문서 링크로 연결했다.
- 현재 파일과 예상 package를 구분하는 `index.md`와 이 진행 문서를 생성했다.

## 진행 중인 작업

- 현재 진행 중인 Java 비즈니스 코드 작업은 없다. package 문서 체계 초기화는 완료됐다.

## 남은 작업

- 첫 API use case를 구현할 때 명세에 맞는 도메인 package와 테스트를 추가한다.
- 오류 기능이 실제로 필요할 때 `ErrorResponseDto`, `FieldErrorDto`, `ErrorResponseFactory`, `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`, `SecurityErrorResponseWriter`를 구현한다.
- 오류 code uniqueness, HTTP status, validation, Security 401/403와 fallback 500 계약 테스트를 추가한다.

## 확인된 문제

- 현재 프로젝트 전용 `SecurityFilterChain`과 공통 오류 변환이 없어 향후 API를 공개하기 전에 인증·인가 계약을 구현해야 한다.
- 실제 API가 없어 문서화된 응답·예외 구조의 사용처와 호환성을 아직 런타임으로 검증할 수 없다.

## 기술적 결정 사항

- [`../../../../../../docs/agent-rules/backend-response-exception.md`](../../../../../../docs/agent-rules/backend-response-exception.md)에 따라 성공 응답용 `BaseResponseDto`는 만들지 않는다.
- 오류는 실제 HTTP 상태와 안정적인 application `code`를 분리하고 ControllerAdvice와 Security가 동일 factory를 사용하게 한다.
- 공통 package와 도메인 package는 사용처 없이 선행 생성하지 않는다.

## 테스트 및 검증 결과

- `Set-Location backend; .\gradlew.bat check`가 성공해 현재 실행 진입점이 컴파일된다.
- `rg` 검색으로 공통 응답·예외 및 비즈니스 Java 클래스가 존재하지 않음을 확인했다.
- 응답·예외 MockMvc/Security 테스트는 구현 코드가 없어 미실행이다.

## 마지막 수정 일자

2026-07-17
