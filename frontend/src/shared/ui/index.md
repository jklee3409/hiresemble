# 공용 UI 영역 안내

## 디렉터리 목적

현재 구현된 여러 화면이 공유하는 비도메인 UI primitive를 관리한다. 제품 token은 [`../../styles/main.css`](../../styles/main.css)에 두고 이 디렉터리는 의미와 접근성 계약이 반복되는 Vue component만 소유한다.

## 주요 파일 및 하위 디렉터리

- [`BrandMark.vue`](BrandMark.vue), [`hiresemble-logo.png`](hiresemble-logo.png): 승인된 두 번째 로고 자산과 full·compact·inverse lockup
- [`AppIcon.vue`](AppIcon.vue): Dashboard calendar·guide·sparkle·career person과 rocket·flag·trend-up·bolt·pen·bookmark·trophy·compass를 포함한 currentColor 기반 자체 제작 SVG icon
- [`PageHeader.vue`](PageHeader.vue): list·detail·editor·compact variant, 선택적 heading level과 부분 강조용 title slot을 지원하는 route page 제목·설명·action 영역
- [`AppSelect.vue`](AppSelect.vue): 서비스 전체가 쓰는 단일 선택 control. OS가 그리는 native `<select>` option 목록 대신 제품 token을 적용한 listbox를 직접 그린다
- [`appSelectTesting.ts`](appSelectTesting.ts): `AppSelect`를 사용자와 같은 순서(열기 → 고르기)로 조작하는 test helper
- [`StatusBadge.vue`](StatusBadge.vue): text label과 선택적 prefix를 포함하는 semantic status. tone은 neutral·brand·info·success·notice·warning·danger이며 notice는 오류가 아닌 "확인 권장" 상태에 쓴다
- [`productJourney.ts`](productJourney.ts): Landing과 보호 `/guide`가 공유하는 5단계 번호·아이콘·canonical 제목·핵심 설명
- [`BackLink.vue`](BackLink.vue): 상세 화면에서 목록·이전 화면으로 돌아가는 유일한 표현. 전역 `.back-link` 알약과 왼쪽 화살표를 고정하고 위치·간격만 쓰는 쪽에서 정한다
- [`InlineNotice.vue`](InlineNotice.vue): 본문 흐름 안에서 한 가지 상황을 알리는 줄. 표면은 흰 카드로 두고 심각도는 왼쪽 아이콘에만 남긴다
- [`StatePanel.vue`](StatePanel.vue): loading·empty·error section과 action slot
- [`PaginationNav.vue`](PaginationNav.vue): 이전·현재·다음 공용 pagination
- [`formFocus.ts`](formFocus.ts): 검증 실패 뒤 첫 invalid control로 focus를 옮기는 공용 helper
- [`AppNotifications.vue`](AppNotifications.vue), [`notifications.ts`](notifications.ts): 전역 Toast와 focus trap·ESC·focus return을 지원하는 확인 Dialog
- [`uiComponents.test.ts`](uiComponents.test.ts): text status와 loading·empty·error 접근성 검증
- [`progress.md`](progress.md): 공용 UI 구현·검증 이력

## 구성 요소 역할

- 도메인 상태 판단은 page·feature가 수행하고 공용 component에는 이미 결정된 label, tone, 설명과 action만 전달한다.
- 공통 journey에는 보호 route와 page별 CTA를 넣지 않고 Landing과 Guide가 각 사용자 맥락에서 action과 preview를 소유한다.
- 상태는 색상에만 의존하지 않고 visible text와 적절한 `role`, `aria-live`, navigation label을 제공한다.
- `AppIcon`은 emoji나 외부 icon dependency 없이 현재 제품에서 실제 사용하는 최소 SVG path만 제공한다.
- `BrandMark`는 full·compact·inverse variant를 제공하고 실제 link의 accessible name은 사용하는 layout이 소유한다.
- button·input·checkbox·radio·switch·date·file의 시각 상태는 공용 style token을 사용하고 native semantics와 label 연결은 각 component·page가 유지한다.
- 단일 선택은 `AppSelect`만 사용한다. trigger가 `<button role="combobox">`라 `<label for>`로 이름을 붙일 수 없으므로 호출부는 `aria-label` 또는 `aria-labelledby`로 접근 가능한 이름을 반드시 준다.
- 성공은 Toast, 중요 mutation은 Confirm Dialog, 입력 오류는 Inline Validation으로 역할을 분리하고 브라우저 기본 alert·confirm·prompt를 사용하지 않는다.
- 문장 하나로 상황을 알릴 때는 면 전체를 상태색으로 채우는 `.alert` 대신 `InlineNotice`를 쓴다. `.alert`는 화면 전체가 그 상태일 때만 남긴다.
- 목록·이전 화면으로 돌아가는 링크는 화면마다 새로 만들지 않고 `BackLink`를 쓴다. 앞뒤 항목을 오가는 `ProfileSectionActions`의 이전·다음 button은 다른 패턴이므로 여기에 포함하지 않는다.

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
