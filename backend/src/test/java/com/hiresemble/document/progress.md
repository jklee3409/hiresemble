# Progress

## Overview

P4 Document HTTP·workflow port·storage·parser·embedding·outbox 통합 테스트를 구현했다.

## [2026-07-19] Session Summary (P4 Document 테스트 구현)

- What was done:
  - upload replay·동시 key·owner·429·413·415, atomic idempotency 완료 rollback, Agent Run Document filter, manual/reparse, resource retry, evidence tombstone와 delete를 검증했다.
- Key decisions:
  - 양수 Fake reservation은 immutable test price version으로 고정하고 외부 비용 호출을 사용하지 않는다.
- Issues encountered:
  - 직접 terminal 상태를 만들던 fixture에서 reservation release가 필요해 실제 application 경계와 맞췄다.
  - 최초 Validator가 실제 Document resource filter 성공·격리·삭제 회귀 테스트 공백을 발견해 같은 통합 테스트에 추가했다.
- Validation:
  - Backend 전체 287 tests와 P4 실제 Browser E2E 4/4가 통과했다.
- Next steps:
  - P5 이후 provenance table 없이 Fake reference contributor로 tombstone branch를 유지한다.
