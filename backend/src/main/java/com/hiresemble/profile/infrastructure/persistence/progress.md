# Progress

## Overview

com.hiresemble.profile.infrastructure.persistence package의 책임과 검증 상태를 추적한다. 이 package는 기존 Java 파일의 책임별 이동으로 생성됐으며 동작 계약은 변경하지 않았다.

## [2026-07-31] Session Summary (최종 학력 lock·flag 교체)

- What was done:
  - profile row lock, active 학력 조회와 안전한 primary demote/promote persistence 연산을 추가했다.
- Key decisions:
  - partial unique 충돌을 피하도록 기존 최종 학력을 먼저 해제한 뒤 새 항목을 승격한다.
- Issues encountered:
  - None.
- Validation:
  - concurrent 경계의 transaction 구조를 검토하고 Profile 통합·전체 Backend check를 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (evidence 조회 학력 방어)

- What was done:
  - 단건·목록·VERIFIED 분석 조회에서 학력 source와 교육 category를 함께 제외했다.
- Key decisions:
  - service 생성 차단과 별개로 legacy DB row가 공개/분석에 섞이지 않도록 query 방어를 유지한다.
- Issues encountered:
  - None.
- Validation:
  - legacy 학력 tombstone 404와 분석 제외 통합 검증, Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-23] Session Summary (책임별 persistence package 분리)

- What was done:
  - 기존 Java 파일 1개를 persistence 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
