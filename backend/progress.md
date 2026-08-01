# Progress

## Overview

- Java 21, Spring Boot 4.1, Spring AI 2.0 기반 단일 애플리케이션의 초기 빌드 환경이 구성되어 있다.
- P1 인증부터 P8 Interview, 계정 닉네임 변경과 Agent Run history delete까지 총 84 operations/63 paths가 구현되어 있다.
- V1~V15 migration이 적용됐고 V14는 embedding provider key canonicalization, V15는 사용자 직접 대외활동을 소유한다.
- Backend 전체 69 suites/479 tests와 final-source actual P8~P4 wrapper가 통과했고 local은 실제 provider, local-offline/test는 network-disabled다.

## [2026-08-01] Session Summary (공고 charset·image extraction v2)

- What was done:
  - bounded raw HTML charset 탐지·strict decode와 JPEG/PNG image fetch, 별도 image text gateway, Job extraction v2 workflow를 구현했다.
  - semantic null·손상 문자·description source 품질과 manual override/owner/version apply 검증을 추가했다.
- Key decisions:
  - Spring AI 2.0 byte-backed media, strict schema, retry 0, store false를 사용하고 image usage를 기존 chat token price item으로 기록한다.
- Issues encountered:
  - legacy v1 definition 때문에 canonical coverage 테스트가 2건 실패해 canonical 필터와 v1 non-executable 격리 assertion으로 보정했다.
  - 최종 cache-free check에서 무관한 Interview timestamp 경계 테스트가 1회 간헐 실패했으나 단독 재현과 허용된 마지막 전체 재검증은 모두 통과했다.
- Validation:
  - `gradlew check --rerun-tasks --no-daemon --console=plain --max-workers=1`: 69 suites/479 tests, failure/error/skip 0. `p5BrowserE2eTest`: Chromium 5/5. 실제 Provider 호출 0회.
- Next steps:
  - 실제 Provider/외부 채용 사이트 live 호출은 별도 사용자 승인 후 수행한다.

## [2026-08-01] Session Summary (사용자 대외활동·소재 일괄 검토 계약)

- What was done:
  - `activities` V15, owner-scoped 대외활동 CRUD, `ACTIVITY` direct evidence projection, 소재 사용 여부와 일괄 검토 API, 문서 원본 파일명 응답을 추가했다.
  - 문서 삭제가 직접 등록 활동에 영향을 주지 않고 다른 사용자 접근이 차단되며 verified snapshot에 선택된 활동만 포함되는 통합·migration·OpenAPI 회귀를 추가했다.
- Key decisions:
  - 대외활동은 문서에 종속되지 않는 별도 aggregate/table로 소유하고, 자소서·면접 기존 verified evidence 조회 경계에는 상태 projection으로만 합류시켰다.
- Issues encountered:
  - 전체 check에서 Interview 테스트 fixture의 DB 시계와 애플리케이션 시계 경계가 드물게 역전되어 terminal timestamp를 `GREATEST(now(), started_at)`로 고정했다.
- Validation:
  - `gradlew check --rerun-tasks --no-daemon --console=plain --max-workers=1`: 69 suites/469 tests, failure/error/skip 0.
- Next steps:
  - None.

## [2026-08-01] Session Summary (Document partial rejection terminal 정책 보정)

- What was done:
  - 문서 apply의 가짜 failed scope를 제거하고 stable rejection 집계, workflow별 terminal policy와 자기소개서 partial failure 회귀를 구현했다.
- Key decisions:
  - 일부·전체 candidate rejection은 문서 성공이며 공용 Orchestrator는 업무별 safe code를 소유하지 않는다.
- Issues encountered:
  - 기존 terminal Run row는 audit 보존을 위해 자동 수정하지 않는다.
- Validation:
  - `gradlew check --no-daemon --console=plain --max-workers=1`: 68 suites/466 tests, 0 failures/errors/skips. 실제 Provider 호출 0회.
- Next steps:
  - 사용자 local에서 새 문서 Run terminal `SUCCEEDED`를 bounded 1회 확인한다.

## [2026-08-01] Session Summary (Structured output 의미 진단과 비용 재시도 보정)

