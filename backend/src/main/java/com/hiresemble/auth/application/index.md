# 인증 애플리케이션 안내

## 디렉터리 목적

가입·로그인·로그아웃 transaction과 SecurityContext·CSRF Session 전이를 조정한다.

## 주요 파일 및 하위 디렉터리

- [`AuthService.java`](AuthService.java): 사용자 생성·검증, transaction 참여 Session 인증과 실패 정리
- [`CsrfTokenService.java`](CsrfTokenService.java): 현재 token projection과 성공 후 token 교체
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- JPA 저장, password 검증, Session ID rotation과 SecurityContext 저장의 순서를 명시적으로 관리한다.
- Session 저장 실패 시 in-memory 인증 상태를 폐기해 request-end 재저장을 차단한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`auth/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 이메일 존재 여부를 노출하지 않고 Session·비밀번호·개인정보를 로그에 남기지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
