# Progress

## Overview

- `com.hiresemble` 아래 P1 common·auth, P2 profile과 P3 agentrun·ai package가 구현되어 있다.
- 공개 Controller는 인증 5, profile 25, Agent Run 5 operation만 제공한다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - 운영 Java 158개와 package-private 결합 테스트 4개의 책임별 이동 및 상위 source tree 문서 연결을 반영했다.

- Key decisions:
  - 파일 경로, package·import와 필요한 FQCN만 변경하고 API·DB·workflow·접근 제한자는 유지했다.
  - 실제 파일이 있는 책임 package만 생성하고 P5 이후 기능과 빈 디렉터리는 만들지 않았다.

- Issues encountered:
  - 한국어 literal/comment 19개의 중간 인코딩 손상을 발견해 HEAD UTF-8 원문을 복원하고 byte-safe 본문 대조로 재확인했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P1~P3 Java package 구현 상태 반영)

- What was done:
  - 공통 HTTP·인증, profile과 Agent Run·AI runtime package의 실제 상태를 문서에 반영했다.

- Key decisions:
  - 업무 구현은 계속 `com.hiresemble` 하위 책임 package에 둔다.

- Issues encountered:
  - 이 상위 progress가 초기 상태로 남아 있어 P3 통합 시 현재 코드와 맞게 갱신했다.

- Validation:
  - production Java 파일·HTTP mapping과 package tree를 정적으로 확인했다.

- Next steps:
  - 새 package 추가 시 상위 namespace 상태도 함께 갱신한다.

## [2026-07-17] Session Summary (Java 기본 패키지 및 진입점 구성)

- What was done:
  - 당시 구현 상태:
    - `com.hiresemble.HiresembleApplication`만 존재하는 최소 Java 구조다.
    - 비즈니스 package, 공통 API 오류 package, Controller와 테스트 대상 Java 구현은 아직 없다.
  - 완료된 작업:
    - `com/hiresemble` 기본 package와 Spring Boot 실행 진입점을 구성했다.
    - 중간 namespace를 포함한 Java 소스 문서 계층을 생성했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 Java 기능 구현은 없다. package 책임의 초기 문서화는 완료됐다.

- Key decisions:
  - 기본 package는 `com.hiresemble`로 유지한다.
  - 도메인 package는 문서의 예상 목록을 한 번에 만들지 않고 실제 use case와 함께 추가한다.

- Issues encountered:
  - 기능 코드가 없어 package 경계와 의존 방향을 컴파일 수준 외에는 검증할 수 없다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 성공해 현재 `HiresembleApplication`의 컴파일을 확인했다.
  - package 의존 규칙이나 API 계약 테스트는 구현 코드가 없어 미실행이다.

- Next steps:
  - `auth`, `profile`, `document`, `job`, `coverletter`, `interview`, `agentrun` 등 실제 요구되는 도메인을 기능 구현 시점에 추가한다.
  - 공통 오류 구조와 Security 연동은 [`../../../../docs/agent-rules/backend-response-exception.md`](../../../../docs/agent-rules/backend-response-exception.md)에 따라 구현한다.
  - 도메인 및 API 테스트용 `src/test/java` 구조를 추가한다.
