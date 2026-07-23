# Progress

## Overview

- 초기 프론트엔드, 백엔드, Docker Compose, CI 환경이 구성되어 있다.
- 제품 기능·API·DB·화면·기술 명세는 P0 승인 기준선으로 `docs/spec/`에 존재한다.
- P1 공통 HTTP·Session·CSRF·인증·idempotency 기반과 P2 사용자 소유 프로필·direct evidence가 구현되어 있다.
- P3 PostgreSQL Agent Run 수명주기, 고정 Fake workflow, 비용 예약·정산, SSE와 Frontend 복구 기반이 최종 validator `PASS`로 완료됐다.
- P4 Document upload·parse·storage·Fake AI 근거 pipeline과 Frontend 목록·상세·검토가 최종 validator `PASS`로 완료됐다.
- 공개 Spring/OpenAPI는 인증 5개, 프로필·evidence 25개, Agent Run 5개, Document 8개인 총 43 operations·30 paths다.
- Dashboard·공고·P6 전체 RAG와 실제 provider는 아직 없다.

## [2026-07-23] Session Summary (backend package·디렉터리 구조 세분화)

- What was done:
  - 운영 Java 158개와 package-private 결합 테스트 4개를 44개 실제 책임 package로 이동하고 규칙·설계·계층형 추적 문서를 동기화했다.

- Key decisions:
  - `ProfileController`와 package-private `ProfileDtoMapper`는 접근 제한자를 바꾸지 않고 기존 package에 유지했다.
  - `common`·`ai` 전문 경계와 P5 이후 미구현 상태, API·DB·workflow 계약을 유지했다.

- Issues encountered:
  - 중간 감사에서 한국어 literal/comment 19개의 인코딩 손상을 발견해 HEAD UTF-8 원문을 복원한 뒤 구조 변경만 재적용했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P4 문서·근거 Pipeline 통합 구현)

- What was done:
  - 단일 V5, Document 공개 API 8개, parser·masker·chunker, MinIO 호환 Object Storage와 deletion outbox를 구현했다.
  - P3 Agent Run에 authoritative typed Document link를 연결하고 Fake 1536 embedding·structured evidence extraction의 고정 8단계 workflow를 구현했다.
  - Frontend `/documents` 목록·상세, P2 증빙 문서 selector, SSE invalidation과 실제 Backend Playwright 4개 시나리오를 연결했다.

- Key decisions:
  - parse와 evidence extraction 상태를 분리하고 parser 성공 뒤 AI 실패에는 text·chunk와 `PARSED`를 보존한다.
  - `agent_run_resource_links`를 authoritative source로 사용하되 V4 legacy projection parity를 deferred DB trigger로 강제한다.
  - production provider 기본값은 계속 `none`이며 Fake embedding·Chat·price catalog는 test scope에만 존재한다.
  - Object 준비 뒤 Document·Run·budget·typed link·idempotency 완료 응답은 한 DB transaction에서 커밋하고 실패 시 Object를 보상한다.

- Issues encountered:
  - 격리 Browser E2E의 Frontend 고정 port와 Vite 인자 전달을 random validated port로 바꿨다.
  - Fake usage의 불완전한 price pair 때문에 AI 단계가 안전 실패한 문제를 immutable Chat·Embedding price item seed와 참조로 해결했다.
  - 기존 개발 DB에는 Flyway V1과 과거 Spring Session table만 있어 수정·repair·삭제하지 않고 모든 migration/E2E를 격리 DB에서 수행했다.
  - 최초 read-only Validator가 Agent Run 목록의 Document resource filter가 P3 예약 404에 막힌 점을 `NEEDS_CHANGES`로 판정해 active owner resolver와 실제 Document 성공·격리·삭제 테스트를 허용된 한 차례 보정했다.

- Validation:
  - Backend 30 suites/287 tests, Frontend 26 files/95 tests와 production build, Compose, 실제 P4 Playwright 4/4가 통과했다.
  - OpenAPI 43 operations/30 paths, V1–V4 SHA-256 불변, 단일 V5·`vector(1536)`·HNSW/P5 table 부재를 검증했다.
  - 최초 read-only Validator는 Document resource filter를 `NEEDS_CHANGES`로 판정했고 한 차례 보정 후 최종 판정은 finding 없이 `PASS`다.
  - 최종 Validator 전후 status/content 207개 snapshot SHA-256은 각각 `18e76431e70324441471d5e126bc64b377486791c3d901ac232ac9f581ef1648`, `3c30406c5bbc475e85cc96e5e0b5759e6725207d957fa9f5fe3bc7d2a7b82597`로 일치했다.

- Next steps:
  - P4는 AC-03 완료다. P5–P10은 미착수이며 P6 전체 RAG와 실제 provider는 P4에 포함하지 않는다.

## [2026-07-19] Session Summary (P3 Agent Run·AI runtime 기반 통합 구현)

