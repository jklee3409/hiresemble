# Progress

## Overview

P3 workflow launch, state, checkpoint, budget, retry·cancel·resume와 domain apply port가 구현됐다.

## [2026-07-19] Session Summary (Agent Run application port와 transaction 구현)

- What was done:
  - 내부 `WorkflowLauncher`와 query/state/checkpoint/budget/usage/retry/cancel/resume port를 구현했다.
  - blocking gateway 호출 중 DB lease를 주기적으로 갱신하는 `AgentRunLeaseHeartbeatPort`를 추가했다.
  - predecessor당 successor 하나, replay 가능한 idempotency metadata와 cancellation completion을 연결했다.
  - signup transaction에 기본 AI preference 생성을 추가했다.

- Key decisions:
  - workflow는 repository 대신 application port만 사용한다.
  - WAITING_USER는 같은 Run을 QUEUED로 resume하고 terminal retry는 새 lineage Run을 만든다.

- Issues encountered:
  - retry body가 없으므로 retry-scope canonical hash는 `{}`이며 일반 hash mismatch는 공통 idempotency 테스트가 검증한다.

- Validation:
  - lineage·동시 retry·signup 실패 rollback·cancel compensation·resume와 장시간 호출 heartbeat PostgreSQL 테스트가 통과했다.

- Next steps:
  - 실제 resource owner resolution과 apply adapter는 각 P4 이후 domain에서 제공한다.
