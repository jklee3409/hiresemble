# Progress

## Overview

P7 자기소개서 filter·query·TipTap editor·session draft·작업별 409 비교·Agent Run UI가 actual Chromium과 최종 validator `PASS`로 완료됐다.

## [2026-08-06] Session Summary (자기소개서 편집기 전체 폭과 초점 표현)

- What was done:
  - 편집 wrapper의 `focus-within` 파란 border·ring을 제거하고 실제 TipTap 입력 영역의 46rem 최대 폭을 없애 wrapper 전체 폭을 사용하도록 했다.
  - 입력 면은 focus 시 미세한 배경 변화만 사용하고 `box-sizing`과 `width: 100%`를 명시했다.
- Key decisions:
  - keyboard focus 자체는 편집 caret과 내용 면 변화로 유지하면서 중첩 panel 전체를 파란 선으로 둘러싸지 않는다.
- Issues encountered:
  - None.
- Validation:
  - component test와 Frontend 전체 `check` 통과. Chromium 측정에서 wrapper와 content 폭이 모두 991px이고 focus border가 gray로 유지됨을 확인했다.
- Next steps:
  - None.

## [2026-08-06] Session Summary (충돌 안내 component test 정합성 보정)

- What was done:
  - `CoverLetterConflictPanel.test.ts`가 새 사용자 문구인 `저장된 답변이 그 사이에 바뀌었어요.`와 `저장하는 중…`을 검증하도록 보정했다.
- Key decisions:
  - HTTP·version 내부 용어 대신 실제 화면에 표시되는 사용자 문구를 회귀 경계로 유지한다.
- Issues encountered:
  - 최초 Frontend 전체 검사에서 과거 `현재 답변 버전`·`재적용 중` 문구 assertion 2개가 실패했다.
- Validation:
  - 집중 Vitest와 최종 `corepack pnpm check`가 통과했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (사용자 언어 label과 충돌 안내 문구 정리)

- What was done:
  - `presentation.ts`의 공용 label을 사용자 언어로 고쳤다. 상태 `최종화 → 작성 완료`, 검토 `검증 중 → 검토 중`·`통과 → 문제없음`·`검증 실패 → 수정 필요`, 작성 출처 `사용자 저장 → 내가 쓴 글`·`과거 버전 복원 → 되돌린 내용`, issue code와 severity 문구, 근거 현재 상태 `현재 승인 거절됨 → 지금은 사용 안 함` 계열을 정리했다.
  - `conflict.ts`의 `현재 답변 버전이 달라졌어요` 문구와 `CoverLetterConflictPanel.vue`의 `409 버전 충돌` eyebrow·비교 제목·button label을 `다른 곳에서 먼저 저장됐어요`·`지금 저장된 내용`·`내가 쓰던 내용`·`내가 쓰던 내용으로 저장`·`저장된 내용 그대로 두기`로 바꿨다.
  - `CoverLetterTipTapEditor.vue` 본문 폭을 46rem으로 제한하고 좁은 화면에서 서식 button이 폭을 나눠 갖도록 했다.
- Key decisions:
  - label 값만 바꾸고 enum·API 계약·tone 매핑은 그대로 둔다. 이 label을 함께 쓰는 목록·공고 tab 화면도 같은 문구를 사용한다.
  - 사용자 문구에서 HTTP status와 `immutable` 같은 내부 용어를 제거하되 개발자 로그와 계약 문서에서는 유지한다.
- Issues encountered:
  - 로컬 Node 20.18.0에서 `vitest` 실행이 불가능해 component test는 미검증이다.
- Validation:
  - `eslint`, `prettier --check`, `vue-tsc -b --force`, `vite build` 통과와 실제 Chromium fixture 캡처로 확인했다.
- Next steps:
  - Node 24 환경에서 `corepack pnpm check`로 component test 회귀를 확인한다.

## [2026-08-05] Session Summary (작성 화면 개편에 맞춘 feature component 정리)

