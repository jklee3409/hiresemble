# Progress

## Overview

logout·401·사용자 ID 변경 시 사용자 경계를 폐기하는 순서와 P3 Agent Run EventSource 등록을 관리한다.

## [2026-07-19] Session Summary (P3 Agent Run EventSource cleanup 연결)

- What was done:
  - AgentRunStreamController가 실제 source·reconnect timer·polling을 cleanup coordinator에 등록한다.

- Key decisions:
  - logout·401·사용자 변경은 EventSource 종료 뒤 query cancel·cache clear 순서를 유지한다.

- Issues encountered:
  - None.

- Validation:
  - stream unit test와 logout Playwright fixture, 기존 queryClient clear 회귀가 통과했다.

- Next steps:
  - 후속 SSE 기능도 동일 coordinator에 등록한다.

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
