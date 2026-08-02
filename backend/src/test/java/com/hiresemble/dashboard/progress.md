# Progress

## Overview

Dashboard owner 집계·서울 마감과 게시 Career Guide 통합 회귀를 소유한다.

## [2026-08-02] Session Summary (Dashboard 통합 회귀)

- What was done:
  - owner 격리, 서울 월 경계, `CLOSED` 제외, 정확 count·프로필과 guide 게시 순서를 검증했다.
- Key decisions:
  - 경계 fixture는 UTC Instant로 저장하고 서울 LocalDate 응답을 assertion한다.
- Issues encountered:
  - 초기 fixture의 Instant JDBC binding을 `OffsetDateTime`으로 보정했다.
- Validation:
  - `DashboardIntegrationTest` 포함 대상 Backend 10 tests 통과.
- Next steps:
  - None.
