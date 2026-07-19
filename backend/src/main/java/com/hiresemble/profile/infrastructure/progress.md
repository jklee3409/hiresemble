# Progress

## Overview

P2 프로필·direct evidence의 owner-scoped JDBC 영속성과 optimistic mutation이 구현되어 있다.

## [2026-07-19] Session Summary (P4 document evidence JDBC 연결)

- What was done:
  - owner-scoped document filter·candidate apply·delete/tombstone와 structured profile document FK 조회를 추가했다.
- Key decisions:
  - source chunk는 같은 user·document·revision이어야 하고 deleted source content는 tombstone에서 제거한다.
- Issues encountered:
  - None.
- Validation:
  - cross-user, invalid chunk, metadata/confidence, unreferenced delete와 referenced tombstone가 통과했다.
- Next steps:
  - P5 provenance store는 실제 schema가 생긴 뒤 contributor로 추가한다.

## [2026-07-19] Session Summary (P2 프로필 JDBC 영속성 구현)

- What was done:
  - 기본 프로필, 다섯 source와 evidence 조회·변경, pagination·sort, version·soft delete SQL을 구현했다.

- Key decisions:
  - resource 존재 여부와 타 사용자 소유 여부를 드러내지 않도록 모든 lookup을 `(user_id, id)`로 제한한다.

- Issues encountered:
  - None

- Validation:
  - PostgreSQL 통합 테스트에서 owner 404, soft-delete 제외, unique·CHECK·trigger와 transaction rollback을 검증했다.

- Next steps:
  - P4 document table 추가 때 owner 복합 FK를 새 migration과 함께 연결한다.
