# Frontend Session Cleanup 안내

## 디렉터리 목적

logout·401·사용자 ID 변경 시 사용자 경계를 폐기하는 순서와 최소 확장 port를 관리한다.

## 주요 파일 및 하위 디렉터리

- [`sessionCleanup.ts`](sessionCleanup.ts): EventSource·query·store·draft cleanup coordinator
- [`sessionCleanup.test.ts`](sessionCleanup.test.ts): 정확한 순서와 사용자별 draft purge test
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- P3 Agent Run SSE controller와 향후 resource가 등록할 수 있는 close/reset/purge port를 제공한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`shared/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- cleanup 순서와 QueryClient clear를 유지하고 user boundary를 넘는 EventSource·cache를 남기지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../AGENTS.md)
- [공통 작업 절차](../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
