# Progress

## Overview

P5 Job filter·query·mutation·stream·version conflict feature가 구현됐다.

## [2026-07-28] Session Summary (지원 상태·공고 불러오기 UX Writing 적용)

- What was done:
  - `업무 상태`를 `지원 상태`, extraction을 `공고 불러오기`로 표현하고 manual·failed·conflict guidance를 다음 행동 중심으로 바꿨다.
- Key decisions:
  - JobStatus와 JobExtractionStatus, 201/202·idempotency·retry·manual 입력 의미는 그대로 분리 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Job presentation/conflict/page test와 관련 fixture E2E가 통과했다.
- Next steps:
  - P6 분석 label·route는 계약 구현 전 추가하지 않는다.

## [2026-07-27] Session Summary (Job 상태·충돌 UI 개선)

- What was done:
  - Run monitor와 version conflict 비교·재적용 panel의 정보 계층, action priority와 responsive 표현을 개선했다.
- Key decisions:
  - 업무 상태와 extraction 상태를 별도 label로 유지하고 NEEDS_MANUAL_INPUT·FAILED action 의미를 바꾸지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Job component/page test와 전체 Frontend check가 통과했다.
- Next steps:
  - P6 analysis action과 DTO는 계약 구현 전 추가하지 않는다.

## [2026-07-27] Session Summary (P5 Jobs frontend feature 구현)

- What was done:
  - URL query canonicalization, 생성/수정/상태/retry/delete mutation과 Job Run monitor를 추가했다.
- Key decisions:
  - NEEDS_MANUAL_INPUT은 수동 입력만 강조하고 retry는 FAILED에만 제공한다.
- Issues encountered:
  - 초기 P6 분석 DTO 선행 구현을 validator 보정에서 제거하고 P5 projection을 strict null·false·빈 값으로 제한했다.
- Validation:
  - Frontend 32 files/122 tests와 P5 Browser E2E 5/5가 통과했다.
- Next steps:
  - P6에서 분석 feature를 별도 계약으로 추가한다.
