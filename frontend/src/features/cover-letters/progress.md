# Progress

## Overview

P7 자기소개서 filter·query·TipTap editor·session draft·작업별 409 비교·Agent Run UI가 actual Chromium과 최종 validator `PASS`로 완료됐다.

## [2026-08-06] Session Summary (끝난 AI 작업 bar 제거와 소재 고르기 분리)

- What was done:
  - `CoverLetterRunMonitor.vue`가 진행 중인 run만 표시하도록 했다. 성공·실패·취소로 끝난 run의 상태 bar와 loading 줄은 노출하지 않으며 terminal emit은 그대로 유지한다. 실패 결과는 page의 toast로만 알린다.
  - `CoverLetterMaterialPicker.vue`를 추가해 답변에 쓸 소재 선택을 작성 도움에서 분리했다. `CoverLetterAssistPanel.vue`는 `공고 요구사항`·`AI 검토 결과` 두 tab만 남기고 evidence props·event를 제거했다.
  - 작성 도움 본문이 편집 영역 높이 안에서만 스크롤하도록 `height: 100%`와 `overflow-y: auto`를 적용했다.
- Key decisions:
  - 끝난 작업 bar는 성공·실패 모두 남기지 않고 실패는 toast와 남은 문항 재선택으로 안내한다.
  - 소재 선택은 modal이 아니라 현재 단계 주요 행동 button에 붙어 펼쳐지고 다른 영역 위에 겹친다.
- Issues encountered:
  - jsdom은 SFC scoped style을 적용하지 않아 `getComputedStyle`로 겹침을 검증할 수 없어 DOM 구조로 검증했다.
- Validation:
  - 컨테이너(node:24) 전체 검사(eslint·prettier·vue-tsc·Vitest 69 files/310 tests·build) 통과.
- Next steps:
  - None.

## [2026-08-06] Session Summary (자기소개서 AI 모델 dropdown)

- What was done: 생성 panel에 서버 catalog 기반 model dropdown을 추가하고 생성·검증 mutation identity와 payload에 선택 모델을 반영했다.
- Key decisions: 추천 모델은 catalog의 `recommended` 표시를 따르고 catalog가 없으면 유료 작업 접수를 막는다.
- Issues encountered: None.
- Validation: Frontend `pnpm check` 69 files·308 tests, lint·format·typecheck·build 통과.
- Next steps: 실제 Chromium에서 desktop/mobile dropdown 상호작용을 확인한다.

## [2026-08-06] Session Summary (번호 전용 문항 rail과 화면 높이 편집기)

- What was done:
  - `CoverLetterQuestionRail.vue`에 `compact | list` variant를 넣었다. `compact`는 번호만 있는 2.75rem 사각 button과 상태 색 점, `list`는 질문 preview·상태 문구까지 보여 준다. 상태 문구는 두 variant 모두 `aria-label`에 포함한다.
  - `CoverLetterTipTapEditor.vue` 본문을 고정 `min-height` 대신 화면 높이 기반 `height` + 내부 스크롤로 바꾸고 글자 크기를 0.8125rem, 여백을 줄였다. `--cover-editor-height`로 상위에서 덮어쓸 수 있다.
- Key decisions:
  - 번호만 남겨도 상태를 잃지 않도록 색 점과 접근 가능한 이름을 함께 쓴다.
  - 답변이 길어져도 페이지 전체가 늘어나지 않게 편집 본문만 스크롤한다.
- Issues encountered:
  - None.
- Validation:
  - 컨테이너(node:24) 집중 Vitest 9 files/50 tests와 전체 69 files/307 tests·build 통과.
- Next steps:
  - None.

## [2026-08-06] Session Summary (진행 표시 축소와 작성 도움 tab 분리)

- What was done:
  - `CoverLetterRunMonitor.vue`를 한 줄 요약 `details`로 바꿔 세로 영역을 줄이고, 연결 상태·완료/실패 문항·`AI 작업 상세`는 펼침 영역으로 옮겼다. 실패 문항이 있으면 기본 펼침이다.
  - `AssistTab`을 `MATERIAL | JOB | REVIEW`로 나누고 `CoverLetterAssistPanel.vue`에 `쓸 소재` tab을 분리했다. 선택 개수 요약, `모두 해제`, 아직 쓰지 않은 소재와 이미 쓴 소재 구분, 경험이 없을 때의 자료 등록 link를 추가했다.
