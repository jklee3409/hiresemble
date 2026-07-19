# 프론트엔드 App 조립 안내

## 디렉터리 목적

Vue 애플리케이션이 공유하는 Pinia와 TanStack Query 인스턴스 구성을 관리한다.

## 주요 파일 및 하위 디렉터리

- [`pinia.ts`](pinia.ts): 애플리케이션 Pinia instance
- [`queryClient.ts`](queryClient.ts): 4xx·mutation 재시도 정책을 가진 QueryClient
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- main.ts가 전역 plugin을 조립할 수 있도록 상태 container와 server cache 정책을 제공한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`src/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 도메인 상태나 page 규칙을 app bootstrap에 넣지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
