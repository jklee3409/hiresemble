# 공통 HTTP·OpenAPI 안내

## 디렉터리 목적

ControllerAdvice와 Security가 함께 사용하는 공개 오류 DTO·생성 규칙 및 P1·P2 애플리케이션 공통 OpenAPI metadata를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`ErrorResponseDto.java`](ErrorResponseDto.java): 여섯 필드 공통 오류 응답
- [`FieldErrorDto.java`](FieldErrorDto.java): rejected value 없는 field 오류
- [`ErrorResponseFactory.java`](ErrorResponseFactory.java): ErrorCode와 서버 Request ID 기반 응답 생성
- [`OpenApiConfiguration.java`](OpenApiConfiguration.java): API info, Session·CSRF security scheme, 프로필 enum·schema와 복합 requirement
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 오류 field set과 request correlation을 한 곳에서 생성해 MVC와 Security 응답을 일치시킨다.
- OpenAPI 공통 설정은 같은-origin Swagger UI에서 브라우저 Session Cookie와 `X-CSRF-TOKEN`을 사용해 실제 API를 시험하는 방법을 정의한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`common/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 내부 예외 message, SQL, stack trace, rejected value를 DTO에 추가하지 않는다.
- Session과 CSRF가 함께 필요한 operation은 OpenAPI security 배열의 OR가 아니라 같은 requirement 객체의 AND로 표현한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
