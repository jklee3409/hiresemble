# Progress

## Overview

P4 Document ingestion 고정 workflow의 contract와 PostgreSQL integration 시나리오를 검증한다.

## [2026-08-01] Session Summary (strict evidence metadata 회귀)

- What was done:
  - scalar metadata 보존, null/string warning, 중복·잘못된 type·예약 key 거절과 domain apply 전 실패를 검증했다.
- Key decisions:
  - invalid Chat output에서도 앞선 chunk·embedding 보존 계약을 유지한다.
- Issues encountered:
  - 없음.
- Validation:
  - workflow contract와 PostgreSQL orchestrator integration focused test 통과.
- Next steps:
  - bounded live document ingestion 결과를 별도 기록한다.

## [2026-07-19] Session Summary (Document ingestion workflow 검증)

- What was done:
  - 전체 성공, 짧은 text same-run resume, invalid embedding partial failure를 Fake gateway로 검증했다.
- Key decisions:
  - zero-cost Fake usage는 가격 pair 없이 기록하고 실제 브라우저 E2E에서는 immutable Fake catalog item을 사용한다.
- Issues encountered:
  - None.
- Validation:
  - P4 workflow 6 tests와 P3 orchestrator 회귀가 전체 Backend check에서 통과했다.
- Next steps:
  - 실제 provider 가격·network는 후속 승인 없이는 추가하지 않는다.
