# Progress

## Overview

P7 V8 schema를 사용하는 owner-scoped JDBC store와 generation·verification 비용 설정이 구현됐다.

## [2026-08-05] Session Summary (USER_EDITED exact excerpt provenance SQL)

- What was done:
  - parent answer evidence link 중 nonblank claim_text가 새 편집 본문에 실제 존재하는 link만 새 answer version으로 복사하는 owner-scoped SQL을 추가했다.
- Key decisions:
  - 새 migration 없이 기존 immutable link table에 historical candidate provenance를 생성한다.
- Issues encountered:
  - None.
- Validation:
  - PostgreSQL 통합 테스트와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 PostgreSQL store)

- What was done:
  - active cardinality, 질문 order, current answer, provenance, immutable verification, acknowledgement와 lifecycle 조건부 SQL을 구현했다.
- Key decisions:
  - owner·active·version 조건을 모든 aggregate/child 조회와 mutation에 포함하고 DB 제약과 application CAS를 함께 사용한다.
- Issues encountered:
  - 없음.
- Validation:
  - repository·migration negative/upgrade, application 통합과 actual P7 DB assertions가 통과했다.
- Next steps:
  - None.
