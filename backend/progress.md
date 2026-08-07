# Progress

## Overview

- Java 21, Spring Boot 4.1, Spring AI 2.0 기반 단일 애플리케이션의 초기 빌드 환경이 구성되어 있다.
- P1 인증부터 P8 Interview, Dashboard·Career Guide read, profile eligibility와 Agent Run history delete까지 총 95 operations/70 paths가 구현되어 있다.
- V1~V25 migration이 적용됐고 V24는 전체 AI 기능이 공유하는 전역 일일 USD 10 budget policy를, V25는 취업 준비 가이드 5편의 현재 콘텐츠를 소유한다.
- 최신 Backend 전체 `check`가 통과했다. local은 실제 provider, local-offline/test는 network-disabled다.

## [2026-08-07] Session Summary (취업 준비 가이드 콘텐츠 재작성)

- What was done: `career_guide_posts` 5편의 제목·요약·본문을 실제 준비 동작과 체크리스트 중심으로 다시 쓰는 forward migration `V25`를 추가하고, `DashboardIntegrationTest`의 제목 assertion을 함께 갱신했다.
- Key decisions: API·DTO·schema는 그대로 두고 데이터만 바꾼다. V18과 같은 시드 상태 가드로 관리자가 편집한 글은 덮어쓰지 않는다.
- Issues encountered: None.
- Validation: 임시 PostgreSQL DB에 V17 → V18 → V25를 적용해 5행 갱신과 본문 보존을 확인했고 `./gradlew compileTestJava`가 통과했다. Testcontainers가 필요한 `check`는 실행하지 않았다.
- Next steps: 다음 backend 작업에서 `./gradlew check`를 실행한다.

## [2026-08-06] Session Summary (AI 비용 정책 단일 전역 일일 한도 전환)

- What was done: 분야별 고정 예약 설정·환경 변수·typed cost properties를 제거하고 활성 가격 version 자동 선택과 호출 직전 동적 예약을 연결했다.
- Key decisions: 운영 비용 한도는 DB versioned policy의 전역 일일 USD 10 하나만 적용하며 사용자 preference와 비동기 run 상한은 사용하지 않는다.
- Issues encountered: 기존 테스트 fixture와 budget 테스트가 삭제된 preference column·고정 시작 예약을 전제로 해 전역 ledger와 0원 시작 계약에 맞게 갱신했다.
- Validation: 구현 중 `compileJava`, `compileTestJava`, 인증 통합 테스트 1건과 `AgentRunBudgetResumeIntegrationTest` 7건이 통과했다. 프로젝트에 Spotless task가 없어 `spotlessApply`는 실행할 수 없었고 diff whitespace 검사는 통과했다. 최종 원장 병합 보정 후 재검증과 전체 `check`는 요청에 따라 생략했다.
- Next steps: plan·credit 계약 확정 후 Provider 원가 ledger와 분리된 entitlement/credit accounting을 추가한다.

## [2026-08-06] Session Summary (자기소개서 exact OpenAI 모델 선택과 memo context)

- What was done: 자기소개서 품질 모드를 exact model catalog·v4 workflow로 교체하고 model catalog API, memo-aware 생성 context, V23 가격 catalog를 구현했다.
- Key decisions: model ID는 `OpenAiChatModels`에서 단일 관리하고 신규 v4 Run에 고정하며 v1~v3 Run만 legacy 품질 계약으로 재개한다.
- Issues encountered: 로컬 Gradle daemon의 비용·가격 환경 변수 쌍이 불일치해 테스트 context가 실패했으며 새 프로세스에서 테스트용 값을 0으로 고정해 코드 회귀가 아님을 확인했다.
- Validation: `./gradlew.bat check --no-daemon` 성공, 81 suites·578 tests·실패 0.
- Next steps: 실제 API key의 프로젝트별 model entitlement와 운영 비용 한도는 배포 전 smoke test로 확인한다.

## [2026-08-06] Session Summary (Cover Letter Writer 의미 검증 보정 재시도)

- What was done:
  - 플래티어 실패 Run의 `WRITE_ANSWER` 경로에서 scope·허용 근거·내용·문항별 최대 길이 검증을 typed structured-output correction 대상으로 옮기고, Writer prompt를 v5로 격리해 Unicode code point 최대 길이를 명시했다.
  - blank·20,000자 초과 record 경계와 기존 최종 domain validation은 방어선으로 유지했다.