- What was done:
  - V1·V2·V3를 보존하고 Agent Run·Step, immutable AI 정책·가격, 사용자 preference, budget ledger·reservation·usage의 11개 table을 단일 V4 migration으로 추가했다.
  - Backend에 owner-scoped 5개 Agent Run API, 상태 전이, 내부 launcher·checkpoint·apply 경계, DB claim·lease·reconciliation, bounded executor, retry·cancel·비용·SSE 기반을 구현했다.
  - AI 모듈에 canonical 8개 workflow definition과 실행 contribution 분리, 고정 orchestrator, Context/Model/Prompt/Structured Output 계약, disabled gateway와 test-scope Fake 3-step workflow를 구현했다.
  - Frontend에 lazy Agent Run 목록·상세 route, repeatable filter, 상태 timeline, retry·cancel, progress drawer와 snapshot-first SSE·1/2/5초 재연결·5초 polling 복구를 구현했다.

- Key decisions:
  - PostgreSQL과 REST snapshot을 상태 원천으로 유지하고 SSE는 commit 뒤 전달되는 비영속 projection으로만 사용한다.
  - P3에는 실제 resource table이나 generic FK를 만들지 않아 Fake Run의 resource pair는 모두 null이며 typed resource link와 실제 domain apply는 P4 이후 forward migration으로 넘긴다.
  - 외부 가격은 migration에 고정하지 않고 테스트 fixture의 immutable price version/item으로 비용 동시성·정산을 검증하며 production gateway는 `none`으로 비활성화한다.
  - 명세의 reconnect 값은 1초·2초·5초의 총 3회 재연결 뒤 5초 REST polling으로 해석하고, 이번 threshold에서는 10초·30초 값을 사용하지 않는다.
  - P3는 AC-13의 공통 기반만 완료하며 Dashboard·공개 설정·전체 운영 hardening은 P10에 남긴다.

- Issues encountered:
  - 기존 개발 DB에는 Spring Session table과 불일치하는 Flyway history가 남아 있어 repair·drop·volume 삭제 없이 유지했고 P3 migration·동시성 검증은 Testcontainers의 격리 PostgreSQL에서 수행했다.
  - AI workflow 구현 에이전트의 첫 실행이 장시간 진전 없이 멈춰 같은 에이전트를 중단·범위 보정 후 허용된 두 번째 라운드로 완료했으며 추가 역할 재생성은 하지 않았다.
  - 최초 read-only Validator는 SSE 타 사용자 404의 빈 본문과 blocking gateway 호출 중 주기 heartbeat 부재를 MAJOR로 판정했다. 허용된 한 차례 보정에서 공통 6-field JSON 404와 별도 scheduler 기반 호출 중 lease 갱신을 추가했다.
  - P3 브라우저 흐름은 production 실행 endpoint를 만들지 않고 Playwright test-local REST/SSE fixture로 검증했다. 실제 typed resource를 포함한 cross-stack retry/apply는 P4 경계다.

- Validation:
  - 수정 전 P2 gate에서 Backend 54개, Frontend 57개 test, Compose와 격리 PostgreSQL 기반 실제 Chromium P2 E2E 1개가 통과했다.
  - 통합 Backend `check --rerun-tasks`가 21 suite·243개 test, 실패·오류·skip 0으로 통과했고 OpenAPI는 35 operation·24 path다.
  - Frontend `check`가 20 test file·78개 test로 통과했고 P3 Playwright test-local REST/SSE 2개 Chromium 시나리오가 통과했다.
  - V1·V2·V3 blob·SHA-256은 기준선과 같고 실제 AI·검색·embedding provider 호출, production Fake 실행 endpoint, P4 이후 table·endpoint는 없다.
  - 보정 후 전체 재실행에서 Backend `check --rerun-tasks` 243개 test, Frontend `check` 78개 test·production build, P3 Chromium 2개 시나리오, Compose와 `git diff --check`가 모두 통과했다. read-only 재판정만 남아 있다.
  - 두 Validator 보완의 직접 통합 테스트는 실제 PostgreSQL에서 통과했고 전체 Backend 수는 21 suite·243 tests로 확정됐다.
  - 한 차례 read-only 재검증은 BLOCKER·MAJOR·MINOR 없이 `PASS`였고, 전후 81개 status line과 258개 content file snapshot SHA-256이 각각 `2bc29bc68cd65fdb3d21a5b0e8bfb621ec339d077a47be45e999884ec321a21b`, `1b5d49a25888bbcb660b1582760c8223ff0c7f0243935ba7fbf69ee45c34369d`로 일치했다.

- Next steps:
  - P3는 완료됐으며 P4·P5에서 typed Agent Run resource FK, 실제 resource owner resolution·domain apply와 provider별 timeout을 각 aggregate의 forward migration·workflow에서 검증한다.

## [2026-07-19] Session Summary (P2 프로필·직접 입력 근거 통합 구현)

- What was done:
  - Backend V3 migration, `profile` 4계층, 25개 operation과 54개 전체 테스트를 구현했다.
  - Frontend profile typed API·feature·page·route·onboarding과 57개 전체 테스트, 실제 Chromium E2E를 구현했다.
  - 구현 계획을 P0·P1 완료, P2 검증 진행, P3–P10 미착수의 실제 상태로 보정했다.

