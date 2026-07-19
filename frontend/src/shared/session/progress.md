# Progress

## Overview

logout·401·사용자 ID 변경 시 사용자 경계를 폐기하는 순서와 최소 확장 port를 관리한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (인증 경계 cleanup port 구현)

- What was done:
  - EventSource 종료→query 취소→cache clear→Pinia reset→현재 user draft purge 순서를 구현했다.

- Key decisions:
  - draft는 sessionStorage의 canonical user ID key shape만 선택 삭제한다.

- Issues encountered:
  - None

- Validation:
  - cleanup order와 두 사용자 draft 분리 unit test가 통과했다.

- Next steps:
  - 실제 SSE·draft 기능은 이 port에 자신의 최소 cleanup adapter만 등록한다.
