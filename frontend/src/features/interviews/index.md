# Interview Frontend feature 안내

## 디렉터리 목적

P8 예상 질문 set filter·query, source 표시, 답변 version·409 비교와 feedback Agent Run 상호작용을 소유한다.

## 주요 파일 및 하위 디렉터리

- `filters.ts`: `/interviews` `qs*` URL query parse·canonicalization
- `queries.ts`: user-scoped Vue Query key와 P8 mutation·invalidation
- `presentation.ts`: source·coverage·질문 B2C label
- `InterviewQuestionCard.vue`: 답변 version·409 비교·feedback 이력
- `InterviewRunMonitor.vue`: 준비·feedback Agent Run 진행
- `testFixtures.ts`: component/API test fixture
- [`progress.md`](progress.md): P8 feature 상태

## 구성 요소 역할

서버 상태는 Vue Query를 원천으로 사용하고 충돌 시 최초 제출 snapshot을 화면 상태에만 보존해 사용자의 명시적 재적용에서만 최신 parent를 결합한다.

## 다른 디렉터리와의 의존 관계

- API·Zod는 [`../../shared/api/`](../../shared/api/index.md)에 있다.
- 화면은 [`../../pages/`](../../pages/index.md), route는 [`../../router/`](../../router/index.md)에 있다.

## 변경 시 주의사항

답변 draft를 browser storage에 추가하지 않고 실패·취소 feedback을 성공 이력처럼 표시하지 않는다. P9 session UI를 이 feature에 선행 추가하지 않는다.

## 관련 규칙 및 문서

- [Frontend 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [페이지 명세](../../../../docs/spec/page.md)
- [상위 feature 안내](../index.md)
- [진행 상황](progress.md)
