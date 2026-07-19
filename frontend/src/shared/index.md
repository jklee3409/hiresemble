# 프론트엔드 공용 기반 안내

## 디렉터리 목적

여러 화면이 공유하는 P1 인증·P2 프로필·P3 Agent Run HTTP 계약과 Session cleanup port를 관리한다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): typed Axios·CSRF·오류·인증·프로필·Agent Run API
- [`session/`](session/index.md): logout·401·사용자 전환 cleanup 순서
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 기능별 UI를 포함하지 않는 재사용 가능한 transport와 lifecycle 기반만 제공한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`src/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- P4 이후 resource API client·draft 구조를 선행 생성하지 않는다. EventSource는 Session cleanup에 반드시 등록한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
