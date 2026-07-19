# 공통 예외 처리 안내

## 디렉터리 목적

P1 실제 오류 code, 비즈니스 예외와 MVC 전역 오류 변환을 관리한다.

## 주요 파일 및 하위 디렉터리

- [`ErrorCode.java`](ErrorCode.java): P1에서 사용하는 안정적 오류 code와 HTTP status
- [`BusinessException.java`](BusinessException.java): 안전한 code·context 운반
- [`GlobalExceptionHandler.java`](GlobalExceptionHandler.java): validation·JSON·type·예상 밖 오류 변환
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Service 예외와 Spring MVC 예외를 공통 오류 DTO로 변환하고 안전한 상관 로그를 남긴다.

## 다른 디렉터리와의 의존 관계

- 상위 [`common/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 클라이언트에 exception message·stack trace를 노출하거나 모든 오류를 HTTP 200으로 감싸지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
