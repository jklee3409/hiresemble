# Spring 공통 응답 및 예외 처리 규칙

## 적용 범위와 기준

Controller 반환, Service/Domain 예외, 입력 검증, 인증·인가 실패, 전역 예외 변환과 공통 에러 코드에 적용한다.

분석 기준 레퍼런스는 다음 로컬 프로젝트다.

```text
E:\dev_factory\side-project\orchestrator-module-hardening
```

2026-07-17에 해당 프로젝트의 Java 소스를 직접 읽어 확인했다. 아래의 “레퍼런스 확인 결과”는 관찰 사실이고, “Hiresemble 적용 규칙”은 현재 명세에 맞춘 결정이다.

## 레퍼런스 확인 결과

### `BaseResponseDto`

- 위치: `src/main/java/eureca/capstone/project/orchestrator/common/dto/base/BaseResponseDto.java`
- package: `eureca.capstone.project.orchestrator.common.dto.base`
- 구조: Lombok `@Data`, `@Builder`를 사용한 generic class
- 필드: `Integer statusCode`, `String message`, `T data`
- factory:
  - `success(T data)`: `statusCode=200`, `message="success"`, data 설정
  - `voidSuccess()`: `statusCode=200`, `message="success"`, `data=null`
  - `fail(ErrorCode)`: 외부 `statusCode`에 application error number를 넣고 `data=ErrorResponseDto.of(errorCode)`

성공 예시:

```json
{ "statusCode": 200, "message": "success", "data": {} }
```

실패 예시:

```json
{
  "statusCode": 20000,
  "message": "fail",
  "data": {
    "statusCode": 20000,
    "statusCodeName": "USER_NOT_FOUND",
    "detailMessage": "해당 사용자를 찾을 수 없습니다."
  }
}
```

### `ErrorResponseDto`

- 위치: `src/main/java/eureca/capstone/project/orchestrator/common/dto/base/ErrorResponseDto.java`
- package: `eureca.capstone.project.orchestrator.common.dto.base`
- 구조: Lombok `@Data`, `@Builder`
- final 필드: `int statusCode`, `String statusCodeName`, `String detailMessage`
- `of(ErrorCode)` factory가 enum의 code/name/message를 복사한다.

### 커스텀 예외와 `ErrorCode`

- 커스텀 예외 위치: `common/exception/custom/*.java`
- 확인한 30개 커스텀 예외는 공통 base 없이 모두 `RuntimeException`을 직접 상속한다.
- `InternalServerException`, `BidException`만 final `ErrorCode` 필드를 보관하고 Lombok `@Getter`를 사용한다.
- 나머지는 no-arg 생성자에서 고정 문자열을 `super(...)`에 전달하고 handler가 예외 타입별 고정 enum을 선택한다.
- `ErrorCode` 위치: `common/exception/code/ErrorCode.java`
- `@Getter`, `@AllArgsConstructor` enum이며 `int code`, `String name`, `String message`를 가진다.
- 81개 상수를 인증 10000대, 사용자 20000대, 거래 30000대, 결제 40000대, 알람 50000대, 공통 60000대로 구분한다.
- `HttpStatus`를 보유하지 않아 application code와 실제 HTTP 상태의 관계가 없다.

확인된 레퍼런스 데이터 결함에는 `EMAIL_TOKEN_MISMATCH`의 잘못된 name, `SELLABLE_DATA_ADD_FAIL`의 name 불일치, 20057/40056 중복 번호, coupon 관련 name 불일치, 의미가 겹치는 email code가 있다. 숫자와 이름을 복사하지 않는다.

### 전역 예외 처리와 HTTP 상태

- 위치: `common/exception/GlobalExceptionHandler.java`
- 구조: `@Slf4j`, `@Hidden`, `@RestControllerAdvice`
- 커스텀 예외 30개와 `MethodArgumentNotValidException`을 각각 한 method씩 처리한다.
- 각 handler는 stack trace를 error log에 남기고 `BaseResponseDto.fail(...)`을 직접 반환한다.
- `ResponseEntity`나 `@ResponseStatus`가 없어 대부분 실패의 실제 HTTP 상태도 `200 OK`다.
- catch-all `Exception`/`Throwable` handler는 없다.
- validation은 고정 `METHOD_ARGUMENT_NOT_VALID`만 반환하며 field error detail을 제공하지 않는다.
- 일부 Controller는 공통 구조를 우회해 `ResponseEntity` 또는 `ResponseStatusException`을 사용한다.

### 인증·인가 실패

- `auth/filter/JwtAuthenticationFilter.java`는 token 오류를 `BaseResponseDto.fail(ErrorCode)`로 직렬화하지만 실제 HTTP 상태를 200으로 설정한다.
- `auth/config/SecurityConfig.java`의 `AuthenticationEntryPoint`와 `AccessDeniedHandler`는 DTO를 사용하지 않고 JSON 문자열을 직접 작성한다.
- Security 오류 본문은 `statusCode` 401/403, `message="fail"`, `data="false"`이며 일반 오류의 `ErrorResponseDto` 형식과 다르다. 실제 HTTP 상태는 역시 200이다.

