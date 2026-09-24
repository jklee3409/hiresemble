# Progress

## Overview

P7 API·generation·verification에 필요한 최소 immutable application record가 구현됐다.

## [2026-09-24] Session Summary (작성 context model)

- What was done:
  - `WritingInsights`, `CompanyResearch`, `CompanyResearchSource` record를 추가했다. 이 값은 ephemeral AI context이며 provenance가 아니다.
- Key decisions:
  - None
- Issues encountered:
  - None
- Validation:
  - Backend 전체 `check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/713 tests 중 712 통과. 실패 1건 `S3ObjectStorageAdapterTest`는 Docker Hub의 `minio/minio` 이미지 pull 거부로 인한 초기화 오류이며 이번 변경과 무관해 미검증으로 남긴다.
- Next steps:
  - None

## [2026-09-24] Session Summary (근거 원본 발췌 model)

- What was done:
  - `EvidenceSourceExcerpt`(evidence·chunk·document ID, chunk index, masked content) record를 추가했다.
- Key decisions:
  - 원문(content)이 아닌 masked content만 담는다.
- Issues encountered:
  - None
- Validation:
  - 자기소개서 workflow·prompt·policy 집중 테스트 32건 통과(신규 v4 writer 원문 발췌·분량 목표/하한·claim 없는 사실 표현 경고·단일 문항 미지원 수치 ERROR·v3 기존 거부 유지·v4 prompt 계약·policy 경계 포함).
  - Backend 전체 `./gradlew check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/703 tests 중 702 통과. 유일한 실패 `S3ObjectStorageAdapterTest`는 이 환경에서 Docker Hub가 `minio/minio:RELEASE.2025-09-07T16-13-09Z` pull을 거부한 초기화 오류로, 이번 변경과 무관하며 미검증으로 남긴다.
- Next steps:
  - None

## [2026-08-06] Session Summary (선택 모델과 memo application model)

- What was done: generation question에 memo, generation·verification snapshot에 model을 추가하고 durable legacy constructor를 보존했다.
- Key decisions: model은 nullable legacy와 non-null v4 계약을 context builder에서 version별 검증한다.
- Issues encountered: None.
- Validation: compile·workflow·Backend 전체 테스트 통과.
- Next steps: 공개 DTO에는 필요한 catalog metadata 외 내부 snapshot을 노출하지 않는다.

## [2026-08-05] Session Summary (Cover Letter v3 model compatibility 확인)

- What was done:
  - 공개 application model은 변경하지 않고 v3 strict DTO와 truncation/selection metadata를 AI workflow 내부 record로 격리했다.
- Key decisions:
  - DB/API용 `VerifiedClaim`, `EvidenceUse`, `VerificationSnapshot` 의미는 유지하고 unsupported claim은 workflow persistence 전에 제거한다.
- Issues encountered:
  - None.
- Validation:
  - application·workflow·strict schema 테스트와 Backend check 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (Verification sibling answer snapshot)

- What was done:
  - `SiblingAnswerSummary`와 verification snapshot의 sibling current answer 목록을 추가했다.
- Key decisions:
  - owner-scoped current answer만 포함하고 Provider 전달 시 workflow가 질문·본문 길이를 다시 제한한다.
- Issues encountered:
  - None.
- Validation:
  - application/integration 및 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 immutable application model)

- What was done:
  - 자기소개서 detail·version·verification projection과 generation/verification snapshot·apply command를 추가했다.
- Key decisions:
  - historical evidence 작성 당시 정보와 현재 상태를 분리하고 AI 내부·storage 정보를 모델 경계에서 제외한다.
- Issues encountered:
  - 없음.
- Validation:
  - API schema·workflow structured output·Backend 전체 check가 통과했다.
- Next steps:
  - None.