- Key decisions:
  - completion 다섯 항목은 서버 원천이고 각 20%이며 profile incomplete는 hard gate가 아니다.
  - 구조화 source와 direct evidence는 같은 transaction에서 1:1·owner 일치로 동기화하고 source 수정 결과가 evidence 별도 편집보다 우선한다.
  - P2 document ID field·nullable column은 유지하지만 documents table·FK·UI는 만들지 않고 non-null 입력·filter는 404로 처리한다.

- Issues encountered:
  - Backend migration test의 PostgreSQL 제약 message 기대를 실제 발생 순서에 맞춰 보정했다.
  - 기존 개발 DB의 Flyway 이력 불일치 때문에 기존 데이터를 건드리지 않고 E2E 전용 빈 DB를 생성·검증 후 제거했다.
  - Playwright 시작 전 Windows pnpm 탐색과 첫 실제 실행의 중복 text locator를 각각 최소 보정했다.
  - 첫 최종 Frontend check가 Playwright spec을 Vitest로 수집해 실패해 Vitest 기본 exclude에 `e2e/**`를 추가했다.

- Validation:
  - P1 기준선에서 Backend 33개, Frontend 35개 test와 Compose 검증이 먼저 통과했다.
  - P2 Backend check 54개 test, Frontend check 57개 test, Compose, 빈 DB/V1/V2 upgrade와 실제 Chromium E2E 1개가 통과했다.
  - V1·V2 Git blob과 SHA-256은 수정 전 기준선과 일치하며 실제 유료 AI·검색 provider는 비활성 상태다.
  - 최종 read-only validator가 BLOCKER·MAJOR·MINOR 없이 `PASS`로 판정했다. validator 전후 135개 파일의 status·content snapshot SHA-256 `33b4b8df02524ce56c1ba73dec519f78bd6d1c3fe7fb8ab0c9512b51c80314ee`가 일치했다.

- Next steps:
  - P2는 완료 상태다. P3 착수 전 P4 document FK 이관 경계와 P3–P10 미착수 상태를 유지한다.

## [2026-07-19] Session Summary (인증 Controller Swagger UI와 향후 OpenAPI 규칙 보강)

- What was done:
  - 인증 Controller와 DTO에 안정적 operationId, 응답 schema, Session·CSRF requirement와 validation을 통과하는 가짜 example을 추가했다.
  - 공통 OpenAPI 설정에 API info와 `sessionCookie`·`csrfToken` scheme를 정의하고 Swagger UI Try It Out을 활성화했다.
  - 향후 Controller가 같은 Swagger 문서·시험 계약을 유지하도록 백엔드 개발 규칙과 규칙 인덱스를 갱신했다.

- Key decisions:
  - 기존 다섯 API의 path·status·DTO·인증 runtime은 바꾸지 않고 문서 metadata와 계약 테스트만 확장했다.
  - logout의 Session과 CSRF는 OpenAPI의 같은 security requirement 객체에 넣어 AND로 표현한다.
  - 현재 CSRF token은 JSON으로 반환되므로 Springdoc의 Cookie·storage 기반 CSRF 자동화는 켜지 않고, UI에서 bootstrap 응답 token을 `csrfToken` Authorize 값으로 입력한다.

- Issues encountered:
  - OpenAPI security 배열의 별도 requirement 객체는 AND가 아니라 OR이므로 annotation 두 개 나열만으로 logout 계약을 표현할 수 없었다.
  - 최소 OpenAPI customizer로 logout의 두 scheme를 한 객체에 합치고 생성 JSON을 테스트로 고정했다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 33개 테스트, 실패·오류·skip 0으로 통과했다.
  - OpenAPI가 정확히 다섯 path, 두 security scheme, operation별 requirement와 직접 DTO schema만 생성하는지 검증했다.
  - 익명 `/swagger-ui.html` 접근, HTML 로딩, `tryItOutEnabled=true`와 내장 CSRF 자동화 미설정을 통합 테스트로 확인했다.
  - read-only validator가 BLOCKER·MAJOR 없이 `PASS`로 판정했고 validator 전후 23개 변경 파일 snapshot이 일치했다.

- Next steps:
  - 후속 Controller는 `backend-development.md`의 operationId·response·security·example·Swagger UI 회귀 규칙을 함께 적용한다.
  - 운영 환경에서 Swagger/OpenAPI 노출 여부는 배포 보안 정책을 승인한 뒤 별도로 제어한다.

## [2026-07-19] Session Summary (P1 공통 HTTP·인증·테스트 기반 구현)

- What was done:
  - 백엔드에 공통 오류 계약과 request ID, Spring Security Session·CSRF, 사용자·기본 프로필, 정확히 다섯 인증 API와 durable idempotency 기반을 구현했다.
  - 프론트엔드에 typed API client, 세 단계 인증 상태, signup·login·logout 흐름, 안전한 `returnTo`, public-only/auth-required guard와 shell route를 구현했다.
  - P1 schema를 새 V2 Flyway migration으로 추가하고 백엔드 26개 및 프론트엔드 35개 테스트로 계약을 검증했다.

