# Progress

## Overview

- Java 21, Spring Boot 4.1, Spring AI 2.0 기반 단일 애플리케이션의 초기 빌드 환경이 구성되어 있다.
- P1 공통 HTTP 오류·request ID, Session·CSRF 인증과 정확히 다섯 인증 API가 `common`과 `auth` 영역에 구현되어 있다.
- V2 migration은 사용자·기본 프로필·JDBC Session·idempotency 저장소만 추가하며 성공 응답 envelope와 P2 기능은 없다.

## [2026-07-19] Session Summary (P1 백엔드 인증·공통 HTTP 기반 구현)

- What was done:
  - `ErrorResponseDto`, `FieldErrorDto`, 중앙 factory·exception handler·Security writer와 서버 생성 UUID request ID filter를 구현했다.
  - signup, login, logout, csrf, me API와 사용자·프로필 영속성, BCrypt cost 12, Session rotation·무효화, UTF-8 byte 비밀번호 검증을 구현했다.
  - durable idempotency 저장·HMAC hash·처리 충돌·replay 구조와 정확한 P1 OpenAPI 경로 검증을 추가했다.

- Key decisions:
  - 실제 HTTP status와 동일한 여섯 오류 field를 ControllerAdvice와 Security에서 공유하고 내부 예외·거부 값·민감정보를 응답하지 않는다.
  - 가입 transaction은 사용자·기본 프로필과 즉시 flush되는 JDBC Session SQL을 함께 저장하며, 로그인 실패는 존재 여부와 무관하게 `INVALID_CREDENTIALS`를 반환한다.
  - JDBC Session schema 자동 생성을 끄고 Flyway만 schema를 소유하며 인증 endpoint에는 Idempotency-Key를 적용하지 않는다.

- Issues encountered:
  - Jackson 3 ObjectMapper, Spring Security 기본 XOR CSRF request handler, PostgreSQL JDBC 시간 타입 추론과 OpenAPI media type을 통합 테스트 근거로 보정했다.
  - 프로필 저장 실패 시 가입 transaction rollback 여부를 test-only trigger로 재현하고 사용자 저장 예외 처리 범위를 좁혔다.
  - 1차 validator가 TTL 만료 후 영구 replay와 Spring Session 기본 `REQUIRES_NEW` 저장의 부분 성공 가능성을 지적해 조건부 reclaim과 공식 transaction extension으로 보정했다.

- Validation:
  - 보정 에이전트와 루트의 `Set-Location backend; .\\gradlew.bat check`가 성공했고 Auth 15, OpenAPI 2, ErrorCode 1, idempotency 8, validation 2, migration 3의 총 31개 테스트가 모두 통과했다.
  - Testcontainers PostgreSQL에서 빈 DB 적용과 V1-only upgrade, JPA `ddl-auto=validate`, schema constraint·index·unique를 검증했다.
  - 만료 key reclaim 동시성과 Session persistence·deferred commit 양방향 실패 주입에서 부분 user/profile 또는 dangling 인증 Session이 없음을 검증했다.
  - 실제 외부 AI·검색 provider는 호출하지 않았다.

- Next steps:
  - P2의 첫 idempotent resource endpoint에서 검증·인증·소유권 이후 reservation과 비즈니스 transaction의 경계를 연결한다.
  - 운영 배포 전에 `IDEMPOTENCY_HMAC_KEY`, Secure Cookie와 proxy 환경 설정을 운영 secret/config로 제공한다.
  - agent_run_id가 연결된 만료 IN_PROGRESS row는 P3 run terminal 상태를 확인하는 reconciliation 정책으로 처리한다.

## [2026-07-17] Session Summary (Spring Boot 백엔드 초기 환경 구성)

- What was done:
  - 당시 구현 상태:
    - Java 21, Spring Boot 4.1, Spring AI 2.0 기반 단일 애플리케이션의 초기 빌드 환경이 구성되어 있다.
    - 실행 진입점, 환경 설정, pgvector 확장 migration만 존재하며 Controller, Service, Domain, Repository와 비즈니스 API는 아직 구현되지 않았다.
    - 공통 성공 envelope, 오류 DTO, `ErrorCode`, 커스텀 예외, 전역 예외 처리와 프로젝트용 Security 설정도 아직 구현되지 않았다.
  - 완료된 작업:
    - `build.gradle.kts`, `settings.gradle.kts`, Gradle Wrapper를 이용한 백엔드 빌드 기반을 구성했다.
    - PostgreSQL/JPA/Flyway, Session JDBC, Validation, Security, Spring AI, 문서 파싱, S3와 테스트 의존성을 선언했다.
    - 이 파일과 [`index.md`](index.md), `src/` 이하 관리 대상 디렉터리의 추적 문서를 생성해 현재 책임과 상태를 기록했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 비즈니스 기능 구현은 없다.
    - 백엔드와 하위 source/resource 영역의 초기 문서 추적 체계는 이번 작업에서 완료됐다.

- Key decisions:
  - Spring 응답·예외 처리는 레퍼런스의 중앙 변환 원칙만 채택하고 오류를 HTTP 200으로 반환하거나 성공 응답을 일괄 envelope로 감싸는 형식은 사용하지 않는다.
  - 공통 추상화는 실제 API 사용처가 생길 때 도입하며 문서에 적은 예상 package를 구현 완료로 간주하지 않는다.
  - 스키마는 Hibernate `ddl-auto=validate`와 Flyway migration으로 관리한다.

- Issues encountered:
  - 비즈니스 코드와 테스트 소스가 없어 현재 검증은 초기 애플리케이션 구성 범위에 한정된다.
  - [`src/main/resources/progress.md`](src/main/resources/progress.md)에 기록한 대로 리소스 계층의 추적 Markdown이 별도 제외 설정 없이 classpath에 복사될 수 있다.
  - 저장소에 아직 커밋이 없어 백엔드 기존 파일도 Git에서 untracked로 표시된다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`로 전체 문서 formatting 반영 후 백엔드 표준 검증을 재실행해 성공했다.
  - `rg --files backend`와 디렉터리별 파일 조회로 소스 구조 및 제외 대상(`gradle`, `build`, `.gradle`)을 확인했다.
  - 실제 API 계약 테스트는 구현 대상 코드가 없어 실행하지 않았다.

- Next steps:
  - [`../docs/agent-rules/backend-response-exception.md`](../docs/agent-rules/backend-response-exception.md)에 정의한 오류 DTO, `ErrorCode`, `BusinessException`, 전역 처리기와 Security 오류 writer를 실제 API 사용처와 함께 구현한다.
  - 인증, 프로필, 문서, 공고, 자기소개서, 면접과 Agent Run 도메인을 명세 우선순위에 따라 추가한다.
  - 단위·MockMvc·Testcontainers·외부 서비스 Fake 테스트 구조를 기능 구현과 함께 추가한다.
