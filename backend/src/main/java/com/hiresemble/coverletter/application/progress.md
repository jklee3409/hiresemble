# Progress

## Overview

P7 자기소개서 application use case와 generation·verification port가 구현됐다.

## [2026-09-24] Session Summary (자기소개서 v5 작성 context 조회)

- What was done:
  - `loadWritingInsights`가 run snapshot의 analysisId와 같은 최신 공고 분석일 때만 강점·보완점·요약을 반환하고, 회사 조사 port 결과를 함께 묶는다.
- Key decisions:
  - 분석이 바뀌었으면 강점·보완점을 비워 실행 중 context 불일치를 막는다.
- Issues encountered:
  - None
- Validation:
  - Backend 전체 `check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/713 tests 중 712 통과. 실패 1건 `S3ObjectStorageAdapterTest`는 Docker Hub의 `minio/minio` 이미지 pull 거부로 인한 초기화 오류이며 이번 변경과 무관해 미검증으로 남긴다.
- Next steps:
  - None

## [2026-09-24] Session Summary (근거 원본 masked chunk 조회 연결)

- What was done:
  - `CoverLetterApplicationService`가 읽기 전용 transaction으로 v4 writer용 원본 masked chunk 조회를 Document adapter에 위임한다.
- Key decisions:
  - 원문 chunk는 AI 실행 메모리에만 두고 durable checkpoint에는 개수와 hash만 남긴다.
- Issues encountered:
  - None
- Validation:
  - 자기소개서 workflow·prompt·policy 집중 테스트 32건 통과(신규 v4 writer 원문 발췌·분량 목표/하한·claim 없는 사실 표현 경고·단일 문항 미지원 수치 ERROR·v3 기존 거부 유지·v4 prompt 계약·policy 경계 포함).
  - Backend 전체 `./gradlew check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/703 tests 중 702 통과. 유일한 실패 `S3ObjectStorageAdapterTest`는 이 환경에서 Docker Hub가 `minio/minio:RELEASE.2025-09-07T16-13-09Z` pull을 거부한 초기화 오류로, 이번 변경과 무관하며 미검증으로 남긴다.
- Next steps:
  - None

## [2026-08-06] Session Summary (v4 Run 접수 계약)

- What was done: 생성·검증 접수에서 exact model을 검증하고 model·memo를 immutable snapshot/hash/retry input에 반영했다.
- Key decisions: v4에는 `requestedQuality`를 저장하지 않고 legacy overload는 v1~v3 재개에만 사용한다.
- Issues encountered: None.
- Validation: generation service·workflow·전체 Backend 테스트 통과.
- Next steps: model별 실제 비용·품질 지표를 운영 usage 집계와 연결한다.

## [2026-08-05] Session Summary (Cover Letter v3 launch와 USER_EDITED provenance)

- What was done:
  - active launch version을 v3로 변경하고 verification persistence의 v2/v3 modern snapshot 분기를 보존했다.
  - USER_EDITED 저장 시 parent evidence link의 claim_text가 새 plain text에 실제 남은 경우만 candidate provenance로 복사한다.
- Key decisions:
  - 편집으로 사라진 claim은 자동 계승하지 않으며 verification이 current VERIFIED freshness를 다시 판정한다.
- Issues encountered:
  - None.
- Validation:
  - `CoverLetterApplicationIntegrationTest` exact excerpt 유지/제거 경계와 전체 Backend check 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (Cover Letter v2 launch와 sibling snapshot)

- What was done:
  - 신규 generation·verification launch version을 v2로 전환하고 verification snapshot에 다른 문항의 current answer summary를 owner scope로 구성했다.
- Key decisions:
  - v2 sibling 본문 hash를 snapshot freshness에 포함하되 durable v1 loader/hash는 기존 계약을 유지하고 API·DB·immutable version·CAS 계약은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - application/integration 회귀와 Backend 전체 check 통과.
- Next steps:
  - 사용자 보완 action/resume은 Frontend와 함께 별도 계약으로 진행한다.

## [2026-07-31] Session Summary (P8 owner-aware resource resolver 연결)

- What was done:
  - Cover Letter·answer version Agent Run resource owner lookup을 강화된 user-aware resolver 계약에 맞췄다.
- Key decisions:
  - P7 공개 동작과 자기소개서 lifecycle은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Backend 전체 check와 final-source P7 actual Chromium 1/1·DB assertions가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 수명주기·AI application 경계)

- What was done:
  - owner-scoped CRUD, 문항 정렬, immutable save/restore, generation·verify Run 접수, partial apply, verification persist와 finalize/archive/unarchive transaction을 구현했다.
- Key decisions:
  - AI apply 직전에 cover letter/question/current version CAS를 재검증하고 성공 문항만 독립 transaction으로 저장한다.
- Issues encountered:
  - 문서 삭제 시 historical provenance 보호를 위해 기존 `EvidenceReferenceQueryPort`에 자기소개서 참조를 기여했다.
- Validation:
  - application·finalization·Agent Run 통합 테스트와 actual P7 DB assertions가 통과했다.
- Next steps:
  - 독립 validator 판정을 반영한다.
