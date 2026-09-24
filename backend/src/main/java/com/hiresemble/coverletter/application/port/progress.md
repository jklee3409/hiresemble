# Progress

## Overview

P7 generation·verification query/command와 owner-scoped evidence 검색 port가 구현됐다.

## [2026-09-24] Session Summary (회사 조사·작성 context port)

- What was done:
  - `CoverLetterCompanyResearchPort`와 `CoverLetterQueryPort.loadWritingInsights`를 추가했다.
- Key decisions:
  - None
- Issues encountered:
  - None
- Validation:
  - Backend 전체 `check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/713 tests 중 712 통과. 실패 1건 `S3ObjectStorageAdapterTest`는 Docker Hub의 `minio/minio` 이미지 pull 거부로 인한 초기화 오류이며 이번 변경과 무관해 미검증으로 남긴다.
- Next steps:
  - None

## [2026-09-24] Session Summary (근거 원본 masked chunk 조회 port)

- What was done:
  - `CoverLetterEvidenceSearchPort.findMaskedSourceExcerpts`와 `CoverLetterQueryPort.findEvidenceSourceExcerpts`를 추가했다.
- Key decisions:
  - query port 기본 구현은 기존 exact-model 메서드처럼 미구성 시 `UnsupportedOperationException`을 던진다.
- Issues encountered:
  - None
- Validation:
  - 자기소개서 workflow·prompt·policy 집중 테스트 32건 통과(신규 v4 writer 원문 발췌·분량 목표/하한·claim 없는 사실 표현 경고·단일 문항 미지원 수치 ERROR·v3 기존 거부 유지·v4 prompt 계약·policy 경계 포함).
  - Backend 전체 `./gradlew check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/703 tests 중 702 통과. 유일한 실패 `S3ObjectStorageAdapterTest`는 이 환경에서 Docker Hub가 `minio/minio:RELEASE.2025-09-07T16-13-09Z` pull을 거부한 초기화 오류로, 이번 변경과 무관하며 미검증으로 남긴다.
- Next steps:
  - None

## [2026-08-06] Session Summary (model 기반 query port)

- What was done: generation·verification query가 exact model을 받는 v4 경계를 추가하고 legacy quality 경계를 재개 호환용으로 유지했다.
- Key decisions: adapter가 모델을 재해석하지 않고 application service에서 검증한 ID를 전달한다.
- Issues encountered: None.
- Validation: compile·workflow·Backend 전체 테스트 통과.
- Next steps: legacy overload는 v1~v3 Run 제거 정책이 생길 때 함께 정리한다.

## [2026-07-30] Session Summary (P7 AI workflow port)

- What was done:
  - generation/verification snapshot 조회, 문항별 answer apply, verification persist·compensation과 evidence candidate 검색 경계를 추가했다.
- Key decisions:
  - 반환값은 최소 immutable data로 제한하고 domain apply는 application command 안에서만 수행한다.
- Issues encountered:
  - 없음.
- Validation:
  - workflow contract·restart·partial success와 Backend 전체 check가 통과했다.
- Next steps:
  - None.