- Key decisions:
  - 성공 응답 envelope와 미래 endpoint·DTO·table은 만들지 않고 P1의 다섯 인증 endpoint와 `users`, `user_profiles`, JDBC Session, `idempotency_records`만 구현했다.
  - 인증 API에는 Idempotency-Key를 적용하지 않았고, 만료 row의 원자 reclaim과 linked IN_PROGRESS 보호를 포함한 최소 저장·hash·replay 구조를 test-source fixture로 검증했다.
  - Spring Session JDBC는 공식 named transaction extension을 `REQUIRED`, flush mode를 `IMMEDIATE`로 구성해 signup의 user·profile·Session SQL을 같은 transaction에 참여시킨다.
  - `/onboarding`과 `/dashboard`는 route·layout 검증용 shell로 제한하고 프로필 저장·대시보드 집계·AI provider 연동을 포함하지 않았다.

- Issues encountered:
  - Spring Boot 4.1의 Jackson 3 타입, 기본 XOR CSRF handler와 raw token 응답, PostgreSQL의 시간 타입 추론 문제를 실제 통합 테스트에서 확인해 호환되는 설정과 타입으로 보정했다.
  - 서버 오류 후 disabled 입력에 focus하려던 프론트엔드 Form 문제를 component test로 발견해 제출 상태 해제 후 focus하도록 수정했다.
  - 사전 점검 시 Docker daemon이 정지 상태여서 Docker Desktop을 숨김 실행한 뒤 Compose와 Testcontainers 검증을 진행했다.
  - 1차 validator는 idempotency TTL이 만료 동작을 수행하지 않고 signup Session 저장이 JPA transaction과 분리된 두 문제를 MAJOR로 판정해 `FAIL`했다.
  - 허용된 한 차례 보정에서 조건부 upsert와 Session transaction 참여·실패 시 재저장 차단을 구현했고, 보정 중 테스트 실패나 추가 자동 수정은 없었다.

- Validation:
  - 보정 에이전트와 루트에서 `backend\\gradlew.bat check`가 최종 31개 테스트와 함께, 구현 에이전트와 루트에서 `corepack pnpm check`가 7개 파일 35개 Vitest 및 production build와 함께 통과했다.
  - 빈 DB V1→V2 전체 적용, V1-only DB upgrade, constraint·index·unique와 JPA validate가 Testcontainers 기반 migration test에서 통과했다.
  - 만료 replay 차단·동시 reclaim·linked IN_PROGRESS 보호와 Session 저장 실패·deferred commit 실패의 원자성 회귀 테스트가 통과했다.
  - Compose 해석, `git diff --check`, V1 hash, 관리 문서·상대 링크 정적 검사가 통과했고, 2차 독립 validator가 두 MAJOR 해소와 전체 P1 회귀를 BLOCKER·MAJOR·MINOR 없이 `PASS`로 판정했다.
  - 실제 외부 유료 provider 호출, commit, push, 배포는 수행하지 않았다.

- Next steps:
  - P2 착수 전 운영 환경의 Session Cookie 속성과 idempotency HMAC secret을 안전하게 주입하고, 첫 실제 idempotent resource endpoint에서 transaction 경계를 연결한다.
  - 실제 resource owner 404, 프로필 온보딩 저장과 Dashboard 데이터는 승인된 P2 범위에서 구현한다.
  - P2 profile mutation 전에 `user_profiles` JSON 배열의 최대 10개·중복 금지·항목 길이 DB 제약을 새 forward migration으로 추가한다.

## [2026-07-18] Session Summary (P0 제품 계약 기준선 승인 반영 완료)

- What was done:
  - 승인된 8개 제품 정책과 제안서의 D-01–D-18을 다섯 기준 명세에 통합하고 설계·계획·진행 문서의 상태를 동기화했다.
  - backend·ai_workflow·frontend의 읽기 전용 분석을 루트에서 통합하고 새로운 read-only validator로 계약 기준선을 독립 검증했다.

- Key decisions:
  - `docs/spec/**`만 활성 제품 계약이며 proposal은 `APPROVED_DECISION_RECORD`로 승인 과정과 근거를 보존한다.
  - P0 계약 기준선은 완료됐지만 P1은 미착수다. Java·TypeScript·Vue, Flyway, dependency·설정·Compose 구현은 이번 범위에 포함하지 않았다.

- Issues encountered:
  - 공고 수동 본문의 동기/비동기 응답 분기, mock 실패 replay, evidence tombstone read-only, Agent retry identity와 DB 상한을 명세 전체에서 일치시켜야 했다.
  - `index.md` 범위 기호가 Markdown 취소선으로 포맷되는 문제는 en dash로 교체해 해결했다.

- Validation:
  - validator가 승인 정책 8개, D 18개, Gate 16개, canonical enum, 97 endpoint, owner·idempotency·quality·embedding과 공개 DTO 경계를 `PASS`로 판정했다.
  - Markdown 표·상대 링크·enum·endpoint·field bound·상태 전이·allowlist 검사와 Prettier, `git diff --check`, 변경 범위 검사를 수행했다.
  - 문서 전용 작업이라 backend/frontend build를 실행하지 않았고 외부 유료 API, commit, push, 배포를 수행하지 않았다.

- Next steps:
  - P1에서 공통 HTTP 오류·Session·CSRF·request ID·idempotency와 테스트 기반을 구현하고, 목표 DB 계약은 새 Flyway migration으로 단계적으로 검증한다.

