# Progress

## Overview

V12 조사 schema의 owner-scoped JDBC adapter가 구현되어 있다.

## [2026-09-24] Session Summary (최신 성공 조사 조회)

- What was done:
  - `ResearchStore.latestSucceededForCoverLetter`를 추가했다(owner·cover letter·SUCCEEDED, 완료 시각 최신순).
- Key decisions:
  - None
- Issues encountered:
  - None
- Validation:
  - Backend 전체 `check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/713 tests 중 712 통과. 실패 1건 `S3ObjectStorageAdapterTest`는 Docker Hub의 `minio/minio` 이미지 pull 거부로 인한 초기화 오류이며 이번 변경과 무관해 미검증으로 남긴다.
- Next steps:
  - None

## [2026-07-31] Session Summary (조사 PostgreSQL 저장소)

- What was done:
  - run·source 조회와 topic/source provenance atomic 저장을 추가했다.
- Key decisions:
  - canonical URL uniqueness와 owner 복합 FK를 DB 최종 방어선으로 유지한다.
- Issues encountered:
  - nullable enum parameter에 명시적 PostgreSQL cast가 필요했다.
- Validation:
  - 빈 DB·V11 upgrade·cross-user FK·source dedupe 통합 테스트가 통과했다.
- Next steps:
  - None.
