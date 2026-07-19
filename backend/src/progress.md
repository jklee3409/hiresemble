# Progress

## Overview

- `src/main`에는 P1 auth, P2 profile과 P3 Agent Run·AI runtime 운영 코드 및 V1~V4 migration이 있다.
- `src/test`에는 인증·프로필·Agent Run·AI·migration 단위·통합 테스트와 test-only prompt fixture가 있다.
- 운영·테스트 source set은 분리되며 production 공개 test endpoint는 없다.

## [2026-07-19] Session Summary (P3 운영·테스트 source 확장)

- What was done:
  - main에 agentrun·ai와 V4를, test에 PostgreSQL runtime·Fake workflow와 prompt resource를 추가했다.

- Key decisions:
  - production과 test contribution·fixture 경계를 source set으로 고정한다.

- Issues encountered:
  - None.

- Validation:
  - production HTTP mapping 35개와 test-only Fake endpoint 0개를 정적으로 확인했다.

- Next steps:
  - P4 기능도 운영·테스트 source를 같은 수직 단계에서 추가한다.

## [2026-07-19] Session Summary (P1 운영·테스트 source set 구현)

- What was done:
  - `src/main`에 P1 공통 HTTP·인증·영속성·Security·migration 코드를 추가했다.
  - `src/test/java`에 MockMvc, Testcontainers, migration, 민감정보 비노출과 idempotency fixture 테스트를 추가했다.

- Key decisions:
  - 동시성·replay 검증을 위한 fixture Controller는 test source에만 두고 운영 OpenAPI와 production path에는 노출하지 않는다.
  - 실제 PostgreSQL 동작이 필요한 repository·Flyway 검증은 운영 DB 대신 Testcontainers를 사용한다.

- Issues encountered:
  - 통합 테스트에서 드러난 CSRF token 처리와 JDBC 시간 바인딩 차이를 운영 설정·repository에서 보정했다.
  - 1차 validator의 TTL·Session transaction 지적을 조건부 upsert, 즉시 Session flush와 REQUIRED transaction 참여로 보정했다.
  - `src/test/resources`는 필요하지 않아 선행 생성하지 않았다.

- Validation:
  - 최종 `Set-Location backend; .\\gradlew.bat check`가 총 31개 테스트와 함께 통과했다.
  - production source와 test fixture 경계를 파일·OpenAPI path test로 확인했다.

- Next steps:
  - P2 기능은 실제 수직 use case와 함께 운영·테스트 source를 같은 작업에서 추가한다.
  - 외부 연동이 생기면 Fake 또는 WireMock을 사용하고 유료 provider를 테스트에서 호출하지 않는다.

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
