# Progress

## Overview

P1~P5 OpenAPI metadata와 Session·CSRF, 공통 오류 응답 보강을 관리한다.

## [2026-07-27] Session Summary (Job OpenAPI 공통 오류·security 연결)

- What was done:
  - Job GET/mutation별 400·401·403·404·409·429·503 응답과 CSRF security를 보강했다.
- Key decisions:
  - 성공 DTO는 Controller operation 문서를 원천으로 하고 공통 customizer는 오류·security만 추가한다.
- Issues encountered:
  - 없음.
- Validation:
  - 생성 OpenAPI 50 operations/34 paths와 Job 7개 operation 계약이 통과했다.
- Next steps:
  - P6 endpoint가 생길 때 실제 계약만 추가한다.

## [2026-07-23] Session Summary (책임별 openapi package 분리)

- What was done:
  - 기존 Java 파일 1개를 openapi 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
