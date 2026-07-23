# Progress

## Overview

- Java 영역에는 P1 `common`·`auth`, P2 `profile`, P3 `agentrun`·`ai` package가 있다.
- resources 영역에는 Session·Agent worker 설정과 V1~V4 Flyway migration이 있다.
- P4 이후 책임 없는 미래 package와 production Fake workflow는 구현하지 않았다.

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

## [2026-07-19] Session Summary (P3 Agent Run·AI 운영 source 추가)

- What was done:
  - Java에 durable Agent runtime과 fixed AI workflow 기반을, resources에 V4와 worker 설정을 추가했다.

- Key decisions:
  - provider 기본값은 `none`이며 실제 executable contribution은 test scope에만 있다.

- Issues encountered:
  - None.

- Validation:
  - Backend 243 tests와 provider/network 정적 검색이 통과했다.

- Next steps:
  - P4 이후 package는 실제 domain vertical slice와 함께 추가한다.

## [2026-07-19] Session Summary (P2 profile 운영 소스·V3 추가)

- What was done:
  - 운영 Java source에 profile 4계층을, resources에 V3 profile migration을 추가했다.

- Key decisions:
  - P2 document aggregate와 P3 이후 package·table은 선행하지 않는다.

- Issues encountered:
  - None

- Validation:
  - Backend check와 빈 DB V1→V2→V3 boot가 통과했다.

- Next steps:
  - 후속 source는 구현 계획 phase 순서에 맞춰 추가한다.

## [2026-07-19] Session Summary (P1 운영 Java·리소스 구현)

- What was done:
  - 공통 오류·request ID·validation·idempotency와 인증 API·application·domain·infrastructure·security 구현을 추가했다.
  - 런타임 JDBC Session 자동 schema 생성을 비활성화하고 Flyway V2가 P1 schema를 관리하도록 설정했다.

- Key decisions:
  - 공개 API는 P1의 다섯 `/api/v1/auth` endpoint에 한정하고 성공 DTO를 직접 반환한다.
  - package는 실제 책임과 호출부가 있는 `common`과 `auth`만 추가했다.
  - Spring Session JDBC는 즉시 flush와 REQUIRED transaction operations로 JPA transaction에 참여시킨다.

- Issues encountered:
  - Spring Boot 4.1 구성 요소의 Jackson 3 및 Security CSRF 기본 동작을 명시적으로 맞춰야 했다.
  - 유료 provider 자동 구성은 기존처럼 비활성 기본값을 유지했다.

- Validation:
  - `Set-Location backend; .\\gradlew.bat check`가 운영 소스 compile, 설정 로딩, 31개 테스트와 함께 통과했다.
  - OpenAPI test가 정확히 다섯 auth path와 공통 오류 schema만 생성되는지 확인했다.

- Next steps:
  - P2에서는 명세 우선순위와 실제 use case에 따라 package·migration을 단계적으로 추가한다.
  - Session·Cookie 운영 속성은 배포 환경의 HTTPS·proxy 구성과 함께 검증한다.

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