- Key decisions:
  - 공개 REST·DB·workflow version은 바꾸지 않고 model이 스스로 고칠 수 있는 결과만 기존 제한 재시도를 사용한다. 내부 오류에는 답변 원문을 포함하지 않는다.
- Issues encountered:
  - 기존 실패 output은 저장되지 않아 scope·evidence·content·length 중 정확한 predicate는 복원 불가했다. 메타데이터상 3,195 output token과 1,000자 제한으로 length 위반 가능성이 가장 높다.
- Validation:
  - 집중 workflow·contract test와 `backend/.\gradlew.bat check` 통과. 실제 계정·DB·외부 AI Run `0b136374-97f3-456c-b9cb-4bca912821c2`가 correction 1회 후 943/1,000자 답변으로 `SUCCEEDED`했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (플래티어 Cover Letter 생성 실패 복구)

- What was done:
  - `PLAN_QUESTIONS` strict output의 조건부 Java record 계약과 exact output schema version을 prompt에 공개하고 nullable connection의 빈 문자열을 `null`로 정규화했다.
  - 단일 문항은 불필요한 계획·분석·배분 model call을 서버 결정으로 대체하고, 최종 Writer만 실제 AI를 사용하도록 최소화했다.
- Key decisions:
  - 공개 API·DB migration·Frontend는 변경하지 않았다. 다중 문항은 기존 AI 배분 정책을 유지한다.
- Issues encountered:
  - 중간 검증 Run에서 exact output schema version 누락과 provider timeout이 확인됐다. 임시 계정 물리 삭제는 불변 provenance trigger로 rollback됐다.
- Validation:
  - 집중 contract test, `bootJar`, 최종 `backend/.\gradlew.bat check` 성공. 실제 성공 Run은 8단계 100%, 답변 1개·752자·`AI_GENERATED`를 확인했다.
- Next steps:
  - 배포 후 기존 실패 Run을 retry한다.

## [2026-08-05] Session Summary (Cover Letter workflow v3 hardening)

- What was done:
  - active generation·verification v3와 legacy v1/v2 executable, strict prompt/schema registry, deterministic claim·issue·framework·truncation·evidence selection 정책을 연결했다.
  - USER_EDITED parent provenance는 새 본문에 exact excerpt가 남은 link만 복사하도록 보강했다.
- Key decisions:
  - 공개 REST/DB 계약과 적용 migration은 유지하고 workflow 내부 strict 구조 변경만 version bump로 격리했다.
- Issues encountered:
  - 전체 check 최초 실행은 도구 timeout 뒤 한 registry count 회귀를 확인했고 수정 후 장시간 재실행이 통과했다. P7 수정 후 실행은 selector 모호성으로 실패했다.
- Validation:
  - `compileJava`, `compileTestJava`, 집중 tests, `check` 80 suites/564 tests 통과. P7 최종 통과 미검증, 유료 Provider 0회.
- Next steps:
  - 고유 selector 수정본의 P7 재검증이 필요하다.

## [2026-08-05] Session Summary (Cover Letter workflow v2와 durable v1 호환)

- What was done:
  - generation·verification 신규 접수를 v2로 전환하고 v1 definition/prompt/executor를 durable Run용으로 유지했다.
  - typed strategy, bounded job/evidence/sibling Context, ACTIVITY support, AI_REVISED current answer 전달과 explicit verification 품질 rubric을 추가했다.
- Key decisions:
  - 공개 API·DB·Frontend 계약과 V22 migration은 변경하지 않고 internal workflow/input/output schema만 versioning했다.
  - Provider call cap·model tier·24,000 input token cap과 bounded correction 정책은 유지했다.
- Issues encountered:
  - P7 Browser E2E의 기존 document Fake가 최신 provider schema/category와 불일치해 cover-letter 단계 전에 실패했다. fixture는 보정했으나 최종 재실행하지 않았다.
- Validation:
  - `backend/.\gradlew.bat check` 성공: 79 suites/549 tests. OpenAPI 94 operations/69 paths 유지, 실제 Provider 호출 0회.
  - `backend/.\gradlew.bat p7BrowserE2eTest` 실패: document evidence fixture 선행 오류.
