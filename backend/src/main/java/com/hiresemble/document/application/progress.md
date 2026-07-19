# Progress

## Overview

P4 Document command/query와 workflow·storage·evidence·outbox port 경계를 구현했다.

## [2026-07-19] Session Summary (Document application 수명주기 연결)

- What was done:
  - upload idempotency, budget reserve, typed Run link, manual same-run resume, reparse 새 Run과 삭제 transaction을 연결했다.
  - Object 준비 뒤 Document·Run·budget·idempotency 완료를 한 transaction에서 commit하고 실패 시 Object를 보상한다.
  - active Document owner resolver를 Agent Run resource filter application port에 제공한다.
- Key decisions:
  - normalized 비공백 100자 미만은 `WAITING_USER`, manual text는 source revision 증가와 비용 재예약을 수행한다.
- Issues encountered:
  - 양수 Fake 예약을 terminal fixture가 직접 종료하던 테스트를 unused reservation release 후 종료하도록 고쳤다.
- Validation:
  - 동시 같은 key, replay/hash mismatch, 완료 trigger rollback, owner resource filter, version 409, retry lineage, compensation, partial success가 통과했다.
- Next steps:
  - P5 이후 provenance 구현은 `EvidenceReferenceQueryPort` contributor로 확장한다.
