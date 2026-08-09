# Progress

## Overview

com.hiresemble.auth.infrastructure.persistence package의 user/session/deletion task·purge 책임과 검증 상태를 추적한다.

## [2026-08-09] Session Summary (Terminal purge stores)

- What was done: Spring Session, deletion task claim/lease와 owner cleanup store를 추가했다.
- Key decisions: task table에는 user FK와 개인정보를 두지 않는다.
- Issues encountered: FK 의존 순서와 outbox terminal gate를 실제 schema에서 확인했다.
- Validation: Testcontainers migration/worker와 전체 check가 통과했다.
- Next steps: 새로운 owner table 추가 시 purge inventory를 갱신한다.

## [2026-07-31] Session Summary (UserEntity 닉네임 변경)

- What was done:
  - 표시 이름과 갱신 시각을 함께 적용하는 `changeDisplayName`을 추가했다.
- Key decisions:
  - Repository query나 schema를 확장하지 않고 JPA dirty checking과 명시적 flush를 사용한다.
- Issues encountered:
  - None.
- Validation:
  - AuthIntegrationTest의 DB 직접 assertion과 전체 Backend check가 통과했다.
- Next steps:
  - None.

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
