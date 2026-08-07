# Progress

## Overview

com.hiresemble.profile.api.dto package의 책임과 검증 상태를 추적한다. 이 package는 기존 Java 파일의 책임별 이동으로 생성됐으며 동작 계약은 변경하지 않았다.

## [2026-08-07] Session Summary (경험 라이브러리 DTO 계약)

- What was done:
  - canonical 경험, 출처 수, match 종류·유사도와 수정·검증·해결 요청 DTO를 추가하고 문서 evidence에 canonical 경험 link projection을 연결했다.
- Key decisions:
  - source 원문과 embedding은 공개하지 않고 표시·검토에 필요한 값만 반환한다.
- Issues encountered:
  - None.
- Validation:
  - compile과 OpenAPI contract 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (Education DTO 단계 계약)

- What was done:
  - 교육 request에 필수 `EducationLevel`, response에 read-only final education flag를 반영했다.
- Key decisions:
  - client가 최종 학력 flag를 직접 쓰지 못하도록 request field를 제거했다.
- Issues encountered:
  - None.
- Validation:
  - compile·OpenAPI contract와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-23] Session Summary (책임별 dto package 분리)

- What was done:
  - 기존 Java 파일 3개를 dto 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
