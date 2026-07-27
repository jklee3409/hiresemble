# Progress

## Overview

P5 Job 상태 전이·timestamp·URL canonicalization 정책이 구현됐다.

## [2026-07-27] Session Summary (Job domain 규칙 구현)

- What was done:
  - 허용/금지 전이, submittedAt 보존, reopen과 canonical URL 규칙을 구현했다.
- Key decisions:
  - percent-encoding은 unreserved 문자만 해제하고 reserved escape는 대문자 escape로 보존한다.
- Issues encountered:
  - 초기 구현의 `%2F`와 `/`, `+`와 `%20` false duplicate 위험을 validator 보정에서 해소했다.
- Validation:
  - 모든 전이와 canonical 동등/비동등 unit test 및 duplicate 통합 테스트가 통과했다.
- Next steps:
  - P6 stale 계산은 현재 content hash를 입력으로 사용한다.
