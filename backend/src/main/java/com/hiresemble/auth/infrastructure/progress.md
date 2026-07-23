# Progress

## Overview

P1 users credential·상태의 JPA mapping과 repository만 소유한다. `user_profiles` 영속성은 P2 profile 영역으로 이동했다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/auth/infrastructure 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (user_profiles 영속성 소유권 이동)

- What was done:
  - auth의 `UserProfileEntity`와 `UserProfileRepository`를 제거하고 사용자 entity·repository만 남겼다.

- Key decisions:
  - 동일 table을 두 영역이 중복 mapping하지 않고 profile JDBC store를 단일 소유자로 사용한다.

- Issues encountered:
  - None

- Validation:
  - JPA schema validate와 가입·Session 통합 테스트가 통과했다.

- Next steps:
  - 계정 관련 table이 실제 추가될 때만 이 영역을 확장한다.

## [2026-07-19] Session Summary (P1 사용자·기본 프로필 JPA mapping 구현)

- What was done:
  - users와 user_profiles entity·repository를 추가하고 가입 transaction에 연결했다.

- Key decisions:
  - 프로필 상세 CRUD 없이 빈 배열 기본값을 가진 사용자당 하나의 기본 row만 생성한다.

- Issues encountered:
  - 사용자 unique 위반만 이메일 중복으로 매핑하고 profile 실패는 내부 오류와 transaction rollback으로 유지했다.

- Validation:
  - Testcontainers PostgreSQL에서 JPA validate, BCrypt 평문 비저장과 user/profile 원자성을 검증했다.

- Next steps:
  - P2 프로필 입력 제약은 새 migration과 전용 application 경계에서 추가한다.
