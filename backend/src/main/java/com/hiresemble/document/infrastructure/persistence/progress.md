# Progress

## Overview

com.hiresemble.document.infrastructure.persistence package의 책임과 검증 상태를 추적한다. 이 package는 기존 Java 파일의 책임별 이동으로 생성됐으며 동작 계약은 변경하지 않았다.

## [2026-09-24] Session Summary (VERIFIED 근거 원본 chunk 조회 SQL)

- What was done:
  - `DocumentStore.evidenceSourceChunks`를 추가했다. 직접 `DOCUMENT_CHUNK` 근거는 자기 chunk를, 정규 `EXPERIENCE` 근거는 삭제되지 않은 experience item의 `experience_evidence_links`로 연결된 활성 문서 근거 chunk를 조회한다.
- Key decisions:
  - owner 조건, `VERIFIED`·`source_deleted_at IS NULL`, 삭제되지 않은 문서, `REJECTED|SOURCE_DELETED` raw 근거 제외를 모두 SQL에서 강제하고 masked content만 반환한다.
- Issues encountered:
  - None
- Validation:
  - `DocumentIntegrationTest` 파이프라인 테스트에 VERIFIED 반환·타 사용자 빈 결과·PENDING 전환 후 빈 결과 단언을 추가했다.
  - Backend 전체 `./gradlew check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/703 tests 중 702 통과. 유일한 실패 `S3ObjectStorageAdapterTest`는 이 환경에서 Docker Hub가 `minio/minio:RELEASE.2025-09-07T16-13-09Z` pull을 거부한 초기화 오류로, 이번 변경과 무관하며 미검증으로 남긴다.
- Next steps:
  - None

## [2026-07-23] Session Summary (책임별 persistence package 분리)

- What was done:
  - 기존 Java 파일 2개를 persistence 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
