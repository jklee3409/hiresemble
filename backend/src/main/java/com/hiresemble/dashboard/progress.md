# Progress

## Overview

Dashboard owner 집계·서울 월별 마감과 게시 Career Guide read API가 구현되어 있다.

## [2026-08-02] Session Summary (지원 Dashboard read projection)

- What was done:
  - 프로필·문서·공고·Agent Run 정확 집계, 서울 월별 활성 마감과 게시 가이드 조회를 추가했다.
- Key decisions:
  - 기존 aggregate mutation 없이 전용 JDBC read projection을 사용하고 가이드는 게시 시각과 노출 순서로 필터·정렬한다.
- Issues encountered:
  - 초기 JDBC mapper method reference 모호성을 명시 lambda로 보정했다.
- Validation:
  - `DashboardIntegrationTest`, `DashboardMigrationTest`, `OpenApiContractTest` 대상 10개 테스트 통과.
- Next steps:
  - 관리자 게시물 mutation은 별도 승인 범위다.