- Next steps:
  - 보정된 P7 E2E fixture 재검증과 정보 부족 사용자 보완 lifecycle 설계.

## [2026-08-05] Session Summary (공고 반기 저장·목록 API와 V21~V22 upgrade)

- What was done:
  - `JobPostingHalf`, 저장 연도·반기, owner 실제 기간 projection과 preset/직접 시작일 목록 필터를 구현하고 기존 행을 V21~V22로 분류했다.
- Key decisions:
  - 등록 `created_at`의 Asia/Seoul 날짜를 시작 기준으로 사용하고 DB trigger와 CHECK로 직접 SQL 경로까지 동일 규칙을 강제한다.
- Issues encountered:
  - backfill 직후 같은 transaction의 ALTER가 pending trigger event로 실패해 DML과 제약 확정을 연속 migration으로 분리했다.
- Validation:
  - 집중 Job·migration·OpenAPI 테스트와 전체 `gradlew check` 79 suites/539 tests 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Job requirement legacy Provider 필드 제거)

- What was done:
  - 동일 공고·profile/evidence snapshot이 source output v4에서는 성공하고 v5에서 `JOB_ANALYSIS_KOREAN_OUTPUT_REQUIRED`로 4회 실패한 회귀를 확인해 Provider requirement를 block ID·원문·ordinal 3필드로 축소했다.
- Key decisions:
  - section·source location은 기존 server-owned source block에서만 파생하고 공개 API·DB·workflow version은 유지한다. 변경된 Provider schema/prompt만 v6/v9로 격리했다.
- Issues encountered:
  - P6 전용 Browser E2E는 Chrome 대기로 3분 timeout, P7은 Job Analysis 이전 문서 `정리 완료` UI 대기로 실패해 실제 Job Analysis 브라우저 경로는 검증되지 않았다.
- Validation:
  - strict schema·workflow·normalization focused tests와 Backend 전체 `check` 79 suites/538 tests가 통과했다. 실제 Provider 호출은 수행하지 않았다.
- Next steps:
  - 인증된 로컬 세션에서 동일 공고를 실제 Provider로 한 번 재분석해 source-output-v6의 수직 동작을 확인한다.

## [2026-08-04] Session Summary (Job Analysis rubric v2 수직 구현)

- What was done:
  - server-owned posting section/source block, criterion별 evidence retrieval, coverage-aware scoring, nullable fit score와 V20/API 저장 계약을 구현했다.
- Key decisions:
  - `ROLE_SUMMARY|OTHER`는 점수 기준에서 제외하고 `UNKNOWN`은 분모에서 제외하며 `analysisCoverage`로 별도 표현한다.
- Issues encountered:
  - 기존 fake workflow가 단일 embedding/search와 provider-owned section을 전제해 source fixture와 batch embedding을 새 계약으로 교정했다.
- Validation:
  - `gradlew --no-daemon check`: 79 suites/538 tests 통과. 마지막 source-block 안정성 보완 후 관련 workflow·normalization·section·scoring 집중 테스트도 재통과했다.
- Next steps:
  - 실제 Provider 재분석에서 추출 7개 source bullet과 criterion별 근거 검색 결과를 확인한다.

## [2026-08-04] Session Summary (회원가입 비밀번호 조합 서버 검증)

- What was done:
  - 가입 비밀번호를 전체 10자 이상, 문자·숫자·특수문자 각 1개 이상, UTF-8 72바이트 이하로 검증하는 Bean Validation 정책을 추가했다.
  - Signup OpenAPI 설명과 통합·단위 경계 테스트를 새 정책에 맞췄다.
- Key decisions:
  - 문자 수는 Unicode code point로 계산하고 Unicode punctuation·symbol을 특수문자로 인정한다. login의 기존 1..72바이트 계약은 유지한다.
- Issues encountered:
  - 별도 공고 분석 집중 테스트가 공유 test result를 덮어쓴 첫 전체 실행은 결과를 판정하지 않고, 다른 Gradle 테스트 종료 후 전체 `check`를 단독 재실행했다.
- Validation:
  - Auth·OpenAPI·validation 집중 테스트와 Backend 전체 `check` 78 suites/536 tests가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Job Analysis eligibility 출력 truncation 회귀 수정)

