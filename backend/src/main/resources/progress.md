# Progress

## Overview

- `application.yml`에 PostgreSQL, Flyway, JPA validate, JDBC Session, multipart, AI, Actuator, OpenAPI, Object Storage와 검색 설정이 있다.
- AI chat/embedding/vector store 자동 구성은 provider 환경 변수의 기본값 `none`으로 비활성화되어 API key 없이 초기 부팅할 수 있다.
- DB 리소스에는 pgvector 확장 활성화 migration만 있고 업무 table schema는 없다.

## [2026-07-17] Session Summary (Spring 설정 및 DB 리소스 기반 구성)

- What was done:
  - 당시 구현 상태:
    - `application.yml`에 PostgreSQL, Flyway, JPA validate, JDBC Session, multipart, AI, Actuator, OpenAPI, Object Storage와 검색 설정이 있다.
    - AI chat/embedding/vector store 자동 구성은 provider 환경 변수의 기본값 `none`으로 비활성화되어 API key 없이 초기 부팅할 수 있다.
    - DB 리소스에는 pgvector 확장 활성화 migration만 있고 업무 table schema는 없다.
  - 완료된 작업:
    - `.env`를 선택적으로 읽는 로컬 설정과 환경 변수 기반 override를 구성했다.
    - 운영 리소스, DB와 migration 하위 계층에 `index.md`, `progress.md`를 생성했다.
    - 리소스 추적 문서의 classpath 포함 가능성을 변경 주의사항과 이 진행 기록에 명시했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 설정 또는 migration 변경은 없다. 리소스 상태의 초기 문서화는 완료됐다.

- Key decisions:
  - 현재 문서 추적 규칙에 따라 resources 계층에도 두 문서를 두되, 패키징 위험을 숨기지 않고 후속 빌드 정책 과제로 남긴다.
  - schema 자동 생성 대신 `ddl-auto=validate`와 Flyway를 유지한다.
  - 유료 AI·검색 연동은 명시적 환경 설정 없이 자동 호출되지 않게 한다.

- Issues encountered:
  - `src/main/resources`는 표준 Gradle resource source set이므로 이 디렉터리와 `db`, `db/migration`에 생성한 총 6개 추적 Markdown이 별도 제외 설정 없이 `build/resources/main`과 bootJar에 포함될 수 있다.
  - 현재 `build.gradle.kts`에는 Markdown 제외 규칙이 없다. 이번 작업 범위는 문서 생성만이므로 설정은 변경하지 않았다.
  - 로컬 기본 credential은 개발 편의를 위한 값이므로 운영 환경에서는 반드시 override해야 한다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 성공했다.
  - 검증 후 `build/resources/main`을 조회해 resources 계층의 추적 Markdown 6개가 실제 복사되는 것을 확인했다.
  - 실제 외부 AI, 검색, Object Storage 호출은 실행하지 않았다.

- Next steps:
  - 실제 기능 추가 시 typed `@ConfigurationProperties`, profile별 운영 설정과 보안 검증을 보강한다.
  - 운영 bootJar에 추적 Markdown이 포함되지 않도록 Gradle resource exclusion 또는 문서 관리 위치 변경 방안을 결정한다.
  - DB 명세에 따른 table, index와 constraint를 새 migration으로 추가한다.
