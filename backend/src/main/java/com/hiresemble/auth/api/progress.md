# Progress

## Overview

P1 인증 Controller와 request·response DTO의 공개 HTTP·Swagger 계약을 소유한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/auth/api 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (인증 Controller Swagger metadata 보강)

- What was done:
  - 다섯 endpoint에 안정적 operationId, 설명, 실제 응답 schema와 Session·CSRF requirement를 추가했다.
  - request DTO에 validation을 통과하는 가짜 email·password·동의 example과 UTF-8 byte 설명을 추가했다.
  - Servlet request/response, principal과 `CsrfToken` framework 인자를 Swagger 입력에서 숨겼다.

- Key decisions:
  - signup/login 성공 뒤 회전된 Session과 새 CSRF token으로 Swagger Authorize 값을 교체하는 흐름을 operation description에 기록했다.
  - API path·status·DTO field와 인증 동작은 변경하지 않았다.

- Issues encountered:
  - None

- Validation:
  - 생성 OpenAPI에서 operationId·response·example·hidden parameter와 security requirement가 통과했다.
  - Backend 전체 33개 테스트가 통과했다.

- Next steps:
  - 후속 인증·계정 Controller도 같은 metadata와 안전한 example 규칙을 적용한다.

## [2026-07-19] Session Summary (P1 인증 HTTP 계약 구현)

- What was done:
  - P1 다섯 경로와 OpenAPI 응답 schema, 입력 validation을 실제 Controller와 record DTO로 구현했다.

- Key decisions:
  - signup은 201, logout은 204이며 모든 성공 응답은 공통 envelope 없이 계약 DTO를 직접 반환한다.

- Issues encountered:
  - OpenAPI의 JSON media type을 명시해 AuthSessionDto schema reference가 안정적으로 생성되도록 했다.

- Validation:
  - OpenAPI path set·schema test와 MockMvc 인증 통합 테스트가 통과했다.

- Next steps:
  - P1 범위를 유지하고 계정 변경 endpoint는 후속 단계 승인 범위에서만 추가한다.
