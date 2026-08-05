# Progress

## Overview

Flyway 단계별 보존과 최신 V22 schema의 빈 DB·upgrade 경로를 실제 PostgreSQL에서 검증한다.

## [2026-08-05] Session Summary (V21~V22 공고 기간 upgrade 검증)

- What was done:
  - populated V20에서 V21~V22로 올리며 기존 공고의 서울 반기 backfill, trigger·index·CHECK를 검증했다.
- Key decisions:
  - UTC 2026-06-30 14:59:59/15:00:00으로 서울 6월/7월 경계를 고정했다.
- Issues encountered:
  - trigger가 동작하는 `created_at` 변경 fixture에서 기존 시간 CHECK에 맞춰 `updated_at`도 함께 조정했다.
- Validation:
  - 집중 migration test와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (V19 fresh·V18 upgrade 검증)

- What was done:
  - 빈 DB V1→V19 적용과 기존 populated V18 schema의 V19 upgrade fixture를 추가했다.
- Key decisions:
  - 이전 migration fingerprint·내용은 그대로 두고 V19 생성물과 기본값만 검증한다.
- Issues encountered:
  - None.
- Validation:
  - `DashboardMigrationTest.emptyDatabaseAndPopulatedV18UpgradeCreateEligibilityAndFactProvenance`가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (V18 장문 Guide 안전 update 검증)

- What was done:
  - fresh V18의 5개 장문 version 2와 V17에서 편집된 guide를 V18이 덮어쓰지 않는 경로를 추가했다.
- Key decisions:
  - 본문 최소 길이와 custom body 보존을 함께 assertion해 콘텐츠 보강과 update guard를 검증한다.
- Issues encountered:
  - None.
- Validation:
  - `gradlew test --tests com.hiresemble.migration.DashboardMigrationTest`: 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (V17 Career Guide migration 검증)

- What was done:
  - fresh V17과 populated V16→V17에서 기존 데이터 보존, seed 5개·정렬·version·게시 제약을 확인했다.
- Key decisions:
  - 적용 이력이 있는 V1~V16 hash와 파일은 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - `DashboardMigrationTest`와 Backend 전체 `check` 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (V16 공고 자동 분석 migration 검증)

- What was done:
  - 기존 migration suite와 Job 자동 분석 PostgreSQL 통합 테스트가 빈 DB에 V16을 적용하고 owner·revision unique·상태 제약을 사용하는지 확인했다.
- Key decisions:
  - V1~V15 파일은 수정하지 않고 V16 적용 결과만 검증한다.
- Issues encountered:
  - None.
- Validation:
  - `JobAutoAnalysisIntegrationTest`와 관련 Job suites에서 V16 Flyway 적용 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (V15 대외활동 migration 검증)

- What was done:
  - 빈 DB와 V14 기존 profile evidence가 있는 DB의 V15 upgrade 검증을 추가했다.
- Key decisions:
  - 새 제약과 trigger가 기존 행을 보존하면서 ACTIVITY만 확장하는지 확인한다.
- Issues encountered:
  - None.
- Validation:
  - UserActivityMigrationTest와 전체 migration suite 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (V14 embedding 정책 migration 검증)

- What was done:
  - latest active version 2 `openai`, legacy version 1 보존과 V13→V14 upgrade를 검증했다.
- Key decisions:
  - V1~V13 SHA-256을 불변 기준으로 확장했다.
- Issues encountered:
  - None.
- Validation:
  - migration focused test와 Backend 전체 check가 통과했다.
- Next steps:
  - P8.6 시작 시 latest migration을 재확인한다.

## [2026-08-01] Session Summary (V13 가격·usage migration 검증)

- What was done:
  - fresh V1→V13과 populated V12→V13, catalog completeness와 usage identity 제약을 검증했다.
- Key decisions:
  - V1~V12 checksum 불변을 자동 검증한다.
- Issues encountered:
  - 1차 read-only self-audit에서 populated V12→V13과 V1~V12 checksum 자동 검증 누락을 발견해 제한 보정했다.
- Validation:
  - `P8_5MigrationTest`, `P8_5UpgradeMigrationTest`와 전체 Backend check가 통과했다.
