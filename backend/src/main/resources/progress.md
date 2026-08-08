# Progress

## Overview

- `application.yml`에 PostgreSQL, Flyway, JPA validate, JDBC Session, multipart, AI, Actuator, OpenAPI, Object Storage와 검색 설정이 있다.
- AI chat/embedding/vector store 자동 구성은 provider 환경 변수의 기본값 `none`으로 비활성화되어 API key 없이 초기 부팅할 수 있다.
- JDBC Session runtime schema 초기화는 꺼져 있고 V1~V28 migration이 P1~P8, GitHub와 Career Artifact schema를 관리한다.
- Agent runtime 기본값은 heartbeat 15초, lease 60초, reconciliation 30초, worker 2개와 queue 32이며 provider는 `none`이다.
- Swagger UI는 `/swagger-ui.html`에서 Try It Out을 제공하며 JSON CSRF 계약과 맞지 않는 내장 CSRF 자동화는 사용하지 않는다.

## [2026-08-08] Session Summary (Career Artifact 설정과 V28)

- What was done:
  - 기본 비활성 feature flag, typed renderer/outbox 한도와 local·local-offline 활성값 및 V28 migration을 추가했다.
- Key decisions:
  - production/default는 `CAREER_ARTIFACT_ENABLED:false`, Gradle test는 true이며 GitHub flag와 독립이다.
- Issues encountered:
  - None.
- Validation:
  - Spring context, fresh/upgrade migration과 `docker compose config --quiet` 통과.
- Next steps:
  - 운영 활성화는 별도 배포 승인 범위다.

## [2026-08-02] Session Summary (V17 Career Guide 리소스 추가)

- What was done:
  - 전역 게시 가이드 table·index·초기 5개 콘텐츠를 V17 Flyway 리소스로 추가했다.
- Key decisions:
  - 별도 seed runner 없이 versioned migration에서 최초 공개 콘텐츠를 안전하게 제공한다.
- Issues encountered:
  - None.
- Validation:
  - Backend 전체 73 suites/498 tests와 Docker Compose config 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 자동 분석 재조정 설정)

- What was done:
  - 자동 분석 reconciliation 주기·lease·batch·retry와 고정 BALANCED 분석 비용의 안전한 기본값 및 환경 변수 예시를 추가했다.
- Key decisions:
  - test profile에서는 scheduler를 비활성화하고 통합 테스트가 coordinator를 명시적으로 구동한다.
- Issues encountered:
  - None.
- Validation:
  - configuration binding, V16 Testcontainers 적용과 자동 분석 통합 테스트 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (Job image item threshold 설정)

- What was done:
  - `JOB_IMAGE_MIN_ITEM_MEANINGFUL_CHARACTERS` 기본값 20을 설정 경계에 추가했다.
- Key decisions:
  - 최종 description 120자 기준과 별도 관리한다.
- Issues encountered:
  - None.
- Validation:
  - configuration binding과 전체 check, Compose config 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (Job page/image bounded policy 설정)

- What was done:
  - 이미지 timeout·후보 6개·개별 5MiB·전체 20MiB·pixel 상한과 DOM/description/replacement 품질 threshold 기본값을 추가했다.
- Key decisions:
  - 설정은 bounded validation을 통과해야 부팅하며 Provider/DB 설정은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - local/local-offline/test Bean matrix를 포함한 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 search·workflow 설정과 V12)

- What was done:
  - Tavily opt-in provider·endpoint·timeout, Interview workflow cost와 V12 migration을 설정 영역에 추가했다.
- Key decisions:
  - 기본 `SEARCH_PROVIDER=none`은 network-disabled이며 `tavily` 활성화 시 key 누락을 허용하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 기본 boot·WireMock provider test, migration fresh/upgrade와 `docker compose config --quiet`가 통과했다.
- Next steps:
  - 운영 key는 저장소에 추가하지 않는다.

## [2026-07-30] Session Summary (P7 V8·Cover Letter runtime 설정)

- What was done:
  - V8 migration과 generation·verification 비용 예약의 안전한 환경 override를 추가했다.
- Key decisions:
  - 기본 provider는 `none`, 기본 테스트 외 유료 호출은 비활성으로 유지한다.
- Issues encountered:
  - 없음.
- Validation:
  - Spring context·`ddl-auto=validate`, Backend 전체 check와 `docker compose config --quiet`가 통과했다.
- Next steps:
  - 운영 provider 가격·키는 별도 승인 전까지 설정하지 않는다.

## [2026-07-28] Session Summary (로컬 idempotency HMAC profile 추가)

- What was done:
  - `application-local.yml`에 개발 전용 HMAC 키를 분리하고 `.env.example`에서 local profile을 명시적으로 활성화했다.
  - profile 미지정·비로컬 환경은 기존 `IDEMPOTENCY_HMAC_KEY` placeholder를 사용한다.
- Key decisions:
  - 운영 secret을 저장소에 넣지 않고 로컬 즉시 실행성과 배포 환경 분리를 profile 우선순위로 해결했다.
- Issues encountered:
  - 알려진 개발 키가 profile 미지정 배포에 적용되지 않도록 전역 default profile을 두지 않았다.
- Validation:
  - 설정 리소스 회귀 테스트와 Backend 전체 `check`가 통과했다.
- Next steps:
  - 배포 환경에서는 `local`을 활성화하지 않고 versioned secret을 주입한다.

## [2026-07-27] Session Summary (P5 Job 설정·V6 추가)

- What was done:
  - Job fetch timeout·redirect·response byte, hourly Scheduler batch와 AI cost 설정 및 V6 migration을 추가했다.
