# Progress

## Overview

P3 PostgreSQL persistence, bounded worker, claim·heartbeat·lease와 reconciliation이 구현됐다.

## [2026-07-19] Session Summary (typed Document link projection 적용)

- What was done:
  - Agent Run 조회가 authoritative `agent_run_resource_links`에서 Document resource를 계산하도록 확장했다.
- Key decisions:
  - V4 legacy projection과 typed link parity는 V5 deferred trigger로 강제한다.
- Issues encountered:
  - None.
- Validation:
  - same-user composite FK, run당 primary Document unique와 retry projection이 통과했다.
- Next steps:
  - 미래 resource column을 미리 만들지 않는다.

## [2026-07-19] Session Summary (DB claim과 worker 복구 구현)

- What was done:
  - 조건부 claim, 2-thread/32-capacity 기본 executor와 QUEUED scan을 구현했다.
  - 15초 heartbeat·60초 lease·30초 reconciliation 설정, blocking gateway 호출용 별도 heartbeat scheduler와 stale Run·Step interruption을 구현했다.
  - atomic ledger reserve·top-up·settle·release와 zero-cost usage 저장을 구현했다.

- Key decisions:
  - queue rejection 전에는 row를 claim하지 않아 reconciliation이 다시 발견한다.
  - SSE fan-out은 durable log가 아니며 DB snapshot이 복구 원천이다.

- Issues encountered:
  - 최초 Validator가 gateway 호출 전후 heartbeat만으로는 60초보다 긴 호출을 보호하지 못한다고 판정해, 호출 전·중·후 DB heartbeat를 수행하고 종료 뒤 예약 pulse가 남지 않는 coordinator로 보정했다.

- Validation:
  - 두 dispatcher claim 경쟁, saturation, restart, stale lease, heartbeat 연장과 budget 동시성 PostgreSQL 테스트가 통과했다.
  - 200ms lease보다 긴 450ms Fake gateway 정지 중에도 주기 heartbeat가 커밋되어 reconciliation이 Run을 중단하지 않는 통합 테스트가 통과했다.

- Next steps:
  - 실제 provider adapter 단계에서는 동일 heartbeat port를 유지하고 provider timeout·shutdown과의 상호작용을 추가 검증한다.