- Next steps:
  - 새 가격은 새 migration/version으로만 추가한다.

## [2026-07-31] Session Summary (V11 최종 학력 migration 검증)

- What was done:
  - populated V10의 고등학교·학사·석사·박사를 backfill하고 박사가 최종 학력으로 재지정되는지 검증했다.
  - 빈 DB V11의 non-null column과 level CHECK를 검증했다.
- Key decisions:
  - application 통합 테스트와 별도 container upgrade test로 기존 DB 보정 경계를 분리했다.
- Issues encountered:
  - None.
- Validation:
  - `FinalEducationMigrationTest` 2개와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (P5 V6 migration·불변식 검증)

- What was done:
  - 빈 DB V1→V6, V5-only upgrade, owner FK·canonical active unique·상태 CHECK·history FK와 P6 table 부재를 검증했다.
- Key decisions:
  - V1~V5 Git blob·SHA-256을 기준선으로 고정한다.
- Issues encountered:
  - 없음.
- Validation:
  - `P5MigrationTest` 6개와 전체 Backend check가 통과했다.
- Next steps:
  - P6 migration은 새 version으로 검증한다.

## [2026-07-19] Session Summary (P4 V5 migration·불변식 검증)

- What was done:
  - 빈 DB와 V1/V2/V3/V4-only upgrade, V5 table·CHECK·FK·trigger·policy metadata를 검증했다.
- Key decisions:
  - V1–V4 hash와 단일 V5, HNSW/P5 table 부재를 명시적으로 고정한다.
- Issues encountered:
  - None.
- Validation:
  - `P4MigrationTest`와 전체 Backend check가 PostgreSQL 18+pgvector에서 통과했다.
- Next steps:
  - 이후 migration 테스트도 기존 hash 불변을 누적 검증한다.

## [2026-07-19] Session Summary (P3 V4 migration·불변식 검증)

- What was done:
  - 빈 DB V1→V4와 V1/V2/V3-only upgrade, 11개 table과 P4 table 부재를 검증했다.
  - V1~V3 Git blob 기준 SHA-256과 owner·retry·step·ledger·preference unique를 고정했다.
  - V4에 선언된 71개 CHECK constraint가 실제 PostgreSQL schema에 모두 설치되는지 대조했다.

- Key decisions:
  - 실제 PostgreSQL과 test fixture price version을 사용한다.

- Issues encountered:
  - None.

- Validation:
  - P3 migration 8 tests가 전체 check에서 통과했다.

- Next steps:
  - V5 이후에도 V1~V4를 수정하지 않는다.

## [2026-07-19] Session Summary (P2 V3 migration·불변식 검증)

- What was done:
  - 빈 DB V1→V2→V3, V1-only·V2-only upgrade와 P2 table·constraint·trigger 검증을 추가했다.
  - V1·V2 Git blob 기준 hash 불변과 source/evidence rollback을 고정했다.

- Key decisions:
  - JSON 배열·대표 학력·날짜·GPA·metadata·owner 불변식은 H2가 아닌 PostgreSQL에서 검증한다.

- Issues encountered:
  - 초기 assertion 두 개가 PostgreSQL의 먼저 발생한 constraint message와 달라 테스트 기대값만 실제 제약 순서에 맞췄다.

- Validation:
  - Backend 전체 check에서 P1·P2 migration test가 모두 통과했다.

- Next steps:
  - 적용 이력 V1~V3는 수정하지 않고 후속 변경은 V4 이후로 추가한다.

## [2026-07-19] Session Summary (P1 Flyway migration 검증 구현)

- What was done:
  - 빈 DB 전체 적용, V1 target 적용 뒤 upgrade와 table·constraint·index 범위를 검증했다.

- Key decisions:
  - pgvector PostgreSQL 18 image를 사용하고 account_deletion_tasks·P2 table 부재도 확인한다.

- Issues encountered:
  - None

- Validation:
  - P1MigrationTest 3개와 V1 SHA-256 고정 assertion이 통과했다.

- Next steps:
  - 새 migration마다 동일한 빈 DB·직전 version upgrade 검증을 추가한다.