- Key decisions:
  - 소재 선택은 읽기 정보인 공고 요구사항과 같은 tab에 두지 않는다.
  - `모두 해제`는 page의 선택 상태를 바꾸므로 `clear-evidence` event로 올린다.
- Issues encountered:
  - None.
- Validation:
  - 컨테이너(node:24) 전체 검사(eslint·prettier·vue-tsc·Vitest 69 files/307 tests·build) 통과.
- Next steps:
  - None.

## [2026-08-06] Session Summary (자기소개서 작성 화면 정보 구조 재설계)

- What was done:
  - `editorFlow.ts`를 추가해 문항별 작성·검토 상태, 작성 완료까지 남은 조건, 단일 primary 행동 판정을 한곳으로 모았다. 판정 규칙은 Backend `finalizeCover`(모든 문항 답변 저장·최대 글자 수·최신 답변 기준 검토 통과·확인 필요 동의)와 같은 조건을 사용한다.
  - 편집 화면 component를 `CoverLetterQuestionRail`, `CoverLetterAssistPanel`, `CoverLetterSheet`, `CoverLetterGenerationPanel`, `CoverLetterVersionPanel`, `CoverLetterCompletionPanel`로 분리했다.
  - AI 설정, 버전 기록, 작성 완료 점검, 문항 추가·수정을 sheet로 옮기고 기본 화면에서는 문항 목록·편집기·작성 도움만 남겼다.
  - 작성 도움에서 이 답변에 실제로 쓰인 경험(최신 검토의 근거)과 아직 쓰지 않은 경험을 구분하고, 고른 경험이 다음 초안에 쓰인다는 문구를 붙였다.
  - 버전 panel에서 고른 과거 저장본의 검토 결과를 따로 조회해 근거 상태 확인 기능을 유지했다.
- Key decisions:
  - AI 검토는 Backend 계약상 작성 완료 필수 조건이므로 선택 기능으로 표현하지 않는다.
  - 재작성은 기존 답변을 지우지 않고 새 버전으로 저장되므로 실행 전에 비파괴 동작과 미저장 내용 여부를 알린다.
  - 편집기 내용은 `currentAnswer.id` 변화에도 다시 불러오고, 미저장 본문은 sessionStorage draft 복구로 보존한다.
  - 409 비교와 오류 안내가 sheet 뒤에 가리지 않도록 오류 발생 시 열린 sheet를 닫는다.
- Issues encountered:
  - `.generation-panel` DOMWrapper를 재사용하면 재렌더 뒤 하위 요소를 찾지 못해 설정 test가 조용히 통과하지 않았다. wrapper 기준 재조회로 바꿨다.
  - 로컬 Node 20.18.0에서는 jsdom 의존성의 `require(ESM)` 때문에 vitest를 실행할 수 없어 `node:24` 컨테이너에서 검증했다.
- Validation:
  - 컨테이너(node:24)에서 eslint, prettier, `vue-tsc -b --force`, Vitest 69 files/307 tests, `vite build`가 모두 통과했다.
- Next steps:
  - 실제 브라우저에서의 시각·반응형 확인과 P7 actual E2E 재실행은 미수행이다.

## [2026-08-06] Session Summary (AI 생성 완료 문항 한 줄 preview)

- What was done:
  - `CoverLetterRunMonitor.vue`의 생성 완료 문항 label을 공백 정규화 후 Unicode 문자 기준 48자로 제한하고 말줄임표를 붙였다.
  - 전체 질문은 `aria-label`과 hover `title`에 보존하고 CSS 한 줄 ellipsis를 함께 적용했다.
  - 전용 component test를 추가해 축약 길이와 전체 접근성 label 보존을 검증했다.
- Key decisions:
  - 재시도가 필요한 실패 문항은 문제 식별을 위해 기존 전체 label 표시를 유지한다.
- Issues encountered:
  - None.
- Validation:
  - 집중 Vitest 2 files/15 tests와 Frontend 전체 `corepack pnpm check` 68 files/287 tests·production build가 통과했다.
- Next steps:
  - None.

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
