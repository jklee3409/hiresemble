# Progress

## Overview

com.hiresemble.document.application.model package의 책임과 검증 상태를 추적한다. 이 package는 기존 Java 파일의 책임별 이동으로 생성됐으며 동작 계약은 변경하지 않았다.

## [2026-08-01] Session Summary (evidence apply 결과와 rejection reason 모델)

- What was done:
  - 적용 evidence ID와 candidate/applied/rejected 및 stable reason count를 검증하는 공용 application result를 추가했다.
- Key decisions:
  - 실제 candidate 값 없이 enum reason과 양의 count만 전달한다.
- Issues encountered:
  - None.
- Validation:
  - document/profile 단위·통합 테스트와 전체 check 통과.
- Next steps:
  - 새 rejection 분류는 안전한 stable enum으로만 확장한다.

## [2026-07-23] Session Summary (책임별 model package 분리)

- What was done:
  - 기존 Java 파일 2개를 model 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
