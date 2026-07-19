# 공통 보안 안내

## 디렉터리 목적

Request ID, Spring Security Session·CSRF 정책과 Security 오류 JSON 작성을 관리한다.

## 주요 파일 및 하위 디렉터리

- [`RequestIdFilter.java`](RequestIdFilter.java): 서버 UUID header·MDC·안전 로그
- [`SecurityConfiguration.java`](SecurityConfiguration.java): Session 인증, CSRF, BCrypt, Cookie와 JDBC transaction 설정
- [`SecurityErrorResponseWriter.java`](SecurityErrorResponseWriter.java): 401·403 공통 오류 직렬화
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Controller 이전에 발생하는 인증·인가·CSRF 실패도 공통 오류 factory와 같은 field set으로 응답한다.
- Spring Session JDBC 저장은 `IMMEDIATE` flush와 named `REQUIRED` transaction operations로 application transaction에 참여한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`common/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 외부 X-Request-Id를 신뢰하지 않고 비밀번호·Session·개인정보를 로그에 남기지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