- What was done:
  - 최근 실제 Job Analysis 실패 메타데이터에서 `ASSESS_ELIGIBILITY`가 정확히 2,048 output token에서 `AI_CHAT_OUTPUT_TRUNCATED`로 종료된 사실을 확인하고, 해당 단계 전용 상한을 8,000으로 복구했다.
  - eligibility 요청에 `reasoningEffort=low`, `verbosity=low`를 명시해 출력 계약과 무관한 토큰 소비를 억제했다.
- Key decisions:
  - prompt instruction/schema와 공개 API·DB 계약은 바뀌지 않았으므로 prompt version, workflow version, migration은 변경하지 않았다.
  - 실제 계정 세션이나 인증 정보를 임의 생성·변경하지 않았으며, 실제 Provider 수직 검증은 활성 인증 세션 부재와 브라우저 런타임 연결 실패 때문에 실행하지 않았다.
- Issues encountered:
  - 전체 `check`는 첫 실행에서 Gradle test result의 `in-progress-results-generic.bin` 누락, 규칙상 한 번 재검증한 `cleanTest check`에서는 result stream `EOFException`으로 종료됐다. 두 실행 모두 assertion 실패는 보고되지 않았지만 전체 성공으로 간주하지 않는다.
- Validation:
  - `JobAnalysisWorkflowTest` 23건과 `JobAnalysisWorkflowContractTest` 5건이 통과했다. 18개 requirement를 전달하는 회귀 fixture에서 eligibility 전용 8,000 상한과 low/low 정책을 검증했다.
- Next steps:
  - 사용자가 로컬 앱에 다시 로그인한 뒤 동일 공고를 강제 재분석하여 실제 Provider 8단계 완료 여부와 eligibility 사용량을 확인해야 한다.

## [2026-08-03] Session Summary (공고 요구사항 canonical 정규화와 bounded retry 복구)

- What was done:
  - source-only requirements v4, 서버 canonical normalization, 단계별 prompt/token policy, semantic/transport retry 분리와 Chat wall-clock deadline을 Backend 내부 계약으로 구현했다.
- Key decisions:
  - API·DB·migration·Frontend·공개 Agent Step attempt 계약은 유지하고 기존 requirements v3 checkpoint는 새 실행에서 hash/schema 불일치로 재사용하지 않는다.
- Issues encountered:
  - 전용 P6 Browser E2E가 분석 전 공고 등록 UI locator timeout으로 실패해 P7 task는 시작하지 못했다.
- Validation:
  - Backend 전체 `check` 78 suites/535 tests, focused workflow/orchestration/gateway tests 통과. 실제 Provider 호출 0회.
- Next steps:
  - 실제 Provider 수직 검증과 범위 밖 Browser locator 보정은 별도 수행한다.

## [2026-08-03] Session Summary (Job Analysis 참조 라우팅과 자동 분석 트랜잭션 복구)

- What was done:
  - 프로필 구조 확장 뒤 실제 Job Analysis의 eligibility와 match 출력이 evidence ID와 structured fact reference를 혼동하는 실패를 재현하고 prompt v6와 correction-once 참조 검증으로 수정했다.
  - `JobAutoAnalysisCoordinator.onRequested`의 외부 `REQUIRES_NEW`를 제거해 claim/launch 내부 트랜잭션이 pool size 2에서 세 번째 connection을 기다리던 문제를 해소했다.
- Key decisions:
  - 도메인의 최종 hard validation과 immutable snapshot/version 검증은 유지했다. 허용 목록 밖 참조는 자동 삭제하지 않고 최대 1회 교정 후 기존 실패 경계로 종료한다.
- Issues encountered:
  - 최초 전체 `check`에서 `CoverLetterAgentRunIntegrationTest` 2건이 `ProfileAnalysisQueryService.loadAnalysisSnapshot`의 `RESOURCE_NOT_FOUND`로 실패했다. 새 필수 eligibility 선언을 만들지 않던 공통 테스트 fixture를 보강해 해결했다.
  - 최종 bootRun 첫 시도는 local profile 미지정으로 activation validator가 종료했으며, local profile을 명시한 재기동 후 `/actuator/health` `UP`을 확인했다.
- Validation:
  - Job Analysis focused 26 tests와 `JobAnalysisIntegrationTest`, `JobAutoAnalysisIntegrationTest`, Backend 전체 `check` 525건이 통과했다. 실제 API/Provider Run은 8단계 모두 1회 시도로 성공해 sealed 결과를 저장했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (문서 경험 retire와 Job Analysis 비교 복구)