## [2026-07-18] Session Summary (P0 계약 제안서 제품 검토 준비 전환)

- What was done:
  - 승인 전 P0 제안서를 수정 전·후 독립 validator로 감사하고, 구현자가 추측하거나 미승인 정책을 확정하지 않도록 계약을 정합화했다.
  - 최종 의미 검증 `PASS`에 따라 상태를 `READY_FOR_OWNER_REVIEW`로 변경하고 설계·문서·루트 추적 기록을 갱신했다.

- Key decisions:
  - D 항목은 권장 10개·제품 승인 필요 8개이며 제품 질문도 8개다.
  - 회원 탈퇴 replay 제거, mock feedback 품질 고정, 성공 feedback만 저장, embedding과 profile 완료의 승인 전 구현 차단을 채택했다.
  - P0는 아직 승인·완료가 아니며 승인 후 `docs/spec/**` 동기화와 재검증이 필요하다.

- Issues encountered:
  - 최초 validator는 4 BLOCKER와 URL·memo·source·취소·공개 DTO 경계 등 MAJOR를 포함해 `NEEDS_CHANGES`로 판정했다.
  - 한 차례 보정 후 새 validator가 승인 차단 충돌 없음으로 `PASS`했다.

- Validation:
  - D-01~~D-18과 Gate A~~C, enum/상태, request-response-DB 상한, quality/idempotency, cancel/retry, 사용자 격리·provenance를 의미·기계적으로 검사했다.
  - Markdown Prettier와 `git diff --check`를 실행했다. 코드·migration·설정·`docs/spec/**`는 변경하지 않았고 문서 전용이라 backend/frontend build를 실행하지 않았다.

- Next steps:
  - 제품 소유자가 8개 승인 질문을 검토한 뒤 승인된 결정을 기준 명세에 반영하고 P0 완료 여부를 판단한다.

## [2026-07-18] Session Summary (P0 계약 결정 제안과 구현 차단 항목 정리)

- What was done:
  - 필수 작업 규칙·기준 명세·설계·구현 계획과 현재 backend/frontend/infrastructure bootstrap을 확인하고, D-01–D-18과 Gate A–C의 승인 전 계약 제안서를 작성했다.
  - backend·ai_workflow·frontend의 읽기 전용 병렬 분석을 통합해 상태·enum, 전체 API projection, tenant·수명주기, AI runtime, route·UX 기준선과 제품 질문 6개를 확정 제안으로 정리했다.
  - 설계 index·progress와 루트·docs progress를 갱신하고 기존 설계 문서의 링크, 깨진 소유권 표와 범위 표기를 정리했다.

- Key decisions:
  - D-01~D-18은 `RECOMMENDED` 11개, `OWNER_DECISION_REQUIRED` 7개이며 사용자 승인 전 P0는 미완료 상태다.
  - 단일 Spring Boot·PostgreSQL·S3 호환 storage, REST snapshot 원천, 유한 AI workflow, 사용자 복합 소유권과 provenance·중복 비용 방지를 유지한다.
  - 회원 탈퇴 삭제 task는 Agent Run·user FK에서 분리하고, 공개 품질·내부 모델 tier·검색 품질은 별도 타입으로 고정했다.

- Issues encountered:
  - 1차 validator의 4개 계약 차단점은 1회 보정 뒤 2차 validator가 해소를 확인했다.
  - 2차 validator가 추가 DTO 상한·연구 출처 enum·path 표기 불일치를 발견해 `NEEDS_CHANGES`로 종료했으며, 루트가 해당 불일치를 최종 정합화했다.
  - 동일 역할 검증 상한에 따라 세 번째 validator를 실행하지 않았으므로 마지막 루트 보정분은 독립 validator 미검증으로 남는다.

- Validation:
  - 세 분석 에이전트는 모두 `DONE`·파일 변경 없음, validator는 두 번 모두 read-only·파일 변경 없음으로 종료했다.
  - 최종 루트 검사에서 D 18행(11/7), Gate A~C, 기준 API 95개 누락 0, 필수 타입 18개, 질문 6개, Markdown 표·링크, Prettier와 `git diff --check`를 통과시켰다.
  - 비즈니스 코드·테스트·dependency·migration·설정, `docs/spec/**`를 변경하지 않았고 commit·push·배포·외부 유료 API 호출을 수행하지 않았다.

- Next steps:
  - 제품 소유자가 6개 질문과 제안 전체를 승인·수정한 후 기준 명세를 동기화하고, 독립 계약 검증을 다시 통과시킨 뒤 P1 구현을 시작한다.

## [2026-07-18] Session Summary (Hiresemble 전체 시스템 설계와 단계별 구현 계획 수립)

