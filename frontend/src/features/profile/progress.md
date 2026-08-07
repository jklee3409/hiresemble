# Progress

## Overview

P2 프로필 Zod·query key·version conflict와 공용 입력 component가 구현되어 있다.

## [2026-08-07] Session Summary (경험 보관함 navigation·query key)

- What was done:
  - `ProfileTabs`에 경험 보관함을 8번째 항목으로 추가하고 user-scoped 경험 목록·상세·root query key를 정의했다.
- Key decisions:
  - 직접 입력 대외활동과 AI 정규 경험을 별도 route로 유지하며 가상 완료 수치는 만들지 않는다.
- Issues encountered:
  - 기존 navigation test의 7개 항목 기대를 새 route와 함께 갱신했다.
- Validation:
  - Frontend 전체 `pnpm check` 70 files/317 tests 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (프로필 navigation 항목 icon)

- What was done:
  - `ProfileTabs.vue`의 desktop 항목에 `aria-hidden` icon tile을 추가하고 hover·현재 항목에서 tile 색을 바꾸도록 했다.
- Key decisions:
  - link의 접근 가능한 이름과 mobile selector option은 그대로 두어 기존 navigation 계약과 단언 의미를 바꾸지 않는다.
  - 가상의 section 완료 count는 여전히 만들지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Node 24에서 `corepack pnpm check`: lint·format·typecheck, Vitest 67 files/284 tests, production build 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (지원 자격 자기신고 form)

- What was done:
  - 프로필 기본 화면에 지원 자격 조회·수정 form, enum 선택지, 날짜 입력과 자기신고 안내를 추가했다.
- Key decisions:
  - 기존 기본 프로필 저장과 독립된 query/mutation/version conflict 경계를 사용한다.
- Issues encountered:
  - None.
- Validation:
  - profile page·API 단위 테스트와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-01] Session Summary (직접 대외활동 navigation·행동)

- What was done:
  - 프로필 탭과 이전/다음 행동을 직접 대외활동 route로 연결하고 문서 추출 근거 화면과 분리했다.
- Key decisions:
  - 빈 상태에서 AI 추출 경험을 대신 노출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Profile navigation/page tests와 실제 모바일 항목 선택 UI를 확인했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (학력 단계 validation)

- What was done:
  - 교육 Form schema에 필수 `EducationLevel`을 추가하고 수동 `isPrimary` 입력을 제거했다.
- Key decisions:
  - 사용자 선택값은 API enum으로 전달하되 화면에는 한국어 단계명을 표시한다.
- Issues encountered:
  - None.
- Validation:
  - schema tests와 Frontend 전체 typecheck가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (대외활동 명칭·학력 상태 표시)

- What was done:
  - 내비게이션의 경험 정보를 대외활동으로 바꾸고 Version conflict field에 display formatter를 지원했다.
- Key decisions:
  - 학력 enum은 일반 카드와 conflict 비교 모두 동일한 한국어 label을 사용한다.
- Issues encountered:
  - None.
- Validation:
  - profile component/page tests와 Frontend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (프로필 전체 내비게이션 첫 화면 노출)

- What was done:
  - Desktop profile outline의 모든 부가 설명·footer note를 제거하고 항목명과 현재 위치만 남겼다.
  - sticky top을 global header 높이에 맞추고 행 높이를 축소해 첫 진입에서 7개 항목 전체가 화면 안에 들어오도록 했다.
- Key decisions:
  - Mobile native selector와 7개 deep link·`aria-current` 계약은 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Navigation unit test와 Playwright CLI 1440×1000에서 sidebar top 108px·bottom 541.64px, viewport 1000px를 확인했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (평면 Profile Navigation·희망 조건 입력)

- What was done:
  - Profile desktop navigation에서 번호 card 장식을 제거하고 현재 항목을 명확히 표시하는 좁은 평면 outline으로 정리했으며 mobile native selector는 유지했다.
  - 희망 직무·산업 입력을 연결된 input·추가 button, 선택 tag, 일부 추천과 `추천 더 보기` 구조로 정리하고 산업 preset을 추가했다.
- Key decisions:
  - Profile section별 완료 여부를 판정하는 계약이 없으므로 임의의 완료·미입력 상태는 만들지 않고 현재 위치만 표시했다.
  - 자유 텍스트 배열, Enter 추가, 중복 방지, 최대 10개와 keyboard semantics를 유지했다.
- Issues encountered:
  - None.
- Validation:
  - Profile workspace navigation과 StringListInput unit test, 전체 Frontend check, 1440px·390px Profile fixture가 통과했다.
- Next steps:
  - section completion을 표시하려면 서버 또는 명세의 판정 규칙이 먼저 필요하다.

## [2026-07-28] Session Summary (Profile outline 인접 상태 간격 보정)

- What was done:
  - desktop section outline에 행 간격과 내부 padding을 추가하고 active surface를 한 단계 선명하게 조정해 선택 항목과 인접 hover 영역이 맞닿지 않게 했다.