### 레퍼런스에서 채택할 것과 제외할 것

채택할 구조적 원칙:

- 응답 생성 경로와 오류 형식을 중앙화한다.
- 안정적인 `ErrorCode`를 단일 기준으로 관리한다.
- Service/Domain은 HTTP 본문을 만들지 않고 typed exception을 발생시킨다.
- `@RestControllerAdvice`가 Controller 예외를 공통 오류 DTO로 변환한다.
- Security filter/entry point도 동일한 오류 factory를 사용한다.

채택하지 않을 구현:

- 실패를 실제 HTTP 200으로 반환하는 방식
- HTTP status와 무관한 숫자 code를 `statusCode`라는 이름으로 중복 노출하는 형식
- 모든 성공 응답을 envelope로 강제하는 방식
- 커스텀 예외가 모두 `RuntimeException`을 직접 상속하는 구조
- 예외별로 거의 동일한 handler method를 반복하는 구조
- field detail이 없는 validation 오류와 Security 수기 JSON
- 검증하지 않은 enum 숫자·문자열과 Lombok 의존성

## 현재 Hiresemble 계약

[`../spec/api.md`](../spec/api.md)는 다음을 명시한다.

- 성공한 단일 resource는 공통 envelope 없이 DTO를 직접 반환한다.
- 목록은 `items`, `page`, `size`, `totalElements`, `totalPages`를 반환한다.
- 본문 없는 성공은 204, 생성은 201, 비동기 접수는 202 등 실제 HTTP 의미를 사용한다.
- 오류는 `timestamp`, `status`, `code`, `message`, `fieldErrors`, `requestId`를 가진다.
- 입력 400, 인증 401, 인가/CSRF 403, 소유권 은닉 404, 충돌 409, 크기 413, media type 415, 비용 429, 외부 장애 503을 실제 HTTP 상태로 반환한다.

따라서 레퍼런스의 `BaseResponseDto`를 현재 Controller 성공 응답에 도입하면 기존 명세와 호환되지 않는다. API 명세가 명시적으로 개정되기 전에는 `BaseResponseDto<T>`를 생성하거나 사용하지 않는다.

## Hiresemble 적용 규칙

### 예상 package와 파일 구조

실제 첫 API 기능을 구현할 때 사용처와 명세를 다시 확인한 뒤 다음 구조를 기준으로 한다.

```text
com/hiresemble/common/
├─ api/
│  ├─ ErrorResponseDto.java
│  ├─ FieldErrorDto.java
│  └─ ErrorResponseFactory.java
├─ exception/
│  ├─ ErrorCode.java
│  ├─ BusinessException.java
│  └─ GlobalExceptionHandler.java
└─ security/
   └─ SecurityErrorResponseWriter.java
```

`BaseResponseDto.java`는 현재 성공 계약에서는 만들지 않는다. 향후 envelope 채택이 필요하면 먼저 `docs/spec/api.md`, 모든 Controller/consumer, OpenAPI와 frontend client의 migration 계획을 승인받는다.

### Controller 성공 응답

- 단일 resource와 목록 DTO를 직접 반환한다.
- status/header 제어가 필요하면 `ResponseEntity<T>`를 사용한다.
- 생성 201, 비동기 202, 본문 없음 204 등 명세의 실제 HTTP status를 지킨다.
- Controller에서 `try/catch`로 business exception을 공통 오류 DTO로 바꾸지 않는다.
- Entity, 내부 workflow state, 예외 message를 직접 반환하지 않는다.

### 오류 DTO

`ErrorResponseDto`는 불변 record를 우선하며 계약 필드를 정확히 가진다.

```text
timestamp   ISO-8601 UTC
status      실제 HTTP status 정수
code        안정적인 대문자 snake-case application code
message     민감정보 없는 사용자용 메시지
fieldErrors 입력 검증 오류 목록, 해당 없으면 빈 목록
requestId   요청 상관관계 ID
```

`FieldErrorDto`는 최소 `field`, `reason`을 가진다. rejected value, 내부 exception message, SQL/경로/개인정보는 노출하지 않는다. `ErrorResponseFactory`가 timestamp, request ID, code/status를 한 곳에서 생성해 ControllerAdvice와 Security가 같은 직렬화 결과를 사용하게 한다.

### `ErrorCode`

- enum은 최소 `HttpStatus httpStatus`, `String code`, `String defaultMessage`를 가진다.
- `code`는 HTTP status와 분리된 안정적인 식별자이며 enum 이름과 의도적으로 일치시킨다.
- 같은 code를 중복 선언하지 않는다. test로 uniqueness를 검증한다.
- 사용자에게 안전한 기본 message만 저장한다. 운영 상세는 exception cause/log에 둔다.
- 도메인별 code를 추가하기 전에 공통 code와 의미 중복을 확인한다.
- 외부 API가 code를 사용한 뒤에는 rename/delete하지 않고 deprecation/migration을 먼저 설계한다.

최소 공통 범주는 `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, `RESOURCE_VERSION_CONFLICT`, `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `RATE_OR_BUDGET_LIMIT_EXCEEDED`, `EXTERNAL_SERVICE_UNAVAILABLE`, `INTERNAL_ERROR`를 검토한다. 실제 enum은 구현하는 endpoint와 명세에서 필요한 code만 추가한다.