- What was done:
  - 문서 Provider DTO v2·trusted mapper, phase별 safe code, repair-once guidance, finish reason과 usage 보존을 구현했다.
- Key decisions:
  - API/DB/migration은 유지하고 deterministic output failure는 1회, model-repairable semantic failure는 최대 2회로 제한한다.
- Issues encountered:
  - 전체 check 1차에서 통합 test fixture가 V13 catalog에 row를 추가해 migration test 1건을 오염시켰고, 기존 immutable item 참조로 수정했다.
- Validation:
  - 최종 `gradlew check --rerun-tasks`: 68 suites/459 tests, 0 failures/errors/skips. 외부 호출 0회.
- Next steps:
  - bounded live Chat·document 검증 전 상태를 성공으로 올리지 않는다.

## [2026-08-01] Session Summary (OpenAI strict Structured Output 호환성 보정)

- What was done:
  - 문서 근거 후보의 임의 `metadata` object를 Provider 전용 scalar entry output과 명시적 domain mapper로 분리하고 nullable warning 계약을 일치시켰다.
  - 전수 keyword 감사에서 발견한 P7 공개 TipTap DTO의 `default` schema 유입을 Provider 전용 recursive output과 bounded mapper로 분리했다.
  - 모든 등록 Chat output의 runtime schema를 중앙 생성·검증·fingerprint하고 Gateway가 그 schema를 그대로 보내도록 변경했다.
- Key decisions:
  - DB JSONB·공개 API metadata object·기존 저장 데이터는 변경하지 않고 schema 거절과 응답 검증 실패를 구분한다.
- Issues encountered:
  - 장애 당시 raw Provider code·param·request ID가 남지 않아 원인은 `HIGH_CONFIDENCE`이며 live 재검증 전 직접 확정하지 않는다.
- Validation:
  - focused 회귀와 `gradlew check` 68 suites/452 tests가 통과했고 실제 OpenAI/Tavily 호출은 0회다.
- Next steps:
  - persistent Chat cap의 versioned 1회 allowance가 별도 승인된 뒤 Chat capability와 문서 ingestion을 bounded 재검증한다.

## [2026-08-01] Session Summary (OpenAI local 연결 오류와 embedding 정책 보정)

- What was done:
  - OpenAI base URL `/v1`, 빈 tool option 비전송, status/code/param/request ID safe logging과 V14 embedding 정책 전환을 구현했다.
- Key decisions:
  - Provider key는 lowercase canonical 값을 사용하고 과거 정책은 update하지 않고 비활성 history로 보존한다.
- Issues encountered:
  - live Chat·Embedding은 OpenAI `insufficient_quota`로 실패했고 Tavily BASIC은 성공했다.
- Validation:
  - `gradlew check`: 67 suites/427 tests, failure/error/skip 0. local DB V14와 Backend health `UP`을 확인했다.
- Next steps:
  - OpenAI quota 복구 후 P8.5-V Chat·Embedding을 재검증한다.

## [2026-08-01] Session Summary (P8.5 Backend Provider runtime·V13)

- What was done:
  - OpenAI Chat·Embedding, Tavily, local/offline 설정, exact price query, usage call identity와 V13 catalog를 추가했다.
- Key decisions:
  - local real provider는 key·model·price·HTTPS·retry/store 정합성을 startup에서 검증한다.
- Issues encountered:
  - 공유 PostgreSQL container의 연결 상한을 test-only Hikari pool 3개로 안정화했다.
- Validation:
  - `gradlew check`: 67 suites/420 tests, failure·error·skip 0; P4~P8 actual도 모두 통과했다.
- Next steps:
  - key/gate가 준비되면 Codex bounded live verification을 1회 실행한다.

## [2026-07-31] Session Summary (P8 Backend·DB·API 수직 구현)

- What was done:
  - research/interview domain, V12, 공개 API 11개, preparation·feedback workflow command/query port와 Agent Run retry/resource 연동을 구현했다.
- Key decisions:
  - 모든 사용자 콘텐츠·교차 참조는 owner 복합 FK를 사용하고 answer/feedback/provenance는 immutable하게 보존한다.
  - Tavily는 명시적 provider 설정에서만 활성화하고 key가 없으면 fail-closed한다.
