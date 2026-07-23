# Progress

## Overview

P3 Agent Run 공개 5 operation과 snapshot-first SSE projection이 구현됐다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/agentrun/api 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (Agent Run API와 SSE 계약 구현)

- What was done:
  - 목록 filter·pagination·sort, 상세 timeline, retry Idempotency-Key와 cancel stateVersion CAS를 구현했다.
  - snapshot, progress, step, waiting_user, heartbeat, terminal event와 subscriber race buffering을 구현했다.

- Key decisions:
  - 타 사용자 Run과 resource filter는 404로 숨기고 P3 resource 성공 경로를 만들지 않는다.
  - terminal 뒤 emitter를 닫고 재연결은 새 DB snapshot부터 시작한다.

- Issues encountered:
  - 최초 Validator가 SSE owner 404의 빈 본문을 공통 오류 계약 위반으로 판정해, SSE 성공 응답은 `text/event-stream`을 유지하면서 owner 실패는 기존 6-field JSON 오류 DTO로 반환하도록 보정했다.

- Validation:
  - MockMvc·실제 PostgreSQL SSE 테스트에서 타 사용자 404의 공통 필드와 content type을 검증했고 OpenAPI 35 operation/24 path 회귀가 통과했다.

- Next steps:
  - P4 이후 typed resource가 생기면 resource filter 성공 경로와 관련 resource invalidation을 연결한다.
