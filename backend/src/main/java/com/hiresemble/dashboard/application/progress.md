# Progress

## Overview

지원 준비 summary와 월별 마감·게시 가이드 query use case가 구현되어 있다.

## [2026-08-02] Session Summary (서울 기준 Dashboard 조합)

- What was done:
  - `Clock`, `YearMonth`, Profile 완료도 정책으로 typed Dashboard view를 조합했다.
- Key decisions:
  - 서울 자정의 inclusive/exclusive Instant 경계를 사용하고 DB 정렬을 보존해 날짜별 목록을 만든다.
- Issues encountered:
  - None.
- Validation:
  - `DashboardIntegrationTest` owner·월 경계 시나리오 통과.
- Next steps:
  - None.