- What was done:
  - `CoverLetterRunMonitor.vue`에 avatar와 상태별 코치 문장(`AI 코치가 초안을 쓰고 있어요`·`다 썼어요`·`끝내지 못했어요`)을 추가하고 panel surface로 다듬었다.
  - `CoverLetterTipTapEditor.vue`를 읽기 폭 46rem·1.85 행간 문서 면으로 조정하고, 정의되지 않은 `--color-focus-ring`을 쓰던 focus box-shadow를 공용 `--focus-ring`으로 고쳤다. 좁은 화면에서 서식 button이 폭을 나눠 갖도록 했다.
  - `CoverLetterConflictPanel.vue`의 radius·padding·제목 위계를 새 화면 언어에 맞췄다.
- Key decisions:
  - 세 component 모두 props·emit·DOM 계약(`data-testid`, `.cover-conflict` button 구성, 진행률 `progress`)을 바꾸지 않고 표현만 조정한다.
  - Run 문장은 workflow type과 서버 상태에서만 유도하고 완료된 run을 진행 중처럼 표시하지 않는다.
- Issues encountered:
  - 로컬 Node 20.18.0에서 `vitest` 실행이 불가능해 component test는 미검증이다.
- Validation:
  - `eslint`, `prettier --check`, `vue-tsc -b --force`, `vite build` 통과와 실제 Chromium fixture 캡처로 확인했다.
- Next steps:
  - Node 24 환경에서 `corepack pnpm check`로 component test 회귀를 확인한다.

## [2026-07-30] Session Summary (P7 cover-letter feature 최종 판정)

- What was done:
  - 최종 read-only validator가 draft 수명주기, query invalidation, archived read-only, historical evidence와 409 snapshot 보정을 재검증했다.
- Key decisions:
  - server authoritative 본문·글자 수·version 계약을 P7 완료 기준선으로 유지한다.
- Issues encountered:
  - 새 finding 없음.
- Validation:
  - Validator `PASS`, Frontend 53 files/211 tests와 P7 actual Chromium 1/1 PASS.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 작업별 409 snapshot 보정)

- What was done:
  - TITLE·QUESTION·ORDER·ANSWER·LIFECYCLE별 최초 mutation input을 immutable snapshot으로 만들고 실제 최신 server title/question/order/current answer/status와 나란히 표시한다.
  - 재적용은 snapshot과 최신 CAS를 결합하고 취소는 추가 mutation 없이 refetch된 server state를 유지하도록 분리했다.
- Key decisions:
  - conflict 발생 뒤 form/editor가 바뀌어도 최초 저장 시도 내용을 암묵적으로 바꾸지 않으며 mutation 자동 재시도는 사용하지 않는다.
- Issues encountered:
  - question refetch watcher가 form을 갱신하는 기존 동작과 충돌하지 않도록 retry payload를 reactive form 밖에 보존했다.
- Validation:
  - question/order/answer 비교·재적용·취소 회귀와 Frontend 53 files/211 tests, actual 문항 충돌 Chromium 시나리오가 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 기다린다.

## [2026-07-30] Session Summary (P7 자기소개서 편집 feature)

- What was done:
  - URL filter, typed queries, 제한된 TipTap editor, 명시적 저장, version compare/restore, verification suggestion, finalize와 archive 상태를 연결했다.
  - 24시간 sessionStorage draft의 user/base-version 격리, save/delete/archive/logout/401 purge와 base mismatch 비교 UI를 구현했다.
- Key decisions:
  - 서버 본문·글자 수가 authoritative이고 generation·verification terminal event는 관련 detail/version/verification query만 invalidate한다.
- Issues encountered:
  - 실제 Browser form에서 number input 값이 문자열 parser에 전달된다고 가정한 오류를 component regression test와 함께 보정했다.
- Validation:
  - Frontend 53 files/211 tests, production build와 P7 actual Chromium 1/1·1440/390px overflow 검사가 통과했다.
- Next steps:
  - 독립 validator 판정을 반영한다.
