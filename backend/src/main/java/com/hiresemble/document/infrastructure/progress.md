# Progress

## Overview

P4 parser·JDBC·S3-compatible storage·embedding query·deletion outbox adapter를 구현했다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/document/infrastructure 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (Document infrastructure 구현)

- What was done:
  - 정상 PDF/DOCX/TXT, 위장·macro·암호화·손상·크기·timeout 방어와 MinIO upload/head/presign/delete를 구현했다.
  - `vector(1536)` exact cosine owner/generation/active-document query와 outbox lease·10회 retry를 구현했다.
- Key decisions:
  - 20 MiB와 parser resource limit을 configuration으로 고정하고 Object absent delete는 성공으로 처리한다.
- Issues encountered:
  - parser timeout은 테스트에서 `Duration.ZERO`와 최대 경계 fixture로 결정적으로 검증했다.
- Validation:
  - 실제 MinIO Testcontainer와 PostgreSQL integration, user·generation·deleted 격리, lease recovery·duplicate worker·DEAD가 통과했다.
- Next steps:
  - ANN index와 실제 embedding provider는 P4 범위 밖이다.