- Issues encountered:
  - actual E2E가 source allowlist 적용 순서와 answer history SQL 공백 결함을 발견해 관련 통합 회귀를 추가했다.
  - 1차 self-audit에서 output 전용 `FOLLOW_UP`과 foreign mutation의 owner 404 우선순위를 보정했다.
- Validation:
  - 제한 보정 후 `.\gradlew.bat check`: 61 suites/407 tests, failure·error·skip 0. P8/P7/P6 actual과 DB assertions도 통과했다.
  - 두 번째 single-agent read-only self-audit는 새 finding 없이 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (서버 계산 최종 학력·V11)

- What was done:
  - 학력 write에 명시적 `EducationLevel`을 추가하고 create/update/delete transaction마다 final education을 다시 계산했다.
  - V11로 legacy 학력 단계를 backfill하고 기존 `is_primary`를 단계·상태·날짜 순위로 보정했다.
- Key decisions:
  - 사용자 profile row lock으로 학력 mutation을 직렬화하고 request의 수동 primary flag를 제거했다.
- Issues encountered:
  - None.
- Validation:
  - `.\gradlew.bat check`, V10→V11 populated upgrade·empty migration test와 개발 DB Flyway 11 적용이 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (학력 evidence 제외·Agent Run history soft delete)

- What was done:
  - 학력 CRUD의 direct evidence 동기화를 제거하고 owner evidence 조회·분석 snapshot 및 문서 추출에서 교육 category를 제외했다.
  - 승인·거절 API를 DOCUMENT_CHUNK 근거로 한정해 직접 입력 근거는 항상 사용자 확인 완료 상태로 유지했다.
  - V9~V10으로 기존 학력 근거를 비식별 tombstone 처리하고 active 교육 category DB CHECK와 `agent_runs.deleted_at`을 추가했다.
  - terminal run 개별·최대 100개 atomic 선택 삭제 API와 owner·상태·audit 보존 통합 테스트를 추가했다.
- Key decisions:
  - 학력 row 자체는 프로필 완료도와 공고 학력 조건에 계속 사용하되 대외활동 evidence에는 투영하지 않는다.
  - Agent Run은 retry/root lineage, step, typed resource, idempotency, budget·usage FK가 깊어 물리 삭제하지 않는다.
- Issues encountered:
  - 개발 DB migration 실행용 non-web context는 V10 적용 성공 뒤 web security bean 부재로 실패했으며 schema는 정상 commit됐다.
- Validation:
  - `.\gradlew.bat check`: 54 suites, 385 tests 통과.
  - 개발 DB Flyway V9·V10 success, active 학력 evidence 0건, tombstone 1건, 교육 category CHECK 설치 확인.
- Next steps:
  - None.

## [2026-07-31] Session Summary (계정 닉네임 변경 API)

- What was done:
  - `PATCH /api/v1/account/display-name`의 validation DTO·Controller·transactional service와 `users.display_name` 상태 전이를 구현했다.
  - `GET /auth/me`를 DB 기반 projection으로 전환해 같은 사용자의 기존 Session에서도 변경된 닉네임을 조회하도록 했다.
  - Account mutation의 Session+CSRF OpenAPI AND requirement와 71 operations/52 paths 기준선을 고정했다.
- Key decisions:
  - 기존 `users.display_name varchar(100)`을 사용하므로 Flyway migration은 추가하지 않는다.
  - inactive·삭제된 사용자 ID는 인증 정보가 유효하지 않은 것으로 처리해 공통 401 계약을 유지한다.
- Issues encountered:
  - 새 OpenAPI operation description 누락으로 첫 contract test가 실패해 annotation을 보완하고 허용된 단일 재검증으로 통과했다.
- Validation:
  - `.\gradlew.bat check --console=plain --no-daemon`: 54 suites/382 tests, failure·error·skip 0.
  - Auth 다중 Session·trim·persistence·validation·401·403과 OpenAPI 71/52 계약이 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Backend 최종 validator PASS)

- What was done:
  - 최종 read-only validator가 V8·17 API·owner scope·immutable version/verification·generation 8단계·verification 6단계와 suggestion 보정을 재검증했다.
- Key decisions:
  - V1~V7은 HEAD와 동일하게 보존하고 P7 schema는 V8 forward migration으로 완료한다.