- What was done:
  - `AGENTS.md`와 `docs/spec/`의 Markdown 7개를 모두 읽고 프로젝트 목적, MVP, 모듈·도메인 의존, 기능·DB·API·페이지 연결을 통합했다.
  - 문서·공고·자기소개서·면접과 Agent Orchestrator·Model Router·Context Builder·Budget Guard의 실행 흐름, 인증·격리·개인정보와 비동기·복구·SSE 설계를 작성했다.
  - `docs/design/`의 전체 시스템 설계, 구현 계획과 추적 문서를 만들고 루트·문서 영역 인덱스를 갱신했다.
  - P0~P10 구현 순서, 완료 조건과 backend·AI workflow·frontend·validator의 단일 파일 소유권을 정리했다.

- Key decisions:
  - 다섯 제품 명세를 변경하지 않고 파생 설계와 권장 해결안을 별도 문서로 관리한다.
  - 공개 계약·데이터 수명주기·AI 운영 정책의 미결 항목은 P0 결정 게이트 전 migration이나 API/UI로 구현하지 않는다.
  - 백엔드는 도메인·HTTP·persistence, AI workflow는 context·model·prompt·workflow, frontend는 UI·API consumer를 소유한다.

- Issues encountered:
  - 공고 상태 축, 품질·version·질문 enum, tenant DB 제약, 삭제·provenance, 멱등성·Agent Run 복구·SSE, 자기소개서 최종화·보관, 조사·모의 면접 lifecycle 등 18개 이슈 그룹을 확인했다.
  - 독립 validator가 보조 MVP 직접 추적 3건과 상위 진행 문서·format 보완을 요구해 허용된 한 차례 수정에 통합했다.

- Validation:
  - backend·AI workflow·frontend 분석 에이전트가 모두 `DONE`, 파일 변경 없음으로 종료했다.
  - 독립 validator는 사용자 요구 1~~15, AC-01~~13, 사용자 격리, 동기·비동기, 역할 경계와 링크를 통과시키고 세 보완점을 반환했다.
  - 보완 후 정적 검사에서 AC 13개, 필수 5필드를 가진 이슈 18개, 상대 링크와 `git diff --check`가 통과했다.
  - 변경 Markdown의 Prettier 검사가 통과했다. 비즈니스 코드·dependency·migration·API·UI를 변경하지 않아 backend/frontend build test는 실행하지 않았다.

- Next steps:
  - 구현 시작 전에 P0의 공개 API·상태, 데이터 수명주기, AI 비용·복구 정책을 사용자 승인으로 확정한다.

## [2026-07-18] Session Summary (Codex 멀티 에이전트 종료 안전성 보완 및 런타임 재검증)

- What was done:
  - 루트 `AGENTS.md`에 최대 2개 오케스트레이션 라운드, 역할별 생성 상한, 실패·Timeout 자동 재생성 금지, 최대 1회 수정-재검증과 명시적 종료 상태를 추가했다.
  - 세 구현 Agent에 보호 경로와 공유 계약 파일의 순차 소유 규칙을 직접 명시하고, 네 Agent에 서로 다른 런타임 식별 마커를 추가했다.
  - fresh read-only Codex 부모 세션 두 개에서 구현 역할 3개와 Validator 1개를 정확히 2개 라운드로 실행했다.

- Key decisions:
  - 기존 TOML의 필수 필드와 역할 경계가 유효하므로 전체 재작성과 `ai-workflow.toml` 파일명 변경은 하지 않았다.
  - Spawn 이름이나 Agent 자기 선언만으로 custom developer instruction 주입을 확정하지 않고, 전용 마커 또는 동등한 런타임 증거가 없으면 `NOT_VERIFIED`로 판정한다.
  - 디렉터리 구조와 책임은 유지되어 `index.md`는 변경하지 않았다.

- Issues encountered:
  - `/root/backend`, `/root/ai_workflow`, `/root/frontend`, `/root/validator` Spawn 이름은 확인됐지만 네 역할 모두 전용 마커를 반환하지 못해 실제 custom profile 주입은 `NOT_VERIFIED`다.
  - `codex --strict-config features list` 조합은 현재 0.144.5에서 지원되지 않아 반복하지 않고 일반 feature 조회와 Doctor·fresh exec로 대체했다.
  - Codex 실행 package와 npm update 대상 불일치 및 기존 rollout scan 경고는 재현됐으며 프로젝트 설정 문제와 분리했다.

- Validation:
  - Python `tomllib` 검사로 프로젝트 config와 Agent TOML 4개의 문법, 필수 필드, 이름·마커 유일성, `max_threads=4`, `max_depth=1`, 보호 경로와 Validator read-only 설정을 확인했다.
  - 한글·영문 무제한 반복 문구 검색 결과 위험 문구 그룹은 0개였다.
  - Round 1은 read-only 부모에서 구현 Agent 3개, Round 2는 별도 read-only 부모에서 Validator 1개를 생성했으며 하위 생성·재시도·자동 수정은 모두 0이었다.
  - 스모크 전후 변경 파일 5개와 diff hash `13e98e88530fec4932ecbe6b4cdadb85ce999195`가 동일했고 `git diff --check`가 통과해 Agent에 의한 파일 변경이 없음을 확인했다.

- Next steps:
  - custom Agent 선택 이름과 developer instruction layer를 직접 노출하는 Codex 런타임 메타데이터가 제공될 때 동일 마커 검증을 역할별 1회로 다시 수행한다.
  - 로컬 Codex 설치 경로와 rollout 경고는 프로젝트 Agent 설정과 별도의 수동 환경 정비 작업으로 처리한다.

