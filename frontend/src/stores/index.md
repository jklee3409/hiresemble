# Frontend Store 안내

## 디렉터리 목적

P1 현재 인증 사용자와 unknown·authenticated·anonymous 상태를 Pinia로 관리한다.

## 주요 파일 및 하위 디렉터리

- [`auth.ts`](auth.ts): bootstrap·signup·login·logout·401 reset store
- [`auth.test.ts`](auth.test.ts): 상태 전이·cleanup·두 사용자 격리 test
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 현재 사용자와 Session lifecycle만 소유하고 server resource cache는 QueryClient에 맡긴다.

## 다른 디렉터리와의 의존 관계

- 상위 [`src/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 사용자별 resource 데이터나 draft 본문을 auth store에 저장하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
