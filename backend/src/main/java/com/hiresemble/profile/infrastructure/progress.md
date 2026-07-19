# Progress

## Overview

P2 프로필·direct evidence의 owner-scoped JDBC 영속성과 optimistic mutation이 구현되어 있다.

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