- What was done:
  - 문서 재분석 시작 transaction에서 이전 문서 유래 evidence를 삭제 또는 `SOURCE_DELETED` 처리해 새 결과 적용 전부터 공고 분석·자기소개서 snapshot에서 제외했다.
  - Job Analysis 비교 요청에 low reasoning/verbosity를 지정해 약 4분 network boundary 초과를 제거하고, 허용된 근거의 support type 불일치는 `UNKNOWN`으로 강등했다. 기존 비교 오류 retry는 성공 단계를 재사용하는 로컬 보수 fallback을 사용한다.
- Key decisions:
  - hallucinated/stale reference는 계속 거부하고, allowlist 안의 근거가 요건 유형과 호환되지 않는 경우에만 근거를 비워 보수 판정한다.
- Issues encountered:
  - 전체 `check`는 `DashboardMigrationTest.startPostgres`의 Testcontainers log wait에서 5분 timeout됐다. 정지된 Gradle 결과 stream의 EOF는 `--no-daemon cleanTest`로 초기화했다.
- Validation:
  - `JobAnalysisWorkflowTest`, `SpringAiOpenAiGatewayTest`, `DocumentIntegrationTest`가 단일-use Gradle에서 통과했다. 공개 API retry가 실제 분석을 저장했고 해당 Run의 `ai_usage_records`는 0건, 누적 distinct provider call은 9건이었다.
- Next steps:
  - Testcontainers 시작 문제를 별도 환경 이슈로 점검한 뒤 전체 `check`를 재실행한다.

## [2026-08-02] Session Summary (공고 분석 profile fact provenance·compatibility)

- What was done:
  - Profile owner 1:1 지원 자격 record/API, 대표 학력 포함 analysis snapshot, JOB_ANALYSIS structured fact allowlist/provenance와 근거 유형 호환성 검증을 추가했다.
  - profile/context/canonical/step hash를 profile-v2 계약으로 갱신해 학력·지원 자격 변경이 `PROFILE_CHANGED`와 checkpoint 무효화로 이어지게 했다.
- Key decisions:
  - 기존 8단계, 점수 가중치·계수, evidence link/공개 DTO 의미와 immutable 분석 이력은 유지했다.
- Issues encountered:
  - `*JobAnalysis*`는 184초, 전체 `check`는 304초 동안 기존 통합 테스트 connection 대기에서 timeout됐다. 존재하지 않는 `spotlessCheck` task는 검증 수단에서 제외했다.
  - OpenAPI exact test의 자동 응답 코드 기준선은 두 번의 실행에서 각각 GET `400`, PUT `403` 누락을 드러냈고 마지막 보정 후 재실행하지 않았다.
- Validation:
  - `compileJava`, `testClasses`, ProfileAnalysis·JobAnalysis workflow/hash 집중 테스트, V19 migration, 신규 Job integration 단일 테스트가 통과했다. 마지막 OpenAPI assertion 보정은 이후 `compileTestJava` 통과만 확인했다.
- Next steps:
  - OpenAPI exact test를 다음 검증 회차에 확인하고 Hikari 대기 문제를 별도 진단한 뒤 전체 Backend suite를 재검증한다.

## [2026-08-02] Session Summary (AI 사용자 노출 결과 한국어 계약)

- What was done:
  - Job Analysis의 requirement·eligibility·match 사용자 문장과 Document evidence 소재 title·content·warning에 한국어 prompt·record validation 계약을 추가했다.
  - 공고 출처의 JSONPath·내부 필드명을 거부하고 두 workflow의 prompt identity를 v3로 갱신했다.
- Key decisions:
  - API·DB·workflow/output schema는 유지하고 최소 한국어 음절 검증 실패만 기존 correction-once 경계로 처리한다.
- Issues encountered:
  - prompt 줄바꿈 때문에 집중 계약 assertion 1건이 실패했으며 값이나 계약 변경 없이 assertion을 교정했다.
  - 전체 `check`는 기존 `JobAnalysisIntegrationTest`의 자동 분석 이벤트가 Hikari connection을 기다리며 실행 제한을 넘겨 thread dump 확인 후 해당 프로세스만 종료했다.