### 커스텀 예외

- `BusinessException extends RuntimeException`이 final `ErrorCode`와 선택적인 안전한 context를 보유한다.
- 도메인별 예외는 `BusinessException`을 상속하고 code를 constructor에서 고정하거나 명시적으로 전달한다.
- Service/Domain은 `ResponseEntity`, HTTP JSON, `ErrorResponseDto`를 만들지 않는다.
- 예외 이름은 상황을 표현하고 `InternalServerException` 같은 포괄 이름으로 여러 비즈니스 오류를 전달하지 않는다.
- 원래 cause는 보존하되 클라이언트에 cause message를 노출하지 않는다.

### 전역 예외 처리

- `@RestControllerAdvice`는 `ResponseEntity<ErrorResponseDto>`를 반환하고 `ErrorCode.httpStatus`를 실제 status로 사용한다.
- `BusinessException`은 공통 handler 하나로 처리하고 code별 중복 method를 만들지 않는다.
- 예상 가능한 client/business 오류는 warn 또는 info, 예상하지 못한 오류는 request ID와 함께 error로 기록한다.
- fallback `Exception` handler를 둘 경우 안전한 `INTERNAL_ERROR`와 500만 반환하고 stack trace는 서버 로그에만 둔다.
- `ResponseStatusException`이나 Spring 기본 오류가 공통 계약을 우회하지 않는지 test한다.

### 입력 검증 실패

- `MethodArgumentNotValidException`의 field errors를 `FieldErrorDto` 목록으로 변환한다.
- `ConstraintViolationException`, `BindException`, type mismatch, 누락 parameter, 읽을 수 없는 JSON을 400 공통 형식으로 정규화한다.
- field 이름과 안정적인 reason code만 공개하고 원본 비밀번호/본문/rejected value는 반환하지 않는다.
- 같은 field의 중복 오류 순서는 test에서 안정적으로 정규화한다.

### 인증·인가 실패

- Spring Security의 `AuthenticationEntryPoint`는 실제 401, `AccessDeniedHandler`는 실제 403을 사용한다.
- 두 경로 모두 주입된 Jackson `ObjectMapper`와 `SecurityErrorResponseWriter`/`ErrorResponseFactory`를 사용한다.
- JSON 문자열을 직접 이어 붙이거나 filter 안에서 `new ObjectMapper()`를 생성하지 않는다.
- ControllerAdvice가 filter chain 앞의 Security 예외를 모두 처리한다고 가정하지 않는다.
- 다른 사용자의 resource 소유권 실패는 Service 조회 단계에서 404 code로 변환한다.
- CSRF 실패도 403과 동일한 공통 오류 필드를 유지한다.

### HTTP status 매핑

| 오류 범주                                | 실제 HTTP status |
| ---------------------------------------- | ---------------: |
| Bean Validation, malformed request       |              400 |
| 인증 필요/세션 만료                      |              401 |
| 권한 또는 CSRF 실패                      |              403 |
| resource 미존재/소유권 은닉              |              404 |
| 중복/낙관적 잠금/상태 충돌               |              409 |
| upload size 초과                         |              413 |
| 지원하지 않는 media type                 |              415 |
| rate 또는 AI 비용 한도 초과              |              429 |
| 외부 LLM/Search/Object Storage 일시 장애 |              503 |
| 예상하지 못한 서버 오류                  |              500 |

세부 endpoint status는 `docs/spec/api.md`가 우선한다.

## 필수 검증

실제 구조를 구현하거나 수정할 때 최소한 다음을 자동화한다.

- `ErrorCode.code` uniqueness와 status 존재 여부 unit test
- 성공 응답이 envelope 없이 명세 DTO와 status를 유지하는 MockMvc test
- BusinessException별 HTTP status/code/body contract test
- validation field error와 malformed JSON test
- 인증 401, 인가/CSRF 403, 타 사용자 404 test
- fallback 500이 내부 message/stack trace를 노출하지 않는 test
- ControllerAdvice와 Security writer가 같은 JSON field set을 생성하는 test
- OpenAPI example과 `docs/spec/api.md`의 error schema 검토

표준 검증은 `Set-Location backend; .\gradlew.bat check`다. 실제 구현이 아직 없으면 검증을 실행하지 않은 이유와 예정 test를 backend `progress.md`에 기록한다.

## 현재 적용 상태

2026-07-17 현재 Hiresemble에는 `HiresembleApplication`과 설정만 있고 Controller, `BaseResponseDto`, `ErrorResponseDto`, custom exception, `ErrorCode`, `GlobalExceptionHandler`가 없다. 이번 규칙 구성 단계에서는 비즈니스 코드나 미사용 추상화를 선행 생성하지 않았으며, 위 예상 구조와 계약을 구현 지침으로 확정했다.

## 관련 문서

- [백엔드 개발 규칙](backend-development.md)
- [API 명세](../spec/api.md)
- [기술 스택 명세](../spec/tech_stack.md)
- [백엔드 진행 상황](../../backend/progress.md)
