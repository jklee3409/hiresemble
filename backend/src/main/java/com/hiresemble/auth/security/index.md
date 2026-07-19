# 인증 Principal 안내

## 디렉터리 목적

Spring Session SecurityContext에 저장할 최소 현재 사용자 principal을 정의한다.

## 주요 파일 및 하위 디렉터리

- [`AuthenticatedUser.java`](AuthenticatedUser.java): UUID name과 공개 사용자 projection을 제공하는 직렬화 principal
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Session에서 인증 사용자 ID를 결정하고 Controller의 현재 사용자 응답으로 안전하게 투영한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`auth/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 비밀번호 hash나 Session ID를 principal에 넣지 않고 외부 입력으로 principal을 교체하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
