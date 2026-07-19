# Progress

## Overview

- Java 21, Spring Boot 4.1, Spring AI 2.0 기반 단일 애플리케이션의 초기 빌드 환경이 구성되어 있다.
- P1 인증 5개, P2 profile 25개, P3 Agent Run 5개와 P4 Document 8개 API가 구현되어 있다.
- V1~V4를 보존한 V5 migration이 Document·text·chunk·typed Run link·Object outbox DB 불변식을 추가한다.
- 생성 OpenAPI는 정확히 43 operations/30 paths이며 job·설정 등 P5 이후 endpoint는 없다.

## [2026-07-19] Session Summary (P4 Document Backend·AI pipeline 구현)

- What was done:
  - V5 Document schema, 8개 API, parser·storage·outbox·embedding query와 profile evidence document FK를 구현했다.
  - `DOCUMENT_INGESTION` 고정 workflow, masked-only Fake AI, manual same-run resume와 resource-linked retry를 연결했다.

- Key decisions:
  - 외부 Object 저장 뒤 DB 실패에는 즉시 보상 삭제하고 그마저 실패하면 document FK 없는 orphan cleanup outbox를 남긴다.
  - Object 준비는 transaction 밖에 두고 Document·Run·budget·typed link·idempotency 완료 응답은 한 transaction으로 커밋한다.
  - embedding active policy는 OpenAI/text-embedding-3-small/1536/cosine/generation 1 metadata지만 실제 provider는 활성화하지 않는다.

- Issues encountered:
  - 양수 budget reservation과 Fake usage price pair를 test-scope immutable price catalog에 맞게 보정했다.
  - 최초 read-only Validator가 Agent Run 목록의 Document filter가 application 예약 404에 막힌 점을 MAJOR로 판정해 active owner resolver와 성공·격리·삭제 테스트를 한 차례 보정했다.

- Validation:
  - `backend\\gradlew.bat check` 30 suites/287 tests, 별도 `p4BrowserE2eTest`에서 Playwright 4/4가 통과했다.
  - 빈 DB와 V1/V2/V3/V4-only upgrade, 실제 MinIO와 OpenAPI 43/30을 검증했다.
  - 최초 Validator `NEEDS_CHANGES`의 Document resource filter를 한 차례 보정한 뒤 최종 read-only 판정은 `PASS`다.

- Next steps:
  - P4 Backend는 완료됐으며 P5 이후 domain과 P6 전체 RAG를 선행 구현하지 않는다.

## [2026-07-19] Session Summary (P3 Agent Run·AI runtime Backend 구현)

- What was done:
  - Agent Run·Step domain과 application port, V4 11개 table, claim·lease·reconciliation·bounded executor를 구현했다.
  - budget reserve·top-up·settle·release, retry lineage·idempotency, cancel·compensation과 owner-scoped API·SSE를 구현했다.
  - canonical workflow registry, no-network gateway와 test-only Fake 3-step orchestration을 추가했다.

- Key decisions:
  - PostgreSQL을 상태 원천으로 두고 AI workflow는 Agent Run repository를 직접 참조하지 않는다.
  - 실제 provider·가격 seed·production Fake endpoint를 만들지 않고 provider 기본값을 `none`으로 유지한다.
  - typed resource FK와 실제 domain apply는 P4 이후 forward migration 경계다.

- Issues encountered:
  - 기존 개발 DB는 Flyway 이력 없이 Session table이 남은 상태라 수정하지 않고 Testcontainers만 사용했다.
  - SSE owner 404 content negotiation과 V4 owner FK를 반영하지 않은 기존 test fixture를 안전하게 보정했다.
  - 최초 read-only Validator는 SSE owner 404 공통 오류 본문과 장시간 gateway 호출 중 heartbeat 부재를 MAJOR로 판정했다. 허용된 한 차례 보정에서 6-field JSON 404와 별도 scheduler 기반 주기 lease 갱신을 추가했다.

