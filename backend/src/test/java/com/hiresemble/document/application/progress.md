# Progress

## Overview

P4 text pipeline과 deletion outbox application 계약을 검증한다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/test/java/com/hiresemble/document/application 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (Text pipeline·outbox 검증)

- What was done:
  - normalization·NFC·code point·masking·chunk order와 outbox retry schedule·lease·중복 claim·DEAD를 테스트했다.
- Key decisions:
  - Object absent는 성공, 최대 10회 뒤 DEAD와 alert hook으로 고정했다.
- Issues encountered:
  - None.
- Validation:
  - 관련 PostgreSQL 통합 테스트가 모두 통과했다.
- Next steps:
  - 운영 alert adapter 연결은 P10 hardening 범위다.
