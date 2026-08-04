# Progress

## Overview

com.hiresemble.auth.api.dto package의 책임과 검증 상태를 추적한다. 이 package는 기존 Java 파일의 책임별 이동으로 생성됐으며 동작 계약은 변경하지 않았다.

## [2026-08-04] Session Summary (Signup 비밀번호 정책 연결)

- What was done:
  - `SignupRequest.password`에 10자 이상·문자·숫자·특수문자 조합 정책과 UTF-8 72바이트 상한을 연결했다.
- Key decisions:
  - password 원문은 오류 응답이나 로그에 노출하지 않고 기존 `password` field error 계약을 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Auth 통합·OpenAPI 계약 테스트와 Backend 전체 `check`가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (DisplayNameUpdateRequest)

- What was done:
  - trim 후 1~100자이며 제어 문자와 `/`, `\`를 거부하는 닉네임 변경 request record를 추가했다.
- Key decisions:
  - Signup displayName과 같은 공개 validation 경계를 사용한다.
- Issues encountered:
  - None.
- Validation:
  - 400 field error와 OpenAPI request schema, 전체 Backend check가 통과했다.
- Next steps:
  - None.

## [2026-07-23] Session Summary (책임별 dto package 분리)

- What was done:
  - 기존 Java 파일 5개를 dto 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