- Validation:
  - 루트 강제 `check --rerun-tasks`에서 21 suites/243 tests가 failure·error·skip 0으로 통과했다.
  - V1~V3 hash 불변, V4 빈 DB·V1/V2/V3 upgrade, OpenAPI 35/24와 provider 호출 부재를 검증했다.
  - 한 차례 read-only 재검증은 SSE 공통 404와 호출 중 heartbeat 보정을 확인하고 BLOCKER·MAJOR·MINOR 없이 `PASS`했다.

- Next steps:
  - P3는 완료됐으며 P4/P5에서 실제 resource owner·typed FK·workflow contribution과 provider별 timeout을 연결한다.

## [2026-07-19] Session Summary (P2 프로필·직접 입력 근거 Backend 구현)

- What was done:
  - 기본 프로필, 학력·자격증·어학·수상·경력 CRUD와 direct evidence 조회·편집·검토 API를 구현했다.
  - owner-scoped JDBC, optimistic version, 대표 학력·날짜·GPA·배열·metadata 불변식과 source/evidence transaction 동기화를 구현했다.
  - V3 migration과 domain·API·migration PostgreSQL 테스트를 추가하고 P1 인증·Session·idempotency 회귀를 유지했다.

- Key decisions:
  - profile completion 다섯 항목은 서버가 read 시 계산하며 미완료 상태를 기능 차단에 사용하지 않는다.
  - 구조화 source를 source of truth로 두고 수정 시 direct evidence를 재생성·`VERIFIED`로 되돌리며 삭제 시 source soft delete와 evidence delete를 같은 transaction으로 수행한다.
  - P2에서는 document DTO·nullable column만 유지하고 non-null 입력·filter는 404로 처리한다. documents table·복합 FK는 P4로 이관한다.

- Issues encountered:
  - migration test의 초기 PostgreSQL constraint message 기대 두 건을 실제 먼저 발생하는 제약에 맞춰 보정했다.
  - 기존 compose 개발 DB는 Flyway 이력 없이 Session table이 남아 있어 E2E boot가 실패했다. 기존 DB를 수정하지 않고 별도 빈 DB를 사용했다.

- Validation:
  - `Set-Location backend; .\\gradlew.bat check`에서 9개 test class, 54개 test가 failure·error·skip 0으로 통과했다.
  - 빈 DB V1→V2→V3, V1-only·V2-only upgrade, V1·V2 hash와 P2 CHECK·unique·owner·rollback을 검증했다.
  - 실제 Chromium 흐름에서 가입·프로필 지속성·두 사용자 404·로그아웃/로그인을 통과했다.
  - 최종 read-only validator가 Backend 계약과 P1 회귀를 BLOCKER·MAJOR·MINOR 없이 `PASS`로 판정했다.

- Next steps:
  - P2는 완료 상태다.
  - P4에서 documents table과 owner 복합 FK를 새 forward migration으로 추가한다.

## [2026-07-19] Session Summary (P1 인증 API Swagger 문서·UI 시험 보강)

- What was done:
  - 인증 Controller·DTO의 Swagger operation metadata, 안전한 example과 응답 schema를 보강했다.
  - 공통 OpenAPI 설정과 `sessionCookie`·`csrfToken` scheme, Swagger UI Try It Out 설정을 추가했다.
  - 생성 OpenAPI metadata·security 의미와 익명 Swagger UI 접근을 통합 테스트로 확장했다.

- Key decisions:
  - production Controller는 기존 다섯 개만 유지하고 API·DB·Session·CSRF runtime 계약은 변경하지 않았다.
  - logout의 Session+CSRF는 한 security requirement 객체의 AND이고 signup/login은 CSRF, me는 Session requirement만 사용한다.

- Issues encountered:
  - annotation을 개별 나열하면 security requirement가 OR 배열로 생성될 수 있어 logout에 최소 customizer를 사용했다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 33 tests, failure/error/skip 0으로 통과했다.
  - `git diff --check -- backend`, 정확히 다섯 production mapping과 migration 무변경 검사가 통과했다.

- Next steps:
  - 새 Controller를 추가할 때 OpenAPI operationId·status·schema·security와 Swagger UI 회귀 테스트를 같은 변경에 포함한다.

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