- Validation:
  - Job Analysis workflow·prompt, Document evidence contract와 공통 한국어 policy 집중 테스트 28건 통과. 전체 suite는 위 connection 대기로 미완료다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (retrieval embedding route 오용 보정)

- What was done:
  - 공고 분석과 자기소개서 근거 검색의 `EmbeddingRequest`가 Chat route 대신 active embedding policy snapshot의 provider·product·dimension을 사용하도록 보정했다.
  - snapshot에 route 필드를 추가하고 adapter·checkpoint identity·회귀 assertion을 함께 갱신했다.
- Key decisions:
  - 기존 V2 embedding policy와 price catalog를 그대로 사용하므로 migration과 공개 API 변경은 없다.
- Issues encountered:
  - 전체 check 두 번이 수정 중 지역 변수 compile 오류에서 중단됐다. 정확한 `ObjectNode` 선언 교정 후 compile과 focused test는 성공했지만 전체 suite 재실행은 규칙상 생략했다.
- Validation:
  - `compileJava` 성공, Job Analysis·Cover Letter workflow focused test 성공, 앞선 Job Analysis integration·auto analysis·policy validator test 성공. 구현 검증 외부 network 0회.
- Next steps:
  - 서버 반영 뒤 기존 실패 공고 분석을 명시적으로 retry해 successor run 결과를 확인한다.

## [2026-08-02] Session Summary (OpenAI image reference 직렬화 보정)

- What was done:
  - image adapter가 local reference text와 이미지 bytes 하나를 같은 user message에 묶도록 변경하고 unsafe·duplicate reference를 Provider 호출 전에 거부했다.
  - image prompt identity와 workflow input association policy를 v4로 올리고 실제 OpenAI SDK request payload 회귀를 추가했다.
- Key decisions:
  - API·DB·9단계 `job-posting-extraction-v3`·output schema v3는 유지하며 `Media.id/name`을 Provider-visible 식별자로 사용하지 않는다.
- Issues encountered:
  - 전체 508 tests 중 범위 밖 Interview 통합 테스트 1건이 실패했으나 정확한 단일 테스트 격리 실행은 통과했다. 첫 격리 필터는 package 오기재로 실행되지 않았다.
- Validation:
  - gateway/workflow 집중 테스트 통과, 전체 `check` 508건 중 무관한 1건 실패, 해당 단일 테스트 격리 통과. 외부 AI/Search 호출 0회.
- Next steps:
  - 배포 뒤 기존 terminal 실패는 현재 retry contributor를 통해 최신 canonical v3 successor로 재시도한다.

## [2026-08-02] Session Summary (JOB_ANALYSIS strict Provider DTO 경계 개선)

- What was done:
  - `EXTRACT_REQUIREMENTS`, `ASSESS_ELIGIBILITY`, `MATCH_EVIDENCE`의 Provider 전용 record와 context-aware 내부 DTO mapping을 추가하고 P6/P7 Fake output을 v2 계약으로 갱신했다.
  - 서버 소유 `reusable|reusableAnalysisId`를 Provider schema에서 제거하고 `sourceLocation|missingReason`만 required nullable로 유지했다.
- Key decisions:
  - API·DB·점수·persist 계약과 8단계 `job-analysis-v1`은 유지한다. 세 output schema는 v2, prompt는 `job-analysis-prompt-v2`로 올려 input hash가 구계약 checkpoint와 달라지게 한다.
- Issues encountered:
  - `spotlessApply` Gradle task가 존재하지 않아 실행할 수 없었다. 첫 집중 테스트 명령은 도구 timeout으로 최종 결과를 확보하지 못해 서로 다른 no-daemon 단일 worker 명령으로 다시 검증했다. 최종 전체 check는 범위 밖 `ObjectDeletionOutboxIntegrationTest` 2건이 `PENDING`으로 남아 실패했고 해당 클래스 격리 실행은 통과했으나, 허용된 전체 재검증에서 동일 실패가 재발했다.
- Validation:
  - `test --tests "*JobAnalysis*"`, `test --tests "*StrictStructuredOutput*"`, `test --tests "*OpenAiStrictSchema*"`와 Outbox 실패 클래스 격리 실행은 통과했다. 최종 `check`는 74 suites/506 tests 중 범위 밖 2건 실패로 미통과했다. 외부 AI/Search 호출 0회.
