# Progress

## Overview

P3 fixed-sequence AgentOrchestrator와 checkpoint·usage·apply 경계가 구현됐다.

## [2026-07-19] Session Summary (bounded AgentOrchestrator 구현)

- What was done:
  - claim된 Run을 registry 순서로 실행하고 호출 중 주기 heartbeat, cancel, reuse, budget, validation, usage와 idempotent apply를 연결했다.
  - WAITING resume, terminal failure·interruption과 reserve 정리를 구현했다.

- Key decisions:
  - checkpoint와 domain apply는 port별 transaction으로 수행하고 gateway 호출은 transaction 밖에 둔다.

- Issues encountered:
  - resume 시 PENDING attempt를 그대로 재개하고 model policy version을 context에서 checkpoint까지 전달하도록 보정했다.
  - 최초 Validator 지적에 따라 gateway 호출 전후의 단발 heartbeat를 별도 scheduler 기반 주기 heartbeat port로 교체했다.

- Validation:
  - 실제 PostgreSQL Fake 3-step integration과 lease보다 긴 blocking gateway/reconciliation 경쟁 테스트가 통과했다.

- Next steps:
  - P4 이후 실제 contribution은 현재 heartbeat port를 재사용하고 provider별 timeout을 추가한다.