## [2026-07-17] Session Summary (Codex 서브 에이전트 및 진행 이력 운영 표준화)

- What was done:
  - 기준 `query-forge/progress.md`를 직접 분석하고 관리 대상 기존 `progress.md` 21개의 모든 상태·결정·문제·검증·후속 기록을 표준 Session 구조로 재배치했다.
  - `AGENTS.md`, 공통 workflow와 문서 추적 규칙에 역할별 최신 5개 조회, 제한된 과거 검색, 루트 관리자 책임, 파일 소유권, 순차·병렬 위임과 구조화 Handoff 규칙을 통합했다.
  - 프로젝트 `.codex/config.toml`에 `agents.max_threads = 4`, `agents.max_depth = 1`을 추가하고 `backend`, `ai_workflow`, `frontend`, `validator` 커스텀 역할을 구성했다.
  - 사용자 전역 설정에는 이 프로젝트의 trust 항목만 추가해 프로젝트 로컬 설정과 역할을 실제 로드할 수 있게 했다. 모델·provider·인증·권한·MCP·플러그인 설정은 변경하지 않았다.

- Key decisions:
  - 기준 파일의 Session 제목과 상단 최신 기록 배치를 채택하되, 중간 Overview/Notes, 필드 누락과 날짜 역전은 복제하지 않고 사용자 요청의 엄격한 표준을 적용했다.
  - 세션 분할 근거가 없는 기존 상태 문서는 원래 수정일의 단일 초기화 Session으로 옮겨 의미를 보존했다.
  - 현재 사용자 요청을 받은 루트 Codex 스레드를 관리자이자 문서 통합 책임자로 유지하고 별도 `manager.toml`은 만들지 않았다.
  - 구현 역할은 부모 모델·권한을 상속하며 검증 역할만 read-only 기본값을 갖는다. 다만 부모의 실시간 권한 override가 우선할 수 있어 검증 전후 diff 확인을 함께 요구한다.

- Issues encountered:
  - 설치된 Codex 0.144.5에는 `codex status` 서브명령이 없어 `codex doctor`와 실제 read-only 실행으로 대체했다.
  - `codex doctor`는 프로젝트 설정 파싱과 Root를 정상 확인했지만 JetBrains/npx 실행 package와 npm global package 경로 불일치 및 기존 rollout scan 경고 때문에 전체 종료 상태는 실패다.
  - `codex exec --ephemeral`에서 첫 subagent 생성이 루트 thread ID를 찾지 못하는 오류가 재현됐다. 일반 read-only `codex exec`에서는 `backend`가 정상 로드됐고 나머지 세 역할도 실제 로드 응답을 확인했다.

- Validation:
  - Python 정적 검사로 관리 대상 `progress.md` 22개의 H1, 단일 Overview, 제목 패턴, 다섯 필드와 최신순을 확인했다. 기존 21개 문서의 168개 legacy 섹션이 현재 문서에 보존됐음을 Git HEAD와 대조했다.
  - Python `tomllib`로 프로젝트 config와 Agent TOML 4개의 구문, 필수 필드, `[agents]` 값과 validator read-only를 확인했다.
  - `codex --strict-config ... doctor --summary`에서 config와 repository Root 로드를 확인하고, read-only Codex 세션에서 루트 `AGENTS.md` 및 네 custom agent 이름을 실제로 확인했다.
  - 변경 Markdown 전체의 Prettier, 상대 링크 314개, `git diff --check`와 최종 Git 변경 범위 검사가 통과했다. 변경은 Markdown과 TOML에 한정되고 비즈니스 코드 변경은 없다.
  - 독립 검증 에이전트가 22개 문서 형식, 기존 실질 기록 405/405줄 보존, Agent 설정·실제 로드와 무수정 범위를 재확인해 `PASS WITH WARNINGS`로 판정했다. 경고는 프로젝트 밖의 Codex 로컬 환경 문제에 한정된다.

- Next steps:
  - 향후 개발 요청부터 파일 소유권을 분리해 네 전문 역할을 선택하고 루트 관리자가 결과와 추적 문서를 통합한다.
  - Codex 설치 경로 불일치와 ephemeral subagent thread 오류는 프로젝트 설정과 별개인 로컬 CLI 후속 점검 대상으로 남긴다.

## [2026-07-17] Session Summary (초기 개발 환경 및 문서 체계 구축)

