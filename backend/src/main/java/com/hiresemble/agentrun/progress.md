# Progress

## Overview

P3 Agent Run·Step, 비용, DB worker, retry·cancel과 SSE 기반이 구현됐으며 실제 provider와 P4 resource 연결은 비활성 상태다.

## [2026-07-19] Session Summary (P3 Agent Run 수명주기와 비용·복구 기반 구현)

- What was done:
  - 7개 Run 상태와 9개 Step 상태, 전체 전이·terminal 불변, WAITING resume와 terminal retry를 구현했다.
  - workflow launcher/query/state/checkpoint/retry/cancel/budget/usage/apply·compensation port를 만들고 JDBC claim·주기 heartbeat·lease·reconciliation을 연결했다.
  - owner-scoped 목록·상세·retry·cancel과 snapshot-first SSE의 정확한 5개 operation을 추가했다.

- Key decisions:
  - DB가 유일한 상태 원천이며 executor rejection 전에는 row를 claim하지 않는다.
  - stale RUNNING은 `INTERRUPTED` terminal로 닫고 사용자 retry만 successor Run을 만든다.
  - P3 Fake Run은 resource pair가 null이며 typed resource FK는 P4 이후 forward migration으로 남긴다.

- Issues encountered:
  - SSE 요청의 `Accept: text/event-stream`에서 owner 오류가 406으로 재변환되지 않으면서 P1 공통 계약을 지키도록 성공 SSE와 6-field JSON 404 media type을 함께 선언했다.
  - V4 owner FK 추가 뒤 기존 idempotency fixture를 실제 owner Run을 생성하도록 보정했다.
  - 최초 Validator의 두 MAJOR인 SSE 빈 404와 장시간 gateway heartbeat 경계를 보정하고 한 차례 재검증했다.

- Validation:
  - Backend 전체 `check --rerun-tasks`에서 21 suites, 243 tests가 failure·error·skip 0으로 통과했다.
  - PostgreSQL에서 claim 경쟁, queue saturation, restart·stale recovery, blocking 호출 heartbeat, budget 경쟁, retry·cancel과 SSE race를 검증했다.
  - 최종 read-only Validator 판정은 `PASS`다.

- Next steps:
  - P4/P5가 실제 resource aggregate를 만들 때 typed owner FK와 domain result apply를 forward migration·port adapter로 연결한다.
