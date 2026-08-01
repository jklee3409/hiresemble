# 공용 UI 영역 안내

## 디렉터리 목적

현재 구현된 여러 화면이 공유하는 비도메인 UI primitive를 관리한다. 제품 token은 [`../../styles/main.css`](../../styles/main.css)에 두고 이 디렉터리는 의미와 접근성 계약이 반복되는 Vue component만 소유한다.

## 주요 파일 및 하위 디렉터리

- [`BrandMark.vue`](BrandMark.vue): H·node·orbit를 결합한 재사용 가능한 inline SVG 브랜드 mark
- [`AppIcon.vue`](AppIcon.vue): currentColor 기반 장식·기능 SVG icon
- [`PageHeader.vue`](PageHeader.vue): route page의 eyebrow·제목·설명·action 영역
- [`StatusBadge.vue`](StatusBadge.vue): text label과 선택적 prefix를 포함하는 semantic status
- [`StatePanel.vue`](StatePanel.vue): loading·empty·error section과 action slot
- [`PaginationNav.vue`](PaginationNav.vue): 이전·현재·다음 공용 pagination
- [`formFocus.ts`](formFocus.ts): 검증 실패 뒤 첫 invalid control로 focus를 옮기는 공용 helper
- [`AppNotifications.vue`](AppNotifications.vue), [`notifications.ts`](notifications.ts): 전역 Toast와 focus trap·ESC·focus return을 지원하는 확인 Dialog
- [`uiComponents.test.ts`](uiComponents.test.ts): text status와 loading·empty·error 접근성 검증
- [`progress.md`](progress.md): 공용 UI 구현·검증 이력

## 구성 요소 역할

- 도메인 상태 판단은 page·feature가 수행하고 공용 component에는 이미 결정된 label, tone, 설명과 action만 전달한다.
- 상태는 색상에만 의존하지 않고 visible text와 적절한 `role`, `aria-live`, navigation label을 제공한다.
- `AppIcon`은 emoji나 외부 icon dependency 없이 현재 제품에서 실제 사용하는 최소 SVG path만 제공한다.
- `BrandMark`는 full·compact·inverse variant를 제공하고 실제 link의 accessible name은 사용하는 layout이 소유한다.
- button·input·select·checkbox·radio·switch·date·file의 시각 상태는 공용 style token을 사용하고 native semantics와 label 연결은 각 component·page가 유지한다.
- 성공은 Toast, 중요 mutation은 Confirm Dialog, 입력 오류는 Inline Validation으로 역할을 분리하고 브라우저 기본 alert·confirm·prompt를 사용하지 않는다.

## 다른 디렉터리와의 의존 관계

- token과 공용 class는 [`../../styles/`](../../styles/index.md)에 의존한다.
- layout·page·feature는 필요할 때 이 primitive를 조합하되 transport나 business state를 이 디렉터리로 이동하지 않는다.

## 변경 시 주의사항

- 두 화면 이상에서 의미와 접근성 계약이 반복될 때만 component를 추가한다.
- status tone을 새 domain enum처럼 확장하지 않고 사용자에게 보이는 label을 항상 함께 제공한다.
- page 전용 배치나 mutation/query 동작을 공용 component에 넣지 않는다.

## 관련 규칙 및 문서

- [프론트엔드 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [페이지 구조 명세](../../../../docs/spec/page.md)
- [상위 shared 안내](../index.md)
- [영역 진행 상황](progress.md)