- Issues encountered:
  - 새 finding 없음.
- Validation:
  - Validator `PASS`; Backend 54 suites/380 tests, OpenAPI 51 paths/70 operations, P7/P6 actual wrapper·DB assertions PASS.
- Next steps:
  - P8 전에는 research/interview schema나 API를 추가하지 않는다.

## [2026-07-30] Session Summary (P7 validator 계약 보정·Backend 재검증)

- What was done:
  - `VerificationDto.suggestions` OpenAPI를 최대 20개·항목 1~1000자로 명시하고 generation fact-check와 verification check/aggregate structured output도 같은 경계로 통일했다.
  - verification aggregate가 facts와 requirements의 유효 제안을 합쳐도 20개를 넘지 않도록 facts 우선의 결정론적 제한을 적용했다.
- Key decisions:
  - 공개 DTO, OpenAPI, AI step output과 persistence command가 하나의 suggestion 계약을 사용하며 모델 단계별 입력이 유효해도 집계 결과가 공개 계약을 깨지 않게 한다.
- Issues encountered:
  - 1차 validator에서 Backend command는 정확했지만 OpenAPI annotation과 AI 중간 output이 더 넓게 허용되는 MAJOR 불일치를 확인했다.
- Validation:
  - `.\gradlew.bat check --console=plain --no-daemon`: 54 suites/380 tests, failure·error·skip 0.
  - P7 actual wrapper 1/1·후속 DB assertion과 P6 회귀 wrapper 1/1(Chromium 2/2)·후속 DB assertion, OpenAPI 51 paths/70 operations가 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 기다린다.

## [2026-07-30] Session Summary (P7 Cover Letter Backend·AI workflow 구현·검증)

- What was done:
  - V8, `coverletter` domain/application/API/store, generation·verification port와 typed Agent Run resource를 구현했다.
  - generation 8단계·verification 6단계, partial success/retry reuse, failure compensation와 immutable apply를 기존 orchestrator에 연결했다.
- Key decisions:
  - owner 복합 FK·partial unique·immutable DB 규칙과 application CAS를 함께 사용하고 provider 호출은 transaction 밖에서 실행한다.
- Issues encountered:
  - actual Browser form parser 결함은 Frontend에서 보정했으며 Backend 공개 계약 변경은 필요하지 않았다.
  - verification provenance claim text가 step checkpoint에 남을 수 있는 경로를 제거하고 최소 출력 privacy 회귀를 추가했다.
- Validation:
  - `.\gradlew.bat check` 54 suites/377 tests, P7 actual wrapper·DB assertions와 P6 actual regression, OpenAPI 51 paths/70 operations가 통과했다.
- Next steps:
  - 독립 validator 판정 후 P7 상태를 확정한다.

## [2026-07-30] Session Summary (P6 actual wrapper와 DB assertion 종료)

- What was done:
  - P6 Spring·PostgreSQL·Fake AI·Vue·Chromium wrapper를 current source에서 재실행하고 후속 분석·criterion·provenance·Agent Run assertion까지 닫았다.
  - DB harness가 존재하지 않는 `safe_error_code`를 조회한 부분을 실제 V4 `agent_runs.error_code`로 보정했다.
- Key decisions:
  - 제품 결함이 아닌 `TEST_HARNESS_DEFECT`로 분류하고 운영 코드·migration·API·workflow는 변경하지 않았다.
- Issues encountered:
  - 첫 실행은 Playwright exit 0 뒤 DB 컬럼명 오류로 실패했다.
- Validation:
  - 보정 후 `p6BrowserE2eTest --rerun-tasks --info --no-daemon --console=plain`: JUnit 1/1, Playwright 2/2, DB assertions 통과와 `BUILD SUCCESSFUL`.
- Next steps:
  - P7은 새 forward migration과 `coverletter` application port를 P6 계약 위에 추가한다.

## [2026-07-29] Session Summary (P6 Job Analysis Backend·AI workflow 구현)

- What was done:
  - owner-scoped snapshot/query/command port, deterministic scoring·hashing, immutable 저장소, 3개 API와 정확한 8단계 `job-analysis-v1` workflow를 연결했다.
  - VERIFIED evidence만 exact cosine·lexical fallback 후보와 provenance에 사용하고 동일 snapshot reuse·force reanalysis·OUTDATED projection을 구현했다.
