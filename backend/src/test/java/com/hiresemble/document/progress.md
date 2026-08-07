# Progress

## Overview

P4 Document HTTP·workflow port·storage·parser·embedding·outbox 통합 테스트를 구현했다.

## [2026-08-07] Session Summary (semantic 중복 경험·문서 독립성 회귀)

- What was done:
  - 서로 다른 문서의 유사 표현이 하나의 canonical 경험과 두 출처로 연결되고 두 번째 문서 응답이 `CORROBORATING`으로 표시되며 승인 경험이 문서 삭제 뒤 유지되는 시나리오를 추가했다.
- Key decisions:
  - 문서별 evidence 조회는 source row, 전역 조회·분석은 canonical row를 검증한다.
- Issues encountered:
  - 전체 check에서 기존 upload compensation 타이밍 테스트 1건이 404 대신 202로 실패했다.
- Validation:
  - 신규 semantic duplicate·삭제·재분석 집중 시나리오는 각각 통과했고 Document 전체 suite는 `NOT_VERIFIED`다.
- Next steps:
  - upload compensation fixture의 fail-once 순서를 별도 안정화한다.

## [2026-08-02] Session Summary (자료 재분석 이전 경험 제거 회귀)

- What was done:
  - verified 문서 evidence가 재분석 전 Job Analysis snapshot에 포함되고, reparse 수락 직후 row와 snapshot에서 제거되는 통합 회귀를 추가했다.
- Key decisions:
  - 새 AI 결과 완료가 아니라 reparse API 수락 시점을 무효화 기준으로 검증한다.
- Issues encountered:
  - None.
- Validation:
  - `DocumentIntegrationTest`가 단일-use Gradle 실행에서 통과했다.
- Next steps:
  - None.

## [2026-08-01] Session Summary (소재 batch 재검토·문서 삭제 독립성 회귀)

- What was done:
  - PENDING 재검토와 batch 상태 변경, 문서 삭제 후 직접 대외활동·ACTIVITY evidence 유지 회귀를 추가했다.
- Key decisions:
  - 문서와 직접 활동의 삭제 생명주기 분리를 테스트 계약으로 고정한다.
- Issues encountered:
  - None.
- Validation:
  - DocumentIntegrationTest와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (candidate rejection reason 집계 회귀)

- What was done:
  - 교육 category와 근거 없는 수치 후보가 각 stable reason count로 집계되고 유효 evidence만 저장됨을 검증했다.
- Key decisions:
  - raw candidate 값은 assertion용 DB 진단에 저장하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `DocumentIntegrationTest`와 전체 check 통과.
- Next steps:
  - 새 rejection validator는 안전 reason test를 함께 추가한다.

## [2026-07-31] Session Summary (문서 교육 근거 후보 차단)

- What was done:
  - 정상·grounding 실패 후보와 함께 `EDUCATION_HISTORY` 후보가 개별 rejected되고 DB row를 만들지 않는 회귀를 추가했다.
  - 생성된 DOCUMENT_CHUNK 근거가 공개 검토 API로 `PENDING→VERIFIED` 전이되는 경계를 검증했다.
- Key decisions:
  - 유효한 비학력 후보는 같은 batch에서 계속 적용되는 partial rejection을 검증한다.
- Issues encountered:
  - None.
- Validation:
  - `DocumentIntegrationTest` 12 tests와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/test/java/com/hiresemble/document 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

- Key decisions:
  - 파일 경로, package·import와 필요한 FQCN만 변경하고 API·DB·workflow·접근 제한자는 유지했다.
  - 실제 파일이 있는 책임 package만 생성하고 P5 이후 기능과 빈 디렉터리는 만들지 않았다.

- Issues encountered:
  - package-private 결합은 접근 제한자를 넓히지 않고 같은 package 이동 또는 명시적 이동 제외로 처리했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P4 Document 테스트 구현)

- What was done:
  - upload replay·동시 key·owner·429·413·415, atomic idempotency 완료 rollback, Agent Run Document filter, manual/reparse, resource retry, evidence tombstone와 delete를 검증했다.
- Key decisions:
  - 양수 Fake reservation은 immutable test price version으로 고정하고 외부 비용 호출을 사용하지 않는다.
- Issues encountered:
  - 직접 terminal 상태를 만들던 fixture에서 reservation release가 필요해 실제 application 경계와 맞췄다.
  - 최초 Validator가 실제 Document resource filter 성공·격리·삭제 회귀 테스트 공백을 발견해 같은 통합 테스트에 추가했다.
- Validation:
  - Backend 전체 287 tests와 P4 실제 Browser E2E 4/4가 통과했다.
- Next steps:
  - P5 이후 provenance table 없이 Fake reference contributor로 tombstone branch를 유지한다.
