# P1 Layout 안내

## 디렉터리 목적

익명 인증 화면과 보호 화면의 공통 shell을 PublicLayout·AppLayout으로 분리한다.

## 주요 파일 및 하위 디렉터리

- [`PublicLayout.vue`](PublicLayout.vue): signup/login 공통 인증 shell
- [`AppLayout.vue`](AppLayout.vue): 보호 route header·navigation·logout action
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Layout은 공통 navigation과 logout만 제공하고 page별 데이터나 P2 기능을 소유하지 않는다.

## 다른 디렉터리와의 의존 관계

- 상위 [`src/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 미구현 메뉴·Dashboard 카드·Agent 상태 UI를 P1 shell에 선행 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
