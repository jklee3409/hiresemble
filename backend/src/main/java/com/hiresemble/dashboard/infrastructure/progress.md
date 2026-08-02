# Progress

## Overview

Dashboard owner 집계·활성 마감과 게시 가이드 JDBC projection이 구현되어 있다.

## [2026-08-02] Session Summary (Dashboard JDBC read store)

- What was done:
  - soft delete·owner·상태 조건을 명시한 count와 deadline query, 게시 시각·순서 guide query를 추가했다.
- Key decisions:
  - 마감 범위 parameter는 UTC `OffsetDateTime`으로 전달하고 결과를 application에서 서울 날짜로 변환한다.
- Issues encountered:
  - mapper method reference 모호성은 명시 lambda로 해결했다.
- Validation:
  - PostgreSQL 기반 `DashboardIntegrationTest` 통과.
- Next steps:
  - None.
