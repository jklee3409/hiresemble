# Progress

## Overview

사용자 기능별 form·상호작용 규칙을 page와 공용 기반에서 분리한다. 현재 P1 auth부터 P8 interviews feature까지 구현되어 있다.

## [2026-08-06] Session Summary (경험 소재 카드에서 좌측 색 띠 제거)

- What was done:
  - `DocumentEvidencePanel`의 경험 소재 카드에서 `border-left: 4px` 색 띠를 없애고, 흰 면 + 옅은 그림자 + 큰 모서리 카드로 바꿨다. hover에서만 살짝 떠오른다.
  - 제목을 한 단계 키우고, 동작 버튼은 얇은 구분선 아래 한 줄로 모았다.
  - 활용에서 제외한 소재는 면을 눌러(`surface-subtle`, 그림자 없음) 앞으로 나오지 않게 했다.
- Key decisions:
  - 확인 여부는 제목 옆 상태 badge가 글자로 이미 알려 주므로 색 띠 없이도 정보가 줄지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `vite build`, `eslint .`, `prettier --check` 통과.
- Next steps:
  - None.

## [2026-08-06] Session Summary (AI 검토 결과 가독성 개선)

- What was done:
  - `CoverLetterAssistPanel`에서 검토 결과를 최신 한 건만 펼치고 나머지는 "지난 검토 N개" 접이식으로 내렸다.
  - 검토 카드에 지적 사항 요약(수정 필요·확인 권장 건수)을 붙이고, 카드 경계를 실선 대신 면과 그림자로 바꿨다.
  - 지적 사항은 "수정 필요"를 먼저 정렬하고 심각도별 좌측 색 띠와 채움면, 심각도 chip을 붙여 훑어볼 수 있게 했다.
  - 제안 적용 버튼 문구를 "편집기에 넣기" → "답변에 적용"으로, 안내 문구도 그에 맞게 바꿨다.
  - 좌측 profile outline·자기소개서 rail 등 기존 채움면 요소는 그대로 두었다.
- Key decisions:
  - 정렬과 접기는 표시 방식만 바꾸고 검토 데이터나 emit 계약은 건드리지 않았다.
- Issues encountered:
  - None.
- Validation:
  - `vite build`, `eslint .`, `prettier --check` 통과. Node 20 환경이라 `vitest`는 실행하지 못했다.
- Next steps:
  - None.

## [2026-08-06] Session Summary (feature component soft surface 정렬)

- What was done:
  - `AgentRunProgressDrawer`, `AgentRunDetailPanel`, `InterviewQuestionCard`, `InterviewRunMonitor`, `JobRunMonitor`, `DocumentRunMonitor`, `DocumentEvidencePanel`, `CoverLetterTipTapEditor`, `CoverLetterConflictPanel`, `JobVersionConflictPanel`, `VersionConflictPanel`의 카드 외곽 테두리를 없애고 그림자 또는 채움면으로 바꿨다.
  - 경고 tone panel은 배경색을 유지한 채 `inset 0 0 0 1px`로 경계를 표현해 다른 카드와 두께가 어긋나지 않게 했다.
  - `ProfileTabs`의 좌측 outline을 구분선 대신 떠 있는 카드로 바꾸고 항목을 둥근 행으로 정리했다.
  - `StringListInput`의 입력+추가 버튼 붙임 그룹을 해제했다. 버튼이 알약이 되면서 붙임 처리가 어긋나기 때문이다. 추천 chip과 선택 항목 chip도 무테두리 채움면으로 바꿨다.
  - 테두리를 없애며 무효가 된 hover `border-color` 규칙은 배경 또는 inset ring으로 대체했다.
- Key decisions:
  - 이미 채움면 token을 쓰던 자기소개서 rail·assist panel은 그대로 두었다. assist panel의 밑줄 tab은 레퍼런스의 tab 유형 중 하나라 유지했다.
- Issues encountered:
  - None.
- Validation:
  - `vite build` 성공, `prettier --check` 통과. Node 20 환경이라 `vitest`는 실행하지 못했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 interview feature 연결)

- What was done:
  - user-scoped query/filter, source presentation, question card, answer 409 conflict와 feedback run monitor를 추가했다.
- Key decisions:
  - immutable 사용자 snapshot은 화면 상태에만 유지하고 새 browser storage 체계를 만들지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Interview feature/API component tests와 Frontend 60 files/238 tests가 통과했다.
- Next steps:
  - P9 feature는 해당 단계에서 추가한다.

## [2026-07-31] Session Summary (프로필 닉네임·내비게이션 규칙)

- What was done:
  - Auth feature에 재사용 가능한 nickname schema를 추가하고 Profile feature navigation을 label-only 구조와 header-safe sticky offset으로 보정했다.
- Key decisions:
  - 가입과 프로필 변경은 같은 `displayName` validation 경계를 공유한다.
- Issues encountered:
  - None.
