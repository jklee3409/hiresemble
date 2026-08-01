# Progress

## Overview

P4 Document ingestion 고정 workflow의 contract와 PostgreSQL integration 시나리오를 검증한다.

## [2026-08-01] Session Summary (일부·전체 candidate rejection 회귀)

- What was done:
  - 6→4/2, 4→4/0, 3→0/3 결과의 step·document·Run 상태, evidence 수, partial refs와 reason count를 검증했다.
- Key decisions:
  - rejected candidate는 failed/succeeded scope key 또는 result ref가 아니다.
- Issues encountered:
  - 모델 유사 fixture가 candidate cap을 충족하도록 synthetic chunk 입력을 충분히 크게 구성했다.
- Validation:
  - Document focused integration과 전체 Backend check 통과.
- Next steps:
  - Provider·structured/ref mapping·domain transaction 실패 회귀를 유지한다.

## [2026-08-01] Session Summary (local ref·retry·paid usage 통합 검증)

- What was done:
  - empty/one/multiple ref·null warning·unknown/duplicate/blank ref·candidate cap과 trusted UUID mapping을 검증했다.
  - malformed 1회 실패, unknown ref 2회 성공/실패에서 attempt별 paid usage·actual cost·reservation release·evidence 0·deterministic 산출물 보존을 검증했다.
- Key decisions:
  - synthetic non-PII 수기 JSON과 Fake gateway만 사용한다.
- Issues encountered:
  - test catalog 오염을 기존 V13 output item 참조로 제거했다.
- Validation:
  - focused integration와 전체 459 tests 통과.
- Next steps:
  - 실제 Chat과 document vertical은 사용자 bounded handoff로 남긴다.

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
