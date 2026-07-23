# Progress

## Overview

Document parse와 evidence extraction의 독립 상태 축 및 삭제·version 불변식을 구현했다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/document/domain 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (Document 상태 모델 고정)

- What was done:
  - 문서·parser·evidence·outbox enum과 snapshot validation을 구현했다.
- Key decisions:
  - AI 실패는 성공한 parse 상태를 되돌리지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 상태 전이, partial success와 DB enum parity 테스트가 통과했다.
- Next steps:
  - P4 상태 축을 P6 RAG 상태와 합치지 않는다.
