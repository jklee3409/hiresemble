# Progress

## Overview

com.hiresemble.agentrun.infrastructure.config package의 runtime·scheduler 설정 책임과 검증 상태를 추적한다.

## [2026-08-09] Session Summary (통합 테스트 scheduler 격리)

- What was done: `@EnableScheduling`을 별도 conditional `SchedulingConfiguration`으로 옮겼다.
- Key decisions: production 기본 true와 outbox cadence/retry는 유지하고 PostgreSQL 통합 테스트만 scheduler를 끈다.
- Issues encountered: scheduled scan과 테스트의 수동 `processDue`가 같은 PENDING row를 경쟁했다.
- Validation: focused scheduler/outbox test와 Backend 전체 680 tests가 통과했다.
- Next steps: scheduler를 끄는 다른 profile은 명시적 사유가 있을 때만 추가한다.

## [2026-07-23] Session Summary (책임별 config package 분리)

- What was done:
  - 기존 Java 파일 2개를 config 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
