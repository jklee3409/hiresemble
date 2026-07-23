# Progress

## Overview

P3 canonical Run·Step enum과 전이·projection 불변식이 구현됐다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/agentrun/domain 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (Agent Run과 Step 상태 machine 구현)

- What was done:
  - Run 7개, Step 9개 상태와 모든 허용·금지 전이를 구현했다.
  - terminal 불변, 최대 Step attempt 3, stateVersion 증가와 retryable/cancellable 계산을 고정했다.

- Key decisions:
  - cancel request timestamp와 terminal CANCELLED를 분리한다.
  - WAITING_USER resume은 같은 Run·Step attempt를 사용하고 terminal retry는 successor Run attempt를 증가시킨다.

- Issues encountered:
  - None.

- Validation:
  - exhaustive domain test 136개를 포함한 Backend 전체 검증이 통과했다.

- Next steps:
  - 새 상태를 임의 추가하지 않고 계약 변경이 필요하면 명세 결정을 먼저 수행한다.
