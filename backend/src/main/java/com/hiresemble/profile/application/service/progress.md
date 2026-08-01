# Progress

## Overview

com.hiresemble.profile.application.service package의 책임과 검증 상태를 추적한다. 이 package는 기존 Java 파일의 책임별 이동으로 생성됐으며 동작 계약은 변경하지 않았다.

## [2026-08-01] Session Summary (stable candidate rejection 집계)

- What was done:
  - provenance, confidence, category, education, content, metadata, ungrounded number, duplicate와 안전한 fallback reason을 count로 집계했다.
- Key decisions:
  - candidate 단위 정상 rejection만 집계하고 DB/transaction 실패는 그대로 workflow failure로 전파한다.
- Issues encountered:
  - 과거 rejection은 reason 없이 count만 저장돼 소급 분류하지 않는다.
- Validation:
  - synthetic non-PII 후보의 reason별 count와 evidence persistence 회귀 통과.
- Next steps:
  - raw candidate나 validation exception message를 로그·checkpoint에 추가하지 않는다.

## [2026-07-31] Session Summary (최종 학력 transaction 재계산)

- What was done:
  - 학력 생성·수정·삭제를 owner profile lock으로 직렬화하고 active 목록에서 최종 학력을 재계산했다.
- Key decisions:
  - 단계, 상태, 졸업일, 입학일, 등록 시각, ID 순서의 deterministic comparator를 사용한다.
- Issues encountered:
  - None.
- Validation:
  - 단계 상승·삭제 후 승계 Profile 통합 테스트와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (학력 동기화·문서 추출 차단)

- What was done:
  - 학력 생성·수정·대표 demotion·삭제에서 direct evidence 작업을 제거하고 교육 category 문서 후보를 validation 단계에서 거부했다.
  - 승인·거절은 DOCUMENT_CHUNK만 허용하고 직접 입력 source 요청은 state conflict로 거부했다.
- Key decisions:
  - 후보 batch 전체를 실패시키지 않고 교육 후보만 rejected count에 포함해 나머지 근거를 적용한다.
- Issues encountered:
  - None.
- Validation:
  - Profile·Document 통합 테스트와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (Document evidence reference contributor 확장)

- What was done:
  - `DocumentEvidenceService`가 여러 `EvidenceReferenceQueryPort` 구현을 조회해 downstream provenance를 보존하도록 확장했다.
- Key decisions:
  - 하나라도 참조하면 hard delete 대신 동일 ID `SOURCE_DELETED` 전환을 적용한다.
- Issues encountered:
  - 없음.
- Validation:
  - Job Analysis·Cover Letter 참조 회귀와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-23] Session Summary (책임별 service package 분리)

- What was done:
  - 기존 Java 파일 3개를 service 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