- Key decisions:
  - route deep link, `aria-current`, mobile native selector와 keyboard semantics는 유지했다.
- Issues encountered:
  - None.
- Validation:
  - 1440px에서 기본 정보 active·학력 hover를 동시에 캡처해 분리된 상태 영역을 확인했고 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (Career Profile Workspace navigation)

- What was done:
  - 가로 tab을 제거하고 desktop sticky vertical outline, mobile native section selector와 이전·다음 action을 추가했다.
  - 기본 정보 저장 성공 후에만 학력으로 이동하는 연속 작성 action과 deep link test를 추가했다.
- Key decisions:
  - 7개 기존 route·browser history·409 보존을 유지하고 실제 데이터가 없는 section 완료 count는 만들지 않았다.
- Issues encountered:
  - mobile selector가 기존 evidence filter test의 첫 select 가정을 깨뜨려 filter 영역 selector로 정확히 한정했다.
- Validation:
  - workspace component, 저장 후 다음 section, 1024·390px navigation·overflow test가 통과했다.
- Next steps:
  - 대외활동 신규 등록은 Backend/API 생성 계약이 마련될 때 별도 수직 기능으로 구현한다.

## [2026-07-28] Session Summary (한국형 희망 조건 추천 입력)

- What was done:
  - 직무·지역 빠른 선택과 입력 문자열을 포함하는 최대 6개 추천을 StringListInput에 추가했다.
  - combobox/listbox/option, ArrowDown·Enter·Escape·ArrowUp 키보드 흐름과 중복·최대 10개 규칙을 검증했다.
- Key decisions:
  - 추천은 정적 한국어 사전으로 제공하고 모든 항목은 기존 자유 입력 배열에 그대로 저장한다.
- Issues encountered:
  - focusout에서 추천 클릭이 닫히지 않도록 focus 이동 경계를 명시했다.
- Validation:
  - 새 component tests와 Playwright keyboard flow, Frontend check가 통과했다.
- Next steps:
  - 직무 taxonomy가 제품 계약으로 생길 때 정적 사전의 소유 위치를 재검토한다.

## [2026-07-28] Session Summary (경험 정보·학력·충돌 문구 재설계)

- What was done:
  - profile navigation의 `직접 입력 근거`를 `경험 정보`로, 대표 학력을 `먼저 보여 줄 학력`으로 바꾸고 conflict·입력 helper를 자연화했다.
  - 경험 정보 metadata를 기존 key와 string·number·boolean·null을 그대로 보존하는 항목형 편집기로 바꾸고 round-trip 경계를 테스트했다.
- Key decisions:
  - `SOURCE_DELETED` read-only와 409 field 재적용 동작은 그대로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - profile page·schema·conflict test와 actual E2E selector의 동일 행동 명칭을 갱신했다.
- Next steps:
  - 실제 경력 timeline의 긴 성과 내용은 Backend 환경에서 추가 검수한다.

## [2026-07-27] Session Summary (Profile navigation·입력·충돌 UX 개선)

- What was done:
  - 구현된 7개 profile route navigation, string chip 입력과 409 비교·재적용 panel을 공통 제품 스타일과 mobile overflow 대응으로 개선했다.
- Key decisions:
  - keyboard 추가·삭제, 사용자별 query key와 자동 overwrite 금지 계약을 유지한다.
- Issues encountered:
  - generic 구조화 profile page의 여러 유형을 분리하지 않고 동일 component 안에서 data 성격별 presentation을 조정했다.
- Validation:
  - profile page/component test와 기존 ID·testid 보존 검사가 통과했다.
- Next steps:
  - 실제 데이터가 많은 경력 timeline은 cross-stack 환경에서 추가 시각 검수한다.

## [2026-07-19] Session Summary (P4 증빙 문서 selector 활성화)

- What was done:
  - 자격증·어학·수상 active document selector와 evidence document filter schema·query key를 활성화했다.
- Key decisions:
  - 선택 후보는 같은 사용자 active document 목록만 사용한다.
- Issues encountered:
  - None.
- Validation:
  - schema·query key·page component와 owner filter E2E가 통과했다.
- Next steps:
  - deleted document는 selector cache에서 즉시 제거한다.

## [2026-07-19] Session Summary (P2 프로필 feature 규칙 구현)

- What was done:
  - 배열 최대 10개·중복, 날짜·GPA·current career schema와 사용자별 query key를 구현했다.
  - 409에서 미저장 값과 최신 snapshot을 비교하고 field별 재적용하는 UI를 구현했다.

- Key decisions:
  - 서버 상태는 Vue Query, form draft는 component local state로 유지하고 Pinia에 프로필 데이터를 저장하지 않는다.
  - version 충돌 mutation은 자동 재시도하지 않는다.

- Issues encountered:
  - 409 snapshot의 GPA 문자열 변환을 form schema와 맞추도록 보정했다.

- Validation:
  - schema·query key·conflict 단위 테스트와 frontend 전체 check가 통과했다.

- Next steps:
  - P4 document 기능은 실제 Backend 계약 확정 뒤 별도 feature로 연결한다.