- Validation:
  - 관련 feature unit test와 Frontend 53 files/214 tests가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 cover-letters feature)

- What was done:
  - filter·query, TipTap content, user/base-version session draft, 409 비교, generation/verification monitor와 관련 component test를 추가했다.
- Key decisions:
  - 서버 authoritative content/version을 보존하고 suggestion 적용은 editor만 변경하며 자동 저장하지 않는다.
- Issues encountered:
  - 실제 form number input 타입 결함을 component regression으로 고정했다.
- Validation:
  - cover-letter feature tests, Frontend 전체 204 tests와 actual P7 E2E가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (Feature Presentation B2C 용어 통일)

- What was done:
  - auth, profile, documents, jobs, agent-runs의 validation·status·conflict·drawer 문구를 하나의 소비자 용어 체계로 연결했다.
- Key decisions:
  - domain enum·query·stream 상태 machine은 유지하고 label·guidance만 변경한다.
- Issues encountered:
  - API parser fallback에 남은 `Agent Run` 문구 두 곳을 루트 통합에서 copy-only로 정리했다.
- Validation:
  - feature unit/component test와 기술 용어 노출 정적 검사가 통과했다.
- Next steps:
  - P6 이후 feature는 실제 계약이 생길 때만 소비자 표현을 추가한다.

## [2026-07-27] Session Summary (현재 Feature 상태 표현 통일)

- What was done:
  - profile navigation·chip·conflict, Document evidence/Run monitor, Job conflict/Run monitor와 Agent Run detail/drawer의 정보 계층을 개선했다.
- Key decisions:
  - query·mutation·SSE state machine은 유지하고 visible label, action priority와 responsive presentation만 변경했다.
- Issues encountered:
  - 동일 색상처럼 보일 수 있는 상태는 공용 label과 보조 설명을 함께 사용하도록 조정했다.
- Validation:
  - feature unit/component test와 fixture Agent Run E2E가 통과했다.
- Next steps:
  - 미구현 P6 feature를 디자인 목적으로 선행 생성하지 않는다.

## [2026-07-27] Session Summary (P5 jobs feature 추가)

- What was done:
  - Job filter·query·mutation·Run monitor·version conflict feature를 추가했다.
- Key decisions:
  - REST Job 상태를 원천으로 삼고 SSE는 terminal·WAITING_USER invalidation 신호로만 사용한다.
- Issues encountered:
  - P6 분석 DTO 선행 구현과 NEEDS_MANUAL_INPUT retry 노출을 validator 보정에서 제거했다.
- Validation:
  - Frontend 32 files/122 tests와 P5 Browser E2E 5/5가 통과했다.
- Next steps:
  - P6 analysis feature는 새 계약 이후 추가한다.

## [2026-07-19] Session Summary (P4 documents feature 추가)

- What was done:
  - user-scoped document query key, filter canonicalization, upload·manual·reparse·delete와 Run monitor를 추가했다.
- Key decisions:
  - SSE disconnect는 실패가 아니며 terminal·WAITING_USER event는 REST query invalidation만 유도한다.
- Issues encountered:
  - WAITING_USER race를 detail invalidation으로 보정했다.
- Validation:
  - targeted 9 tests와 Frontend 전체 95 tests가 통과했다.
- Next steps:
  - P6 retrieval feature는 별도 phase로 남긴다.

## [2026-07-19] Session Summary (P3 agent-runs feature 추가)

- What was done:
  - filter·query·presentation·SSE controller와 detail panel·Progress Drawer를 추가했다.

- Key decisions:
  - Run 상태는 server projection만 사용하고 연결 상태를 별도 UI 안내로 관리한다.

- Issues encountered:
  - None.

- Validation:
  - feature contract·stream·query·component tests가 전체 check에서 통과했다.

- Next steps:
  - resource-specific 동작은 후속 domain feature에 둔다.

## [2026-07-19] Session Summary (P2 profile feature 경계 추가)

- What was done:
  - profile Zod, query key, version conflict와 공용 입력 component를 실제 page 사용처와 함께 추가했다.

- Key decisions:
  - 서버 권한·완료도는 Backend 응답을 사용하고 UI feature는 form·cache·표현 규칙만 소유한다.

- Issues encountered:
  - None

- Validation:
  - schema·query key·conflict 테스트와 frontend 전체 check가 통과했다.

- Next steps:
  - P3 이후 feature는 실제 API·화면 구현 시점에만 추가한다.

## [2026-07-19] Session Summary (P1 auth feature 경계 구성)

- What was done:
  - 인증 Form validation만 실제 사용처와 함께 추가했다.

- Key decisions:
  - P2 feature directory는 해당 화면·API 구현 시점에 생성한다.

- Issues encountered:
  - None

- Validation:
  - Frontend lint·typecheck·feature unit test가 통과했다.

- Next steps:
  - 새 기능은 route page와 API 계약이 함께 생길 때 추가한다.
