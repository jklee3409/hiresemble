# Progress

## Overview

P2 공개 프로필·direct evidence 25개 operation과 DTO·validation 경계가 구현되어 있다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/profile/api 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

- Key decisions:
  - 파일 경로, package·import와 필요한 FQCN만 변경하고 API·DB·workflow·접근 제한자는 유지했다.
  - 실제 파일이 있는 책임 package만 생성하고 P5 이후 기능과 빈 디렉터리는 만들지 않았다.

- Issues encountered:
  - package-private `ProfileDtoMapper`를 별도 mapper package로 옮기려면 접근 제한자 변경이 필요해 `ProfileController`와 함께 기존 package에 남겼다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P4 document evidence filter 활성화)

- What was done:
  - 기존 evidence 3개 operation에 real `documentId` filter와 document source edit·approve·reject를 연결했다.
- Key decisions:
  - 타 사용자·삭제 document filter는 404, `SOURCE_DELETED` mutation은 409다.
- Issues encountered:
  - None.
- Validation:
  - 기존 operation 수를 늘리지 않고 OpenAPI 전체 43/30과 API 통합 테스트가 통과했다.
- Next steps:
  - evidence create/delete endpoint는 추가하지 않는다.

## [2026-07-19] Session Summary (P2 프로필 HTTP·OpenAPI 계약 구현)

- What was done:
  - 기본 프로필, 다섯 구조화 resource CRUD, evidence 조회·편집·검토 API를 구현했다.
  - pagination 기본·최대, sort allowlist, nullable field와 enum을 공개 DTO에 고정했다.

- Key decisions:
  - Controller에는 도메인 규칙과 owner 판정을 두지 않고 application 결과만 직접 DTO로 반환한다.

- Issues encountered:
  - None

- Validation:
  - MockMvc·OpenAPI 테스트에서 status, CSRF·401, validation, owner 404, version 409와 정확히 30개 전체 operation을 검증했다.

- Next steps:
  - P4 document 구현 전까지 document 선택·filter를 공개 사용 흐름으로 활성화하지 않는다.
