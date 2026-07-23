# Progress

## Overview

P4 Document HTTP·workflow port·storage·parser·embedding·outbox 통합 테스트를 구현했다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/test/java/com/hiresemble/document 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (P4 Document 테스트 구현)

- What was done:
  - upload replay·동시 key·owner·429·413·415, atomic idempotency 완료 rollback, Agent Run Document filter, manual/reparse, resource retry, evidence tombstone와 delete를 검증했다.
- Key decisions:
  - 양수 Fake reservation은 immutable test price version으로 고정하고 외부 비용 호출을 사용하지 않는다.
- Issues encountered:
  - 직접 terminal 상태를 만들던 fixture에서 reservation release가 필요해 실제 application 경계와 맞췄다.
  - 최초 Validator가 실제 Document resource filter 성공·격리·삭제 회귀 테스트 공백을 발견해 같은 통합 테스트에 추가했다.
- Validation:
  - Backend 전체 287 tests와 P4 실제 Browser E2E 4/4가 통과했다.
- Next steps:
  - P5 이후 provenance table 없이 Fake reference contributor로 tombstone branch를 유지한다.
