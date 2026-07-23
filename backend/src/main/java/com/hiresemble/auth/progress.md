# Progress

## Overview

P1의 사용자 가입, Session 인증과 현재 사용자 projection을 구성하며 가입 기본 프로필과 P3 AI preference 생성을 각 application 경계에 위임한다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/auth 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (가입 기본 AI preference transaction 연결)

- What was done:
  - 가입 transaction에서 사용자·기본 profile 뒤 active budget policy 기반 기본 AI preference를 생성한다.

- Key decisions:
  - preference 생성 실패는 사용자·profile·Session과 함께 rollback 또는 폐기된다.

- Issues encountered:
  - None.

- Validation:
  - 실패 주입을 포함한 Auth integration tests가 통과했다.

- Next steps:
  - 공개 `/settings/ai`는 P10 전까지 추가하지 않는다.

## [2026-07-19] Session Summary (가입 기본 프로필 책임을 profile 경계로 이동)

- What was done:
  - 인증 공개 API 5개를 유지하면서 가입 transaction이 profile 등록 service를 호출하도록 연결했다.

- Key decisions:
  - auth는 credential·Session만 소유하고 `user_profiles` CRUD·영속성은 profile 영역이 소유한다.

- Issues encountered:
  - None

- Validation:
  - P1 인증·Session·CSRF 회귀와 가입 profile rollback 테스트가 Backend check에서 통과했다.

- Next steps:
  - 계정 설정·탈퇴는 해당 phase 전까지 추가하지 않는다.

## [2026-07-19] Session Summary (P1 Session 인증과 사용자 기반 구현)

- What was done:
  - 정확히 다섯 인증 endpoint, USER/ACTIVE 사용자와 기본 프로필, BCrypt·Session rotation·logout 무효화와 transaction 일관성을 구현했다.

- Key decisions:
  - 성공 DTO를 직접 반환하고 인증 사용자는 외부 user ID 입력이 아닌 Session principal에서만 결정한다.

- Issues encountered:
  - Spring Security 7 CSRF token 처리 형식을 Session token과 맞추고 profile 저장 실패 rollback을 통합 테스트로 고정했다.
  - 1차 validator가 request-end Session 저장의 부분 성공 가능성을 지적해 즉시 flush·REQUIRED 참여와 실패 시 in-memory Session 폐기로 보정했다.

- Validation:
  - Backend check의 인증·CSRF·Session·두 사용자 및 Session/DB 실패 원자성 통합 테스트가 모두 통과했다.

- Next steps:
  - P2에서 승인된 프로필 계약을 별도 기능 경계로 구현하고 auth 공개 API는 이 다섯 경로로 유지한다.
