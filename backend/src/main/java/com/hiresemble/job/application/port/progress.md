# Progress

## Overview

P5 Job fetch와 workflow query/mutation port가 구현됐다.

## [2026-07-27] Session Summary (Job 외부·AI port 정의)

- What was done:
  - 페이지 fetch 분류와 Job snapshot/apply 경계를 typed interface로 추가했다.
- Key decisions:
  - 외부 오류는 safe code·retryable만 전달하고 원 URL 상세나 provider 응답을 전달하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Fake gateway·orchestrator 통합 테스트로 transaction 밖 호출과 상태 반영을 검증했다.
- Next steps:
  - P6 retrieval port는 이번 경계에 선행 추가하지 않는다.
