# Progress

## Overview

Dashboard 월별 projection과 게시 Career Guide read API를 제공한다.

## [2026-08-02] Session Summary (Dashboard·Career Guide HTTP 계약)

- What was done:
  - 인증·월 형식 validation과 두 직접 응답 DTO를 OpenAPI에 추가했다.
- Key decisions:
  - `month`는 필수 `YYYY-MM`, 가이드 사용자 응답은 `PUBLISHED`만 허용한다.
- Issues encountered:
  - None.
- Validation:
  - `OpenApiContractTest`, `DashboardIntegrationTest` 통과.
- Next steps:
  - None.