- Key decisions:
  - 외부 호출은 transaction 밖에서 수행하고 serializable apply transaction이 snapshot 재검증, version 할당, criteria·provenance·Run link를 원자 처리한다.
  - `HIGH_QUALITY`는 거부하고 final fit score·owner·hash·persist는 모델이 아닌 Backend 정책과 command port가 결정한다.
  - 성공·재사용 step checkpoint도 같은 `SERIALIZABLE` 완료 transaction에 포함하며, 분석 당시 승인된 근거의 현재 상태가 바뀌어도 immutable 결과와 provenance link를 유지한다.
- Issues encountered:
  - OpenAPI 기준선, 통합 테스트 connection pool과 E2E selector/비공개 endpoint assertion을 보정했다. 수정된 실제 P6 Browser E2E는 재검증 상한으로 세 번째 실행하지 않았다.
  - 1차 validator의 atomic apply와 historical evidence 유지 MAJOR 두 건은 fresh/reuse rollback·crash/restart 및 `VERIFIED→REJECTED→SOURCE_DELETED` 회귀로 보정했다.
- Validation:
  - 보정 후 `.\gradlew.bat check --console=plain`: 44 suites/352 tests, 실패·오류·skip 0.
  - V1→V7, populated V6→V7, 제약·불변 migration 3/3과 OpenAPI 53/37가 통과했고 V1~V6 SHA-256은 기준선과 동일하다.
  - 최종 validator는 atomic completion과 historical evidence Backend finding 해소를 확인했지만 final-source actual P6 wrapper 미실행으로 전체 `FAIL`을 유지했다.
- Next steps:
  - 새로 승인된 검증 주기에서 실제 P6 Browser wrapper와 후속 DB assertion을 실행한다. 이 요청에서는 추가 자동 재검증하지 않는다.

## [2026-07-28] Session Summary (로컬 Document 업로드 멱등 설정 복구)

- What was done:
  - 실제 multipart 업로드 500의 원인을 빈 idempotency HMAC 키로 확정하고 명시적 `local` profile에 개발 전용 키를 제공했다.
  - 비로컬 profile의 환경 변수 기반 versioned secret 계약과 Document API·workflow 구현은 유지했다.
- Key decisions:
  - Document나 AgentOrchestrator에서 예외를 우회하지 않고 key 누락은 application startup에서 fail-closed한다.
- Issues encountered:
  - 실행 중인 8080 프로세스는 변경 전 설정을 보유해 재시작 전에는 동일 500을 반환한다.
- Validation:
  - `.\gradlew.bat check --console=plain`, DocumentIntegrationTest 12개, 설정 회귀와 P4 Browser E2E 4/4가 통과했다.
- Next steps:
  - 로컬 백엔드를 재시작하고 운영 profile에서는 충분한 엔트로피의 `IDEMPOTENCY_HMAC_KEY`를 주입한다.

## [2026-07-27] Session Summary (P5 Job Backend·AI workflow 구현)

- What was done:
  - Job API·application·domain·JDBC·Scheduler와 5단계 `JOB_POSTING_EXTRACTION` workflow를 추가했다.
  - owner·version·soft delete, canonical duplicate, idempotency 201/202 replay와 typed Agent Run resource를 연결했다.
- Key decisions:
  - validated `InetAddress`에 직접 연결하는 JDK socket transport로 DNS rebinding을 막고 body·압축 해제까지 절대 response deadline을 적용한다.
  - 사용자 override를 AI 추출보다 우선하며 unusable page는 WAITING_USER, 기술 실패는 FAILED로 업무 상태와 독립 처리한다.
- Issues encountered:
  - validator 보정에서 HttpClient 재해석과 stream timeout, reserved escape false duplicate를 해소했다.
- Validation:
  - `gradlew.bat check` 37 suites/322 tests, migration 6, OpenAPI 50/34와 `p5BrowserE2eTest` 5/5가 통과했다.
- Next steps:
  - P6 분석·RAG table·endpoint·workflow는 새 forward migration과 별도 package 책임으로 추가한다.

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