- What was done:
  - 당시 구현 상태:
    - 초기 프론트엔드, 백엔드, Docker Compose, CI 환경이 구성되어 있다.
    - 제품 기능·API·DB·화면·기술 명세는 `docs/spec/`에 존재한다.
    - 실제 비즈니스 Controller, 도메인 모델, 공통 응답·예외 처리 코드는 아직 구현되지 않았다.
    - Codex 설정, 작업 규칙과 21개 관리 대상 디렉터리의 문서 계층 구성이 완료됐다.
  - 완료된 작업:
    - Java 21/Spring Boot 4.1/Gradle 백엔드 초기 환경 구성
    - Vue 3/TypeScript/Vite/pnpm 프론트엔드 초기 환경 구성
    - PostgreSQL 18/pgvector, MinIO, 선택적 Mailpit Compose 구성
    - GitHub Actions CI, Dependabot, `.gitignore`, 환경 변수 예시 구성
    - 현재 저장소와 기존 AI 에이전트 설정 파일 조사
    - 레퍼런스 `orchestrator-module-hardening`의 공통 응답·예외 처리 구조 읽기 전용 분석
    - 루트 `AGENTS.md`, `index.md`, `progress.md` 최초 생성
    - `.codex/config.toml`과 공통·문서·백엔드·응답/예외·프론트엔드·인프라 규칙 6종 생성
    - 21개 관리 대상 디렉터리에 `index.md`, `progress.md` 42개 생성
    - 레퍼런스 분석 결과와 현재 API 계약을 조정한 응답·예외 예상 package 및 적용 규칙 확정
    - Git에서 해석하지 않는 `.gitattributes` brace 패턴을 명시적 확장자별 LF 규칙으로 교체
    - 초기 worktree를 repository 정책, 제품 명세, backend, frontend, infrastructure, CI, Codex 문서의 intent별 commit으로 분리
  - 당시 진행 중인 작업:
    없음. 이번 Codex 설정·문서화 범위는 완료됐으며 비즈니스 기능은 시작하지 않았다.

- Key decisions:
  - `AGENTS.md`를 유일한 자동 로드 진입점으로 사용하고 상세 규칙은 `docs/agent-rules/`에 둔다.
  - `.codex/rules`를 코딩 지침 저장소로 오용하지 않는다. 해당 경로는 Codex 명령 실행 정책용이므로 현재는 별도 명령 정책을 추가하지 않는다.
  - Spring 응답·예외 처리는 중앙 변환 구조를 채택하되 `docs/spec/api.md`의 응답 계약과 실제 HTTP 상태를 유지한다.
  - 이 작업에서는 비즈니스 코드를 추가하지 않고 예상 패키지 구조와 적용 규칙을 먼저 문서화한다.

- Issues encountered:
  - 현재 API 명세는 성공 DTO 직접 반환과 실제 HTTP 상태 코드를 요구하지만, 레퍼런스는 모든 응답을 `BaseResponseDto`로 감싸고 오류도 기본 HTTP 200으로 반환한다. 기존 API 명세를 우선하고 구조적 패턴만 적용하기로 했다.
  - 레퍼런스 `ErrorCode`에는 중복 번호와 이름 불일치가 있어 현재 프로젝트에 그대로 복사할 수 없다.
  - 원격 GitHub Actions 실행 이력과 branch protection 상태는 로컬 저장소만으로 확인할 수 없다.
  - `src/main/resources` 아래의 추적 Markdown은 현재 빌드 시 classpath 리소스에 포함될 수 있다. 운영 패키징 전에 제외 정책을 검토해야 한다.

- Validation:
  - 문서 생성 전 기존 파일·디렉터리와 AI 에이전트 설정 후보를 `rg --files -uu`, `git status --short`로 조사했다.
  - 레퍼런스 Java 소스와 현재 `docs/spec/api.md`를 직접 대조했다.
  - PowerShell 정적 검사로 21개 디렉터리, 추적 문서 42개, 필수 섹션, 55개 Markdown 파일의 상대 링크를 확인했다. 결과: 성공.
  - 통합 정적 검사 script는 작성 중 두 차례 PowerShell 구문 오류가 있었고 수정 후 `documentation_validation=PASS`를 확인했다.
  - 전체 신규 Markdown Prettier 최초 검사에서 30개 파일의 형식 차이가 발견됐다. 신규 문서에만 `--write`를 적용한 뒤 49개 파일 재검사가 통과했다.
  - 커밋 단위 검사에서 README/Compose와 GitHub YAML의 format 차이를 추가로 발견해 해당 파일만 Prettier로 정리한 뒤 재검사를 통과했다.
  - `Set-Location backend; .\gradlew.bat check`: 성공. Java test source는 아직 없다.
  - `Set-Location frontend; corepack pnpm check`: 성공. ESLint, Prettier, TypeScript, Vitest, production build가 통과했으나 Vitest test file은 없다.
  - `docker compose config --quiet`: 성공.
  - `codex features list`: 성공. 프로젝트 TOML 구문과 현재 trusted 환경의 설정 load를 확인했다.
  - `git check-attr text eol`로 Markdown, TypeScript, Kotlin DSL에 `text/eol=lf`가 적용되는지 확인했다.
  - `git-commit` workflow에 따라 각 단위의 staged name/status, stat, 전체 whitespace를 확인하고 AngularJS-style commit message로 순차 커밋했다.
  - 실제 GitHub-hosted CI와 Playwright E2E는 실행 이력/test file이 없어 미검증 상태다.

- Next steps:
  - 공통 오류 응답 및 예외 처리 구조 실제 구현과 테스트
  - 인증, 프로필, 문서, 공고, 자기소개서, 면접, Agent Run 기능 개발
  - 운영 배포 환경과 관찰성 구성
  - 원격 저장소의 branch protection과 PR 운영 정책 확정
