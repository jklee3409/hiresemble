# Progress

## Overview

- Java 21, Spring Boot 4.1, Spring AI 2.0 기반 단일 애플리케이션의 초기 빌드 환경이 구성되어 있다.
- 실행 진입점, 환경 설정, pgvector 확장 migration만 존재하며 Controller, Service, Domain, Repository와 비즈니스 API는 아직 구현되지 않았다.
- 공통 성공 envelope, 오류 DTO, `ErrorCode`, 커스텀 예외, 전역 예외 처리와 프로젝트용 Security 설정도 아직 구현되지 않았다.

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
