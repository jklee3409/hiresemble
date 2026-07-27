# Progress

## Overview

P5 Job 등록·추출·조회·수정·상태·Scheduler·soft delete와 typed Agent Run 연결이 구현됐다.

## [2026-07-27] Session Summary (P5 Job 수직 기능 구현)

- What was done:
  - Job 공개 API 7개와 application/domain/infrastructure 계층을 구현했다.
- Key decisions:
  - 업무 상태와 추출 상태를 분리하고 owner·version·soft delete 조건을 모든 SQL에 적용한다.
- Issues encountered:
  - 초기 validator의 DNS rebinding·stream timeout 지적을 검증된 IP socket 고정과 절대 deadline으로 보정했다.
- Validation:
  - Backend 37 suites/322 tests와 P5 Browser E2E 5/5가 통과했다.
- Next steps:
  - P6에서 현재 `content_hash` 경계를 소비하되 분석 API와 table은 새 forward 변경으로 추가한다.
