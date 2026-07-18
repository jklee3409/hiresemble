# Progress

## Overview

- Java 영역에는 `HiresembleApplication` 하나만 존재한다.
- resources 영역에는 `application.yml`과 pgvector 확장용 V1 Flyway migration이 존재한다.
- Controller, Service, Domain, Repository와 공통 오류 처리 구조는 구현되지 않았다.

## [2026-07-17] Session Summary (Spring Boot 운영 소스 최소 구조 구성)

- What was done:
  - 당시 구현 상태:
    - Java 영역에는 `HiresembleApplication` 하나만 존재한다.
    - resources 영역에는 `application.yml`과 pgvector 확장용 V1 Flyway migration이 존재한다.
    - Controller, Service, Domain, Repository와 공통 오류 처리 구조는 구현되지 않았다.
  - 완료된 작업:
    - 실행 가능한 Spring Boot 운영 source set의 최소 Java·resources 구조를 구성했다.
    - Java와 resources의 책임을 분리해 탐색할 수 있도록 각 계층의 `index.md`, `progress.md`를 생성했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 기능 코드 변경은 없다. 운영 source set의 문서 추적 체계는 이번 작업에서 완료됐다.

- Key decisions:
  - 초기 환경 단계에서는 사용처 없는 공통 abstraction과 빈 도메인 package를 선행 생성하지 않는다.
  - Java 구성과 DB schema 변경은 각각 코드 테스트와 Flyway 검증을 통해 동기화한다.

- Issues encountered:
  - 현재 Java와 DB schema 모두 비즈니스 기능을 제공하지 않는다.
  - resources 아래 문서가 classpath에 포함될 가능성은 [`resources/progress.md`](resources/progress.md)에서 추적한다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 성공해 현재 운영 source set의 컴파일·테스트 lifecycle이 통과했다.
  - 비즈니스 기능과 API가 없어 MockMvc 및 도메인 테스트는 실행할 대상이 없다.

- Next steps:
  - 명세에 따라 업무 package와 API를 작은 수직 기능 단위로 구현한다.
  - 공통 응답·예외 및 Security 오류 계약은 첫 관련 API 구현 시 함께 추가하고 계약 테스트를 작성한다.
  - DB table은 새 Flyway migration으로 순차 추가한다.
