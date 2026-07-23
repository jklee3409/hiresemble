# Progress

## Overview

P4 parser·embedding policy·MinIO adapter 경계를 실제 dependency와 container로 검증한다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/test/java/com/hiresemble/document/infrastructure 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

- Key decisions:
  - 파일 경로, package·import와 필요한 FQCN만 변경하고 API·DB·workflow·접근 제한자는 유지했다.
  - 실제 파일이 있는 책임 package만 생성하고 P5 이후 기능과 빈 디렉터리는 만들지 않았다.

- Issues encountered:
  - package-private parser·outbox·configuration 접근 테스트는 운영 구현과 같은 책임 package로 함께 이동했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (Parser·MinIO·embedding policy 검증)

- What was done:
  - 정상 PDF/DOCX/TXT와 empty·oversize·위장·macro·암호화·corrupt·timeout fixture를 추가했다.
  - 실제 MinIO private object, 5분 presign, delete와 `vector(1536)` policy mismatch를 검증했다.
- Key decisions:
  - OCR·HWP·PPTX는 지원하지 않고 DOCM과 embedded active content를 거부한다.
- Issues encountered:
  - 20 MiB 정확 경계는 유효 PDF prefix 뒤 padding fixture로 고정했다.
- Validation:
  - targeted test와 Backend 전체 `check`가 통과했다.
- Next steps:
  - 실제 provider adapter는 이 테스트 profile에 등록하지 않는다.
