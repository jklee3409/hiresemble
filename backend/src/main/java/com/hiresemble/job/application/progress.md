# Progress

## Overview

P5 Job use case와 transaction·Clock·Agent Run 조정 경계가 구현됐다.

## [2026-07-27] Session Summary (Job application use case 구현)

- What was done:
  - 수동/URL 생성, WAITING_USER resume, terminal retry, 상태·삭제와 batch Scheduler를 구현했다.
- Key decisions:
  - Job과 최초 Run은 같은 transaction, URL fetch는 transaction 밖, 상태와 history는 한 transaction이다.
- Issues encountered:
  - Scheduler와 사용자 command 경쟁은 DB 조건부 update와 version으로 한쪽만 성공하게 고정했다.
- Validation:
  - owner 404, version conflict, retry/resume, soft delete와 Scheduler race 통합 테스트가 통과했다.
- Next steps:
  - P6 분석 use case는 별도 application 경계로 추가한다.
