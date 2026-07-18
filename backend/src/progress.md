# Progress

## Overview

- `src/main` source set만 존재한다.
- 운영 소스에는 애플리케이션 진입점과 설정·migration만 있으며 비즈니스 기능은 구현되지 않았다.
- `src/test` source set은 아직 생성되지 않았다.

## [2026-07-17] Session Summary (Gradle 백엔드 소스 구조 구성)

- What was done:
  - 당시 구현 상태:
    - `src/main` source set만 존재한다.
    - 운영 소스에는 애플리케이션 진입점과 설정·migration만 있으며 비즈니스 기능은 구현되지 않았다.
    - `src/test` source set은 아직 생성되지 않았다.
  - 완료된 작업:
    - Java 소스와 classpath 리소스를 `main` 아래의 표준 Gradle 구조로 배치했다.
    - `src`, `main`, Java와 resources 하위 경계를 설명하는 추적 문서를 생성했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 운영 코드나 테스트 코드 개발은 없다. 초기 문서 계층은 이번 작업에서 완료됐다.

- Key decisions:
  - 현재 단계에서는 비어 있는 `test` 디렉터리를 문서만 위해 선행 생성하지 않는다.
  - 운영 소스와 테스트 소스는 Gradle 표준 source set 경계를 유지한다.

- Issues encountered:
  - 테스트 source set이 없어 실제 비즈니스 동작을 검증할 수 없다.
  - `main/resources` 아래 Markdown의 패키징 위험은 [`main/resources/progress.md`](main/resources/progress.md)에 별도로 추적한다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 성공했다.
  - `rg --files backend/src`로 현재 `main`만 존재하고 테스트 소스가 없음을 확인했다.
  - 기능 테스트는 대상 기능이 없어 미실행 상태다.

- Next steps:
  - 첫 기능 구현 시 `src/test/java`를 추가하고 도메인·API·통합 테스트를 운영 코드와 함께 작성한다.
  - 테스트 fixture나 리소스가 필요해질 때 소유권을 정한 뒤 `src/test/resources`를 추가한다.
