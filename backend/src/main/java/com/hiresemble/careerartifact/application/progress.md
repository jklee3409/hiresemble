# Progress

## Overview

owner-scoped Career Artifact use case와 workflow·renderer port가 구현됐다.

## [2026-08-08] Session Summary (Career Artifact application lifecycle)

- What was done:
  - readiness, 생성·재생성, immutable version apply, archive/unarchive, download 검증, soft delete와 Agent Run retry/compensation을 연결했다.
- Key decisions:
  - 같은 artifact의 active generation은 row lock과 optimistic version으로 직렬화하고 실패한 재생성은 기존 current version을 보존한다. Object upload는 DB 밖, version apply는 checkpoint completion transaction에서 수행한다.
- Issues encountered:
  - raw render profile을 Run checkpoint와 idempotency payload에서 배제하면서 재시작 가능한 private snapshot 경계가 필요했다. cancel transaction 안의 별도 cleanup transaction은 같은 row lock과 교착하므로 commit 이후 실행하도록 보정했다.
- Validation:
  - two-user, replay/conflict, retry·bulk history delete, rollback·cancel 실패 보상과 storage metadata 검증을 통합 테스트로 확인했다.
- Next steps:
  - account deletion subsystem이 구현될 때 terminal outbox 이후 purge hook을 연결한다.
