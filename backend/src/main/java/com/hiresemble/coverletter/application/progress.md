# Progress

## Overview

P7 자기소개서 application use case와 generation·verification port가 구현됐다.

## [2026-08-05] Session Summary (Cover Letter v2 launch와 sibling snapshot)

- What was done:
  - 신규 generation·verification launch version을 v2로 전환하고 verification snapshot에 다른 문항의 current answer summary를 owner scope로 구성했다.
- Key decisions:
  - v2 sibling 본문 hash를 snapshot freshness에 포함하되 durable v1 loader/hash는 기존 계약을 유지하고 API·DB·immutable version·CAS 계약은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - application/integration 회귀와 Backend 전체 check 통과.
- Next steps:
  - 사용자 보완 action/resume은 Frontend와 함께 별도 계약으로 진행한다.

## [2026-07-31] Session Summary (P8 owner-aware resource resolver 연결)

- What was done:
  - Cover Letter·answer version Agent Run resource owner lookup을 강화된 user-aware resolver 계약에 맞췄다.
- Key decisions:
  - P7 공개 동작과 자기소개서 lifecycle은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Backend 전체 check와 final-source P7 actual Chromium 1/1·DB assertions가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 수명주기·AI application 경계)

- What was done:
  - owner-scoped CRUD, 문항 정렬, immutable save/restore, generation·verify Run 접수, partial apply, verification persist와 finalize/archive/unarchive transaction을 구현했다.
- Key decisions:
  - AI apply 직전에 cover letter/question/current version CAS를 재검증하고 성공 문항만 독립 transaction으로 저장한다.
- Issues encountered:
  - 문서 삭제 시 historical provenance 보호를 위해 기존 `EvidenceReferenceQueryPort`에 자기소개서 참조를 기여했다.
- Validation:
  - application·finalization·Agent Run 통합 테스트와 actual P7 DB assertions가 통과했다.
- Next steps:
  - 독립 validator 판정을 반영한다.