- Next steps:
  - 실제 사용자 실행에서는 8단계 `SUCCEEDED`, 실패 step·safe error 없음, Chat 3건+Embedding 1건 usage를 확인한다.

## [2026-08-02] Session Summary (V18 Career Guide 장문 콘텐츠 보강)

- What was done:
  - 기존 API·schema를 유지하면서 미수정 V17 seed 5개의 본문을 실용적인 장문 콘텐츠로 갱신하는 V18 migration과 upgrade test를 추가했다.
- Key decisions:
  - `version=1 AND updated_at=created_at`인 stable seed ID만 갱신해 별도 편집본을 보존하고 version을 2로 올린다.
- Issues encountered:
  - 전체 check의 기존 Interview test가 transaction `now()`와 application 생성 시각의 183ms 차로 한 번 실패했고 격리 재실행은 통과했다.
- Validation:
  - `DashboardMigrationTest` 통과. 전체 check는 499 tests 중 1개 최초 실패, 해당 test 격리 실행 통과.
- Next steps:
  - Interview integration fixture timing 안정화는 별도 범위로 남긴다.

## [2026-08-02] Session Summary (Dashboard projection·Career Guide read)

- What was done:
  - `/dashboard?month=YYYY-MM` owner 집계·서울 마감과 `/career-guides` 게시 조회, V17 seed·OpenAPI 계약을 구현했다.
- Key decisions:
  - 기존 aggregate를 변경하지 않는 JDBC read projection과 전역 guide table을 사용하고 `CLOSED`·미게시·미도래 게시물을 제외한다.
- Issues encountered:
  - 초기 mapper method reference와 test JDBC Instant binding을 보정했다. 첫 전체 check는 5분 제한으로 중단됐으나 장시간 재실행은 통과했다.
- Validation:
  - `gradlew check --console=plain --max-workers=1`: 73 suites/498 tests, 실패·오류·skip 0.
- Next steps:
  - 관리자 guide mutation은 별도 승인 범위다.

## [2026-08-02] Session Summary (공고 자동 분석 durable orchestration)

- What was done:
  - 수동 본문 또는 URL 추출 완료 뒤 같은 공고 revision에 BALANCED 분석을 한 번만 접수하는 V16 요청 table, after-commit coordinator와 scheduled reconciliation을 구현했다.
  - Job detail에 additive 자동 분석 projection을 연결하고 OpenAPI exact schema 계약을 갱신했다.
- Key decisions:
  - 브라우저 연쇄 호출 대신 `(user_id, job_id, job_version)` unique intent와 결정적 Agent Run ID를 사용하며 Provider 호출은 기존 worker transaction 밖에서 수행한다.
- Issues encountered:
  - 첫 전체 check의 OpenAPI 필드 allowlist 누락은 보정했다. 두 번째 전체 check는 기존 Interview fixture의 PostgreSQL transaction timestamp 경계로 1건 간헐 실패했고 같은 테스트 단독 재실행은 통과했다.
- Validation:
  - `JobAutoAnalysisIntegrationTest` 2건, `JobIntegrationTest` 7건, `JobAnalysisIntegrationTest` 4건과 `OpenApiContractTest` 통과. 전체 `check`는 493건 중 무관한 Interview fixture 1건 때문에 최종 green이 아니다.
- Next steps:
  - Interview fixture가 `created_at`도 포함한 statement clock을 사용하도록 별도 안정화한 뒤 Backend 전체 `check`를 다시 실행한다.

## [2026-08-01] Session Summary (Job image extraction v3 후속 보정)

- What was done:
  - image reference·adapter failure/usage·retry upgrade·WebP·aggregate 품질 계약과 회귀 테스트를 구현했다.
- Key decisions:
  - v3만 executable이고 v1·v2는 immutable legacy다. migration과 OpenAPI path/operation은 변경하지 않았다.
- Issues encountered:
  - 첫 전체 check의 registry assertion과 무관한 Interview 간헐 실패는 수정/단독 재현 뒤 최종 전체 검증으로 해소했다.
- Validation:
  - `gradlew check --rerun-tasks --no-daemon --console=plain --max-workers=1`: 70 suites/491 tests, 0 failed. P5 Chromium 5/5. 실제 Provider 호출 0회.
- Next steps:
  - animated WebP·live Provider 검증은 현재 범위 밖이다.

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
