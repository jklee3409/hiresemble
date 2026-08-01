# Progress

## Overview

Agent Run JDBC 저장·조회와 Document·Job·Cover Letter·Interview Question Set·Answer Version typed resource owner resolution을 관리한다.

## [2026-08-01] Session Summary (다중 가격 usage ledger)

- What was done:
  - `ai_usage_records.provider_call_id`를 저장하고 동일 call·price item의 중복 기록을 DB identity와 맞췄다.
- Key decisions:
  - 입력·cached 입력·출력은 각각 exact price item을 참조하며 actual cost 합계에는 한 번씩만 반영한다.
- Issues encountered:
  - None.
- Validation:
  - V13 migration test와 Agent Run ledger 통합 테스트가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 typed link·owner visibility)

- What was done:
  - question set·interview answer version resource column, kind/column parity와 user-aware owner lookup을 JDBC projection에 추가했다.
- Key decisions:
  - `deleted_at`은 공개 Run 조회만 숨기고 P8 typed link·step·usage·lineage 내부 audit은 보존한다.
- Issues encountered:
  - None.
- Validation:
  - terminal history delete 뒤 Run API 404와 P8 domain/audit 보존 DB assertion이 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (owner-visible terminal history soft delete)

- What was done:
  - 선택 row를 `FOR UPDATE`로 잠근 뒤 전부 owner-visible·terminal인지 확인하고 `deleted_at`을 원자 설정했다.
  - 목록·상세에서 deleted row를 제외하고 숨겨진 retry successor가 unique lineage를 점유한 경우 중복 생성을 거부했다.
- Key decisions:
  - 내부 audit 조회와 step 재사용은 보존하고 공개 owner 조회만 soft-delete filter를 적용한다.
- Issues encountered:
  - None.
- Validation:
  - 삭제 뒤 404·목록 제외·budget reservation 보존과 전체 Backend check 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 resource owner·retry seed persistence)

- What was done:
  - Cover Letter/Answer Version owner resolution, secondary result refs와 predecessor partial result seed 조회를 repository에 추가했다.
- Key decisions:
  - owner 조건은 typed aggregate join으로 강제하고 결과 본문 대신 ID·상태·scope만 metadata에 저장한다.
- Issues encountered:
  - 없음.
- Validation:
  - 사용자 A/B resource 404, partial retry 중복 version 부재와 Agent Run 통합 테스트가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (Job typed resource owner resolution 연결)

- What was done:
  - Job resource filter·상세·SSE가 owner-scoped active Job만 해석하도록 repository query를 확장했다.
- Key decisions:
  - 다른 사용자와 soft-deleted Job의 Run은 404로 숨긴다.
- Issues encountered:
  - 없음.
- Validation:
  - Job owner 격리 API·Browser E2E와 Agent Run 회귀 테스트가 통과했다.
- Next steps:
  - P6 analysis resource는 실제 aggregate가 생길 때 별도 typed link로 추가한다.

## [2026-07-23] Session Summary (책임별 persistence package 분리)

- What was done:
  - 기존 Java 파일 7개를 persistence 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

- Key decisions:
  - 실제 구현 파일이 있는 package만 생성하고 미래 기능이나 빈 책임 디렉터리는 만들지 않았다.
  - API·DB·workflow·Spring Bean 동작과 접근 제한자는 유지했다.

- Issues encountered:
  - 구조 세분화 과정에서 추가 기능 변경이나 계약 충돌은 발견되지 않았다.

- Validation:
  - 운영·테스트 Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import와 package-private 교차 참조 정적 검사를 통과했다.
  - HEAD 대비 package·import·FQCN을 제외한 본문 비교 237건이 모두 일치했고 `git diff --check HEAD`가 통과했다.
  - Docker를 찾을 수 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 수행하지 않았으며 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.