- Key decisions:
  - 실제 AI provider 기본값은 disabled이며 URL fetch 제한에는 안전한 기본값을 둔다.
- Issues encountered:
  - 없음.
- Validation:
  - Backend check, migration 6개와 Compose config가 통과했다.
- Next steps:
  - 운영 provider 활성화 전 환경별 가격·network 설정을 승인한다.

## [2026-07-19] Session Summary (P4 V5·Document runtime 설정 추가)

- What was done:
  - V5와 parser·Object Storage·outbox·Document Fake cost configuration을 추가했다.
- Key decisions:
  - 기본 AI provider와 Document estimated cost는 `none`·0이며 양수 비용에는 price version이 필요하다.
- Issues encountered:
  - None.
- Validation:
  - application context, dimension fail-fast, Compose와 Backend check가 통과했다.
- Next steps:
  - 실제 key나 provider 가격을 기본 설정에 넣지 않는다.

## [2026-07-19] Session Summary (P3 V4와 Agent runtime 설정 추가)

- What was done:
  - V4 Agent Run·AI policy·budget migration과 worker timing·capacity 설정을 추가했다.
  - Spring AI chat·embedding·vector 자동 구성을 `none`으로 유지하고 P3 gateway도 disabled로 고정했다.

- Key decisions:
  - 환경 변수는 non-secret override만 제공하고 실제 provider key를 요구하지 않는다.

- Issues encountered:
  - 루트 `.env.example`의 legacy `AI_PROVIDER=openai`를 `none`으로 정정했다.

- Validation:
  - Backend boot·check와 `docker compose config --quiet`가 통과했다.

- Next steps:
  - 실제 provider를 도입할 때만 secret과 가격·model policy adapter를 연결한다.

## [2026-07-19] Session Summary (P2 V3 profile migration 추가)

- What was done:
  - Flyway classpath에 P2 구조화 프로필·direct evidence V3 migration을 추가했다.

- Key decisions:
  - AI provider 기본 비활성과 P1 Session 설정은 변경하지 않고 V1·V2를 보존했다.

- Issues encountered:
  - None

- Validation:
  - Backend check와 격리 빈 DB boot에서 V1→V2→V3가 순서대로 적용됐다.

- Next steps:
  - documents FK는 P4 새 migration에서 추가한다.

## [2026-07-19] Session Summary (Swagger UI Try It Out 설정 명시)

- What was done:
  - `springdoc.swagger-ui.try-it-out-enabled`를 `true`로 명시했다.

- Key decisions:
  - 기존 `/v3/api-docs`와 `/swagger-ui.html` 경로 및 runtime Security 허용 범위는 유지한다.
  - CSRF token은 Cookie가 아니라 API JSON 응답에서 얻으므로 Springdoc CSRF 자동화 설정을 추가하지 않는다.

- Issues encountered:
  - None

- Validation:
  - `/v3/api-docs/swagger-config`에서 `tryItOutEnabled=true`와 `csrf` 설정 부재를 통합 테스트로 확인했다.
  - Backend 전체 check가 통과했다.

- Next steps:
  - 운영 노출 여부는 배포 topology와 인증 정책을 승인한 뒤 별도 profile 또는 환경 설정으로 제어한다.

## [2026-07-19] Session Summary (P1 Session·Cookie·Flyway 설정 구현)

- What was done:
  - Session timeout·Cookie name/httpOnly/secure/sameSite와 idempotency key version·HMAC secret의 환경 설정 경계를 추가했다.
  - `spring.session.jdbc.initialize-schema`를 `never`로 변경해 Flyway가 JDBC Session table을 독점 관리하도록 했다.
  - JDBC Session `flush-mode`를 `immediate`로 설정해 인증 Session SQL이 application transaction 안에서 실행되도록 했다.
  - V2 P1 migration과 OpenAPI P1 path 설정을 운영 리소스에 반영했다.

- Key decisions:
  - 개발 기본 Cookie는 안전한 예시로 제어하고 운영 Secure 여부와 비밀 HMAC key는 환경에서 주입한다.
  - HMAC key는 아직 인증 endpoint에 사용하지 않으며 첫 idempotent endpoint 전까지 빈 기본값을 허용한다.
  - AI provider 비활성 기본값과 JPA `ddl-auto=validate`를 유지한다.

- Issues encountered:
  - 기존 runtime Session schema 자동 생성 설정은 Flyway ownership 계약과 충돌해 `never`로 수정했다.
  - Spring Session JDBC 4.1 기본 transaction propagation이 `REQUIRES_NEW`여서 named operations를 Java 설정에서 `REQUIRED`로 보정했다.
  - resources 계층의 Markdown이 classpath에 포함되는 기존 추적 이슈는 이번 P1 기능 범위 밖이라 유지된다.

- Validation:
  - `Set-Location backend; .\\gradlew.bat check`에서 설정 로딩, JPA validate와 Session·CSRF 통합 테스트가 통과했다.
  - Session persistence 실패와 인증 Session 변경 뒤 deferred DB commit 실패를 주입해 같은 transaction rollback을 검증했다.
  - Testcontainers migration test로 빈 DB와 V1-only upgrade를 검증했다.

- Next steps:
  - 운영 환경에서 Secure Cookie, SameSite와 reverse proxy HTTPS 전달 설정을 실제 배포 topology에 맞춰 검증한다.
  - 첫 idempotent endpoint 전에 비어 있지 않은 HMAC secret을 운영 secret manager에서 제공한다.

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
