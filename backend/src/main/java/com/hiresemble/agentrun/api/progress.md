# Progress

## Overview

P3 Agent Run 공개 5 operation과 snapshot-first SSE projection이 구현됐다.

## [2026-07-19] Session Summary (Agent Run API와 SSE 계약 구현)

- What was done:
  - 목록 filter·pagination·sort, 상세 timeline, retry Idempotency-Key와 cancel stateVersion CAS를 구현했다.
  - snapshot, progress, step, waiting_user, heartbeat, terminal event와 subscriber race buffering을 구현했다.

- Key decisions:
  - 타 사용자 Run과 resource filter는 404로 숨기고 P3 resource 성공 경로를 만들지 않는다.
  - terminal 뒤 emitter를 닫고 재연결은 새 DB snapshot부터 시작한다.

- Issues encountered:
  - 최초 Validator가 SSE owner 404의 빈 본문을 공통 오류 계약 위반으로 판정해, SSE 성공 응답은 `text/event-stream`을 유지하면서 owner 실패는 기존 6-field JSON 오류 DTO로 반환하도록 보정했다.

- Validation:
  - MockMvc·실제 PostgreSQL SSE 테스트에서 타 사용자 404의 공통 필드와 content type을 검증했고 OpenAPI 35 operation/24 path 회귀가 통과했다.

- Next steps:
  - P4 이후 typed resource가 생기면 resource filter 성공 경로와 관련 resource invalidation을 연결한다.
