# Spring 백엔드 개발 규칙

## 적용 범위

`backend/`의 Java, Gradle, Spring 설정, Flyway와 백엔드 테스트 변경에 적용한다. 모든 백엔드 작업 전에 [`workflow.md`](workflow.md)와 이 문서를 읽고, 응답이나 예외 경로가 관련되면 [`backend-response-exception.md`](backend-response-exception.md)도 읽는다.

## 아키텍처와 패키지 경계

- 기본 package는 `com.hiresemble`이다.
- Spring Boot 단일 배포 단위 안에서 도메인별 경계를 가진 모듈러 모놀리스를 유지한다.
- 기능 package는 필요해질 때 `auth`, `profile`, `document`, `job`, `coverletter`, `interview`, `agentrun`처럼 업무 책임을 기준으로 추가한다.
- 여러 도메인이 실제로 공유하는 API 응답, 예외, 보안, 시간·ID 보조 기능만 `common`에 둔다.
- Controller는 HTTP 입력·출력과 인증 context를 변환하고, Service/Application 계층은 use case와 transaction을 조정하며, Domain은 상태 전이와 불변식을 소유한다.
- Repository 구현 세부사항이나 Spring HTTP 타입을 도메인 규칙으로 누출하지 않는다.

## 코드 작성

- Java 21 기능을 사용하되 현재 Spring/Jackson/JPA 호환성을 확인한다.
- 외부 경계 DTO는 명확한 불변 record를 우선하고 Entity를 API로 직접 노출하지 않는다.
- Bean Validation은 입력 형식 검증에 사용하고, 소유권·상태 전이 같은 업무 검증은 Service/Domain에서 수행한다.
- `@Transactional` 경계를 use case 단위로 명시하고, 장시간 외부 호출을 DB transaction 안에 두지 않는다.
- 모든 사용자 소유 데이터 조회에는 인증 사용자 조건을 포함한다. 타 사용자 리소스는 명세에 따라 404로 숨긴다.
- 시간은 UTC/`Instant`, 식별자는 UUID 계약을 따른다.
- 모델명, provider, 비용 한도와 비밀값을 코드에 하드코딩하지 않는다.
- LLM 입력·출력은 구조화 DTO로 제한하고 자유 실행 loop 대신 명시적 단계와 상태를 사용한다.

## 데이터와 migration

- JPA `ddl-auto`는 `validate`를 유지하고 스키마 변경은 Flyway로 관리한다.
- 이미 적용된 migration을 수정하지 않고 순차 버전의 새 migration을 추가한다.
- DB 제약(FK, unique, check)과 애플리케이션 검증을 함께 설계한다.
- pgvector 조회는 항상 `user_id` 격리를 포함하고 embedding model/dimension을 함께 추적한다.
- 원본 파일은 DB BLOB가 아니라 Object Storage에 두며 저장 key에 사용자 입력 파일명을 직접 사용하지 않는다.

## 보안과 로그

- Spring Session Cookie와 CSRF 계약을 유지한다.
- 인증 실패 401, 인가/CSRF 실패 403, 타 사용자 리소스 404를 일관된 공통 오류 형식으로 반환한다.
- 비밀번호, session/token, API key, 이메일, 문서·자기소개서·면접 원문, 전체 prompt/response를 로그에 남기지 않는다.
- 클라이언트 메시지와 운영 로그를 분리하고 stack trace는 서버 로그에만 둔다.
- Request ID와 안전한 error code를 상관관계에 사용한다.

## Controller OpenAPI·Swagger 규칙

