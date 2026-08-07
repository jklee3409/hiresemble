# Progress

## Overview

com.hiresemble.profile.domain.model package의 책임과 검증 상태를 추적한다. 이 package는 기존 Java 파일의 책임별 이동으로 생성됐으며 동작 계약은 변경하지 않았다.

## [2026-08-07] Session Summary (canonical 경험 상태 모델)

- What was done:
  - 경험 record·command와 `NEW`, `SAME_EXPERIENCE`, `RELATED_DIFFERENT`, `CONFLICT` 및 해결 enum을 추가했다.
- Key decisions:
  - AI 제안 상태와 사용자 검증 상태를 별도 축으로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - compile과 Profile domain 테스트 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (EducationLevel model)

- What was done:
  - `OTHER`부터 `DOCTORATE`까지 학력 단계와 비교 rank를 가진 domain enum을 추가했다.
- Key decisions:
  - 사용자 표시 label은 Frontend에 두고 Backend enum은 판정 순위만 소유한다.
- Issues encountered:
  - None.
- Validation:
  - compile, Profile 통합 테스트와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-23] Session Summary (책임별 model package 분리)

- What was done:
  - 기존 Java 파일 8개를 model 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
