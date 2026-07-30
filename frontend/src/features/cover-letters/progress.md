# Progress

## Overview

P7 자기소개서 filter·query·TipTap editor·session draft·작업별 409 비교·Agent Run UI가 actual Chromium과 최종 validator `PASS`로 완료됐다.

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
