# 공개 인증·P1~P8 Layout 안내

## 디렉터리 목적

익명 인증 화면과 responsive 보호 화면의 공통 shell을 분리하고 desktop 상단 사용자 여정 navigation, mobile bottom navigation, account menu와 lazy Progress Drawer를 제공한다.

## 주요 파일 및 하위 디렉터리

- [`PublicLayout.vue`](PublicLayout.vue): `/` Landing과 분리된 비대칭 brand canvas, Landing 복귀 브랜드와 form 우선 mobile signup/login shell
- [`AppLayout.vue`](AppLayout.vue): desktop 상단 navigation, mobile bottom navigation·focus-trapped 더보기, 단일 account menu와 닉네임 Modal
- [`JobDetailLayout.vue`](JobDetailLayout.vue): 공통 공고 resource header, header offset sticky tab, 명확한 body gap과 Job detail outlet
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Layout은 현재 구현 route의 active navigation, account 닉네임 Modal, logout과 최근 active Run query 기반 진행 중인 분석 drawer를 제공하고 page별 form 상태를 소유하지 않는다.
- PublicLayout의 CSS node·orbit motion은 장식이며 `prefers-reduced-motion`에서 비활성화한다.
- route meta title은 layout이 아니라 Router 공통 hook이 관리해 PublicLayout·AppLayout·404에서 같은 규칙을 사용한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`src/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 미구현 P9 navigation과 Dashboard mock 집계를 선행 추가하지 않는다. Header count는 owner-scoped active 목록의 `totalElements`, Drawer 항목은 최근 5개로 표시한다.
- mobile drawer는 accessible name, Escape·Tab focus 처리와 trigger focus 복원을 유지한다.
- 닉네임 Modal은 header trigger focus 복원, Escape·Tab focus trap과 body scroll lock을 유지한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
