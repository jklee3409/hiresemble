# Progress

## Overview

P3 workflow launch, state, checkpoint, budget, retry·cancel·resume와 domain apply port가 구현됐다.

## [2026-07-19] Session Summary (P4 typed Document resource 연결)

- What was done:
  - Document owner resolution, display label, deletion check와 cancel compensation 경계를 연결했다.
  - Agent Run 목록의 `DOCUMENT` resource filter가 active owner resolver를 통과한 뒤 typed resource criteria를 repository에 전달하도록 연결했다.
- Key decisions:
  - typed link가 공개 `resourceType/resourceId`의 원천이며 resource-linked retry도 owner를 다시 검증한다.
- Issues encountered:
  - 최초 P4 Validator가 application의 P3 예약 404 때문에 Document resource filter repository가 도달 불가능한 점을 MAJOR로 발견했다.
- Validation:
  - active owner Document 성공, 타 사용자·없는·삭제 Document 404, upload typed link, generic retry lineage와 delete cancel이 통과했다.
  - 허용된 한 차례 보정 뒤 최종 read-only Validator 판정은 `PASS`다.
- Next steps:
  - P5 Job typed link는 실제 aggregate와 함께 추가한다.

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
