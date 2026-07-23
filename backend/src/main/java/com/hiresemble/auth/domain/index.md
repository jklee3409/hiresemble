# 인증 도메인 값 안내

## 디렉터리 목적

P1 사용자 identity에 필요한 최소 역할과 lifecycle 상태 값을 정의한다.

## 주요 파일 및 하위 디렉터리

- [model/](model/index.md): 해당 계층의 값·결과 모델
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- JPA와 principal이 같은 canonical enum을 사용하도록 안정적인 타입 경계를 제공한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`auth/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 미래 역할이나 상태를 제품 계약 없이 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