- 모든 production Controller는 업무 책임 단위의 `@Tag`를 갖고, 공개 endpoint마다 안정적이고 유일한 `operationId`, `summary`, 필요한 `description`을 `@Operation`으로 선언한다.
- `@ApiResponses`에는 명세에 있는 실제 HTTP status와 직접 성공 DTO·공통 `ErrorResponseDto` schema를 기록한다. 성공 envelope를 가정하거나 아직 구현하지 않은 미래 응답을 Swagger에 선행 노출하지 않는다.
- Session 인증 endpoint는 `sessionCookie`, CSRF가 필요한 mutation은 `csrfToken` security requirement를 선언한다. 두 인증 요소가 모두 필요한 endpoint는 OpenAPI의 같은 security requirement 객체에 넣어 AND로 표현하고, 별도 배열 항목의 OR로 잘못 생성하지 않는다. `csrfToken`은 `X-CSRF-TOKEN` header API key이고, 브라우저가 관리하는 `SESSION` Cookie를 request body·query parameter로 문서화하지 않는다.
- `HttpServletRequest`, `HttpServletResponse`, `CsrfToken`, 인증 principal처럼 framework가 주입하는 인자는 Swagger 사용자 입력에서 숨긴다.
- request example은 Bean Validation과 API 계약을 실제로 통과하는 가짜 값만 사용한다. 실제 이메일·비밀번호·Session·CSRF token·운영 secret이나 개인정보를 annotation과 문서에 넣지 않는다.
- 로컬 Swagger UI는 `/swagger-ui.html`, OpenAPI JSON은 `/v3/api-docs`를 사용한다. Swagger UI의 API 시험은 다음 순서를 따른다.
  1. `GET /api/v1/auth/csrf`를 실행해 같은 origin 브라우저에 anonymous `SESSION` Cookie를 만들고 응답의 `token`을 확인한다.
  2. Swagger UI `Authorize`에서 `csrfToken` 값으로 해당 token을 입력한다. `SESSION` Cookie는 브라우저가 자동 전송하므로 임의 Cookie 값을 입력하거나 복사하지 않는다.
  3. signup/login/logout 같은 mutation을 실행한다. signup/login 성공으로 Session이 rotate되면 성공 응답의 새 `csrf.token`으로 `csrfToken` 값을 즉시 교체한다.
  4. 실제 운영 계정·데이터로 Swagger UI를 시험하지 않으며, 운영 환경의 문서 노출 여부는 별도 배포 보안 결정 없이 확대하지 않는다.
- Controller나 DTO 계약을 변경하면 생성 OpenAPI의 path·operationId·status·schema·security requirement와 Swagger UI 접근을 통합 테스트로 검증하고, frontend 소비 타입과 관련 명세를 함께 대조한다.
- test fixture Controller는 production OpenAPI와 Swagger UI에 노출하지 않는다.

## 테스트

- Domain: JUnit 5로 불변식과 상태 전이
- Repository: Testcontainers PostgreSQL로 실제 제약과 pgvector 쿼리
- API: MockMvc로 인증, 검증, 상태 코드와 JSON 계약
- 외부 서비스: WireMock 또는 Fake gateway
- AI workflow: Fake model로 단계 순서, 재시도, 비용 차단, 복구
- 공통 오류 변경 시 ControllerAdvice와 Spring Security 진입점 양쪽의 계약 테스트

기본 검증은 Windows에서 `backend\.\gradlew.bat check`에 해당하는 `Set-Location backend; .\gradlew.bat check`다. CI에서 실제 유료 AI/Search API를 호출하지 않는다.

## 변경 시 주의사항

- 현재는 초기 application class와 설정만 있으며 도메인 패키지는 아직 없다. 문서의 예상 구조를 구현 완료로 간주하지 않는다.
- 새 공통 추상화를 만들기 전에 실제 두 개 이상의 도메인 사용처를 확인한다.
- Spring Boot 또는 Spring AI 버전 변경은 공식 호환성과 전체 test를 확인한다.
- API, DB, Security 변경은 관련 `docs/spec/`과 모듈 `progress.md`를 함께 갱신한다.

## 관련 문서

- [응답·예외 처리 규칙](backend-response-exception.md)
- [기술 스택 명세](../spec/tech_stack.md)
- [API 명세](../spec/api.md)
- [DB 명세](../spec/db.md)
- [백엔드 구조 안내](../../backend/index.md)
