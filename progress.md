# Progress

## Overview

- 초기 프론트엔드, 백엔드, Docker Compose, CI 환경이 구성되어 있다.
- 제품 기능·API·DB·화면·기술 명세는 P0 승인 기준선으로 `docs/spec/`에 존재한다.
- P1 공통 HTTP·Session·CSRF·인증·idempotency 기반과 P2 사용자 소유 프로필·direct evidence가 구현되어 있다.
- P3 PostgreSQL Agent Run 수명주기, 고정 Fake workflow, 비용 예약·정산, SSE와 Frontend 복구 기반이 최종 validator `PASS`로 완료됐다.
- P4 Document upload·parse·storage·Fake AI 근거 pipeline과 Frontend 목록·상세·검토가 최종 validator `PASS`로 완료됐다.
- P5 Job 등록·URL 추출·상태·Scheduler와 Frontend 목록·등록·overview가 최종 validator `PASS`로 완료됐다.
- P6 공고 분석·owner-scoped RAG·결정론적 점수·OUTDATED·재분석 수직 기능은 두 구현 MAJOR 보정과 final-source actual Chromium 2/2·후속 DB assertion을 통과해 `DONE`이다.
- P7 자기소개서 Backend·AI Workflow·Frontend 수직 기능은 1차 validator의 두 MAJOR 보정, final-source actual Chromium·DB assertion과 최종 read-only validator `PASS`로 `DONE`이다.
- P8 면접 조사·예상 질문·답변 피드백은 Backend·AI Workflow·Frontend, final-source actual P8/P7/P6 회귀와 두 번째 single-agent read-only self-audit를 통과해 `DONE`이다.
- 공개 Spring/OpenAPI는 profile eligibility GET/PUT을 포함해 총 94 operations·69 paths다.
- P8.5 local 실제 Provider 연결은 구현됐다. Tavily BASIC, 실제 문서 Embedding, Chat strict output, trusted ref mapping, evidence persistence와 document finalize가 실제 run에서 성공했다. candidate rejection terminal 분류 보정은 offline 검증됐지만 live 재검증 전이므로 전체 상태는 `IMPLEMENTED_NOT_LIVE_VERIFIED`다.
- P8.5-V 사용자 로컬 검증 뒤 P8.6 기능 한도, P8.7 사용량·원가 집계, P8.8 실패 UX, P8.9-A 읽기 전용 Backoffice를 순서대로 진행한다. P9는 이 선행 기반이 완료될 때까지 차단된다.

## [2026-08-04] Session Summary (공고 분석 source block·criterion RAG·coverage v2 개선)

- What was done:
  - 공고 구역과 원문 bullet을 서버가 소유하도록 바꾸고 criterion별 hybrid evidence 검색, `UNKNOWN` 제외 점수와 분석 커버리지, 가독성 중심 결과 화면을 수직으로 구현했다.
- Key decisions:
  - 화면용 원문 bullet과 점수용 atomic criterion을 분리하고 이전 분석은 rubric v1 이력으로 보존한다.
- Issues encountered:
  - 기존 전체-query 검색과 `UNKNOWN=0`이 근거 누락을 실제 불일치처럼 점수에 반영했고 역할 소개도 criterion 후보가 될 수 있었다.
- Validation:
  - Backend 전체 79 suites/538 tests와 최종 집중 회귀, Frontend 전체 67 files/281 tests·production build 및 최종 page 9 tests/type check가 통과했다.
- Next steps:
  - 실제 플래티어 공고를 새 rubric으로 재분석해 Provider source-block 선택과 사용자 evidence 매칭 분포를 관찰한다.

## [2026-08-04] Session Summary (공고 분석 최신 Run 표시 오류 수정)

- What was done:
  - 재실행 성공 뒤에도 과거 자동 실패 Run이 상단과 상세 link에 노출되던 Frontend 선택 오류를 수정했다.
- Key decisions:
  - DB 데이터나 Backend 계약을 변경하지 않고 최신 Run query보다 자동 Run ID를 우선하던 문제 코드 한 줄을 바로잡았다.
- Issues encountered:
  - 대상 공고의 실제 DB 이력은 최신 `SUCCEEDED`와 과거 `FAILED` 순서로 정상이었고, 화면만 반대로 선택했다.
- Validation:
  - 실제 Run 시각·상태 대조, Job Analysis 집중 Vitest와 Frontend 전체 67 files/281 tests·production build가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 실패 화면 재실행 경로 정정)

- What was done:
  - `retryable=false`인 실패 공고 분석 화면에도 실패 카드 내 재실행 버튼을 제공하고 현재 공고 기준 새 분석 요청을 연결했다.
- Key decisions:
  - generic retry 가능 여부와 사용자가 현재 resource로 분석을 새로 실행하는 행동을 구분한다.
- Issues encountered:
  - 이전 확인은 generic retry 분기에 한정돼 실제 첨부 화면의 버튼 부재를 놓쳤다.
- Validation:
  - Job Analysis 집중 Vitest와 Frontend 전체 67 files/280 tests·production build가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 재시도 확인·두 번째 로고 적용)

- What was done:
  - 실패한 공고 분석이 서버의 `retryable` 판정에 따라 기존 Agent Run retry endpoint로 successor 실행을 생성하는 흐름을 명세·코드·테스트에서 확인했다.
  - 제공된 두 번째 로고를 투명 256px 자산으로 최적화해 서비스 공용 BrandMark와 파비콘에 적용했다.
- Key decisions:
  - 이미 구현된 공고 분석 재시도 계약과 API를 중복 변경하지 않고 회귀 검증으로 고정했다. 로고 이름 text와 full·compact·inverse 호출 계약은 유지했다.
- Issues encountered:
  - None.
- Validation:
  - 공고 분석·공용 UI 집중 Vitest 12건과 Frontend 전체 `corepack pnpm check` 67 files/279 tests·production build가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 안내 문구·동의 상세 레이아웃 개선)

- What was done:
  - 이메일 형식 보조 문구를 제거하고 비밀번호 안내를 세 개의 사용자 문장으로 정리했다.
  - 동의 상세에서 전문 용어를 제거하고 핵심 요약·번호 카드·고정 확인 영역의 desktop dialog/mobile bottom sheet로 개편했다.
- Key decisions:
  - 서버의 실제 비밀번호 검증은 유지하고 기술 단위는 화면에 노출하지 않는다.
- Issues encountered:
  - 인앱 Browser runtime이 비어 있어 공식 Toss·Material·Apple 자료 조사와 저장소 Chromium으로 대체했다. Chromium 2회는 동작이 아닌 중복 문구 locator strict 오류로 중단되었고 재검증 상한에 따라 보정 후 세 번째 실행은 하지 않았다.
- Validation:
  - 집중 Vitest 20건과 Frontend 전체 `corepack pnpm check` 67 files/279 tests·production build가 통과했다. 수정된 Chromium 시나리오의 최종 완주는 `NOT_VERIFIED`이다.
- Next steps:
  - 다음 검증 회차에서 정확한 locator로 회원가입 desktop/mobile Chromium 1건을 완주한다.

## [2026-08-04] Session Summary (회원가입·첫 지원 정보·공고 마감 UX 보강)

- What was done:
  - 회원가입 이메일·비밀번호 blur 검증과 서버 password 정책, 온보딩 지원 자격 입력, 공고 등록 30분 단위 마감 선택을 구현했다.
  - 기능·API·페이지 계약과 Backend·Frontend·E2E 추적 문서를 실제 동작에 맞췄다.
- Key decisions:
  - password는 Unicode 10자 이상과 문자·숫자·특수문자 조합 및 BCrypt 72-byte 상한을 함께 적용한다. eligibility·Job 공개 API와 DB는 유지한다.
- Issues encountered:
  - 첫 Frontend 전체 check는 신규 eligibility mock 누락 1건에서 실패했고 보완 후 성공했다. 병행된 별도 공고 분석 변경과 Gradle 결과는 건드리지 않고 전체 Backend check를 단독 재실행했다.
- Validation:
  - Backend 78 suites/536 tests, Frontend 67 files/279 tests·production build, Chromium 2/2와 `git diff --check`가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 검증 안내·필수 동의 상세 Modal)

- What was done:
  - 회원가입 이메일·비밀번호 client 검증을 실제 API 계약으로 안내하고 이용약관·개인정보와 AI 처리의 필수 동의 상세 Modal을 구현했다.
  - 기능·페이지 명세를 UTF-8 비밀번호 경계, 개인정보 수집·이용과 OpenAI API 처리 안내에 맞춰 동기화했다.
- Key decisions:
  - Backend의 UTF-8 10..72바이트 계약을 유지하고 숫자·특수문자 필수 조합을 새로 만들지 않았다. 상세 확인은 checkbox를 자동 선택하지 않는다.
- Issues encountered:
  - 인앱 Browser runtime에는 사용 가능한 browser가 없어 직접 screenshot 검수는 수행하지 못했고 저장소 Playwright Chromium 회귀로 대체했다.
- Validation:
  - Frontend 집중 Vitest 18건, 회원가입 Chromium 1건과 전체 `corepack pnpm check` 67 files/276 tests·production build가 통과했다.
- Next steps:
  - 운영 전 개인정보 처리방침의 사업자·문의처·국외 이전 세부는 실제 운영 주체와 계약 조건으로 법률 검토한다.

## [2026-08-03] Session Summary (Job Analysis source 정규화·재시도·deadline 경계 재설계)

- What was done:
  - `EXTRACT_REQUIREMENTS` Provider 계약을 source-only v4로 교체하고 복합 조건의 atomic 분할·typed 분류·근무일·중복 제거·출처 추적을 서버 단일 정책으로 이동했다.
  - semantic correction과 transport retry counter를 분리해 network·timeout 뒤에도 마지막 correction을 유지했고 Job Analysis 단계별 prompt/token identity와 Chat wall-clock deadline을 적용했다.
- Key decisions:
  - 공개 API·DB·Flyway·Frontend·8단계 workflow version은 변경하지 않았다. 변경 없는 upstream `BUILD_SNAPSHOT`은 기존 v6 identity로 재사용하고, 새 실행은 source v4 schema/prompt/input hash로 `EXTRACT_REQUIREMENTS`와 실제 downstream만 새 checkpoint에 진입한다. 기존 terminal history 조회는 유지한다.
- Issues encountered:
  - P6/P7 전용 Browser E2E는 P6의 기존 `직접 입력해서 등록` UI locator를 찾지 못해 두 시나리오가 각각 300초·240초 timeout됐고, 선행 task 실패로 P7은 실행되지 않았다. 제품 분석 단계나 Fake Provider 응답까지 도달하지 못한 UI harness 실패다.
- Validation:
  - 정규화·Workflow·contract·strict schema·Orchestrator·OpenAI gateway 집중 테스트와 Backend 전체 `check` 78 suites/535 tests가 통과했다. 실제 Provider 호출은 0회다.
- Next steps:
  - 별도 승인 후 실제 Provider 수직 검증을 수행하고, 범위 밖 P6 UI locator를 현재 화면 계약에 맞춘 뒤 P6/P7 Browser E2E를 재실행한다.

## [2026-08-03] Session Summary (공고 분석 구조화 참조 복구와 실제 E2E 검증)

- What was done:
  - 최근 사용자 공고 분석 이력과 단계별 오류를 조회하고, 프로필 구조 확장 뒤 eligibility/match 모델 출력이 실제 입력 필드 경로와 다른 참조를 생성해 실패하는 문제를 실제 Provider E2E로 재현했다.
  - Job Analysis prompt v6에 `verifiedEvidence[].id`, `verifiedEvidenceCandidates[].evidenceId`, `structuredProfileFacts[].reference`의 정확한 복사 규칙을 명시하고, 잘못된 참조는 저장하지 않은 채 correction-once 경계로 보냈다.
  - 자동 분석 AFTER_COMMIT listener의 중첩 `REQUIRES_NEW`를 제거해 Hikari pool 2개 환경에서 발생하던 통합 테스트 connection 대기를 해소했다.
- Key decisions:
  - API·DB·8단계 workflow·점수·지원 가능 판정 규칙은 변경하지 않았고, 허용 목록 밖 참조를 삭제하거나 수용하지도 않았다. 입력/출력 구조 안내와 트랜잭션 경계만 현재 설계 계약에 맞췄다.
  - 실제 사용자 계정의 최신 실패는 분석 도중 eligibility version이 갱신된 정상 `RESOURCE_VERSION_CONFLICT`였으므로 재시도하거나 데이터를 변경하지 않았다.
- Issues encountered:
  - in-app Browser에 활성 runtime이 없어 저장된 사용자 세션 기반 UI 재현은 수행하지 못했고, 공개 API로 만든 합성 계정에서 실제 Provider E2E를 수행했다.
  - 최초 Backend 전체 `check`에서 `CoverLetterAgentRunIntegrationTest` 2건이 새 필수 eligibility fixture 부재로 `RESOURCE_NOT_FOUND` 실패했다. 프로덕션 로직 대신 공통 테스트 사용자 fixture에 기본 `UNSPECIFIED` eligibility 행을 추가해 구조 계약을 복구했다.
  - 최종 서버 재기동 첫 시도는 local profile 누락으로 provider activation 검증에서 즉시 종료됐고, `SPRING_PROFILES_ACTIVE=local`을 명시해 정상 재기동했다. 이 실패 시 외부 요청은 발생하지 않았다.
- Validation:
  - 최초 합성 E2E는 `ASSESS_ELIGIBILITY/JOB_ANALYSIS_EVIDENCE_INVALID`, 중간 E2E는 eligibility 통과 뒤 `MATCH_EVIDENCE` 참조 오류를 재현했다. 최종 Run `2cf7b812-b3ce-4091-b9e9-6e13c86546b6`은 prompt v6의 8단계를 모두 attempt 1로 통과하고 sealed 분석을 저장했다(10 usage records, USD 0.013896; 이 작업의 실제 E2E 총비용 USD 0.042607).
  - Job Analysis workflow/contract/strict schema 집중 테스트, `JobAnalysisIntegrationTest`, `JobAutoAnalysisIntegrationTest`, Backend 전체 `check` 525건, `docker compose config --quiet`, `git diff --check`가 통과했고 최종 local 서버 health는 `UP`이다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (AI 작업 복구·자료 재분석·공고 분석 실사용 복구)

- What was done:
  - 문서·공고 추출·공고 분석·자기소개서·면접 준비·답변 피드백 화면이 route 재진입 시 활성 Agent Run을 복구하고 동일 자원 중복 실행을 막도록 통합했다.
  - 자료 재분석을 수락하면 기존 문서 유래 경험을 같은 transaction에서 즉시 retire하고 새 분석만 다시 적재하게 했다.
  - 공고 분석 비교 단계의 장시간 network failure와 근거 유형 불일치를 보정했으며 공개 API 테스트 실행이 `PERSIST_ANALYSIS`까지 완료돼 분석 1건을 저장했다. OUTDATED 알림의 재분석 CTA는 한 개로 정리했다.
- Key decisions:
  - 모델이 허용된 근거를 잘못된 요건 유형에 연결하면 긍정 판정을 유지하지 않고 해당 항목만 `UNKNOWN`으로 강등한다. 같은 비교 오류의 retry는 성공 checkpoint를 재사용하고 보수 결과로 완료한다.
  - 외부 호출은 단계별 최대 3회, 전체는 provider call ID 기준 9회(Chat 7, Embedding 2)에서 중단했다.
- Issues encountered:
  - in-app Browser가 제공되지 않아 저장된 인증 Session을 사용한 실제 공개 API pipeline으로 대체했다.
  - Backend 전체 `check`는 기존 `DashboardMigrationTest`의 PostgreSQL Testcontainer 시작 대기에서 두 차례 timeout됐다. 변경 범위 단일-use 테스트는 통과했다.
- Validation:
  - Backend 관련 3개 테스트 클래스, Frontend `corepack pnpm check` 67 files/275 tests·typecheck·build, `docker compose config --quiet`, 실제 재시도 Run `5176b6a4-231f-4917-b7ca-c818871683a5` 성공과 신규 provider usage 0건을 확인했다.
- Next steps:
  - Docker/Testcontainers 시작 지연을 해소한 환경에서 Backend 전체 `check`를 다시 실행한다.

## [2026-08-02] Session Summary (Dashboard 본문·바로가기 정렬 분리)

- What was done:
  - Dashboard의 헤더·CTA·본문을 viewport 중앙 열에 맞추고 바로가기를 독립된 우측 sticky 레일로 분리했으며 page 계약과 Browser geometry 회귀를 동기화했다.
- Key decisions:
  - API·DB·Workflow와 전역 page 폭은 변경하지 않고 Dashboard presentation 범위만 수정했다.
- Issues encountered:
  - UI shell 전체 병렬 실행의 별도 프로필 제안 테스트 1건이 timeout됐지만 Dashboard 시나리오와 Frontend 전체 check는 통과했다.
- Validation:
  - Frontend 67 files/269 tests·build, Dashboard Chromium 1/1과 1440·1024·390px 시각 검수가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 분석 구조화 프로필·근거 호환성 보강)

- What was done:
  - 대표 학력과 지원 자격 자기신고를 공고 분석 snapshot에 포함하고, 검증 근거와 구분되는 구조화 fact provenance·strict support compatibility·입력 UI를 구현했다.
  - V19 forward migration과 additive profile eligibility API를 추가하고 활성 기능·API·DB·페이지·아키텍처 계약을 갱신했다.
- Key decisions:
  - 학력은 `profile_evidence`로 되돌리지 않고 owner-scoped 구조화 fact로 유지하며, section·scoring category·support type을 서로 독립된 계약으로 검증한다.
  - `UNSPECIFIED` 자기신고는 `UNKNOWN`, 졸업 예정일만 있는 근무 가능 조건은 보수적으로 `PARTIAL`/`CONDITIONAL`로 제한한다.
- Issues encountered:
  - Backend `*JobAnalysis*`와 전체 `check`는 기존 PostgreSQL 통합 테스트의 Hikari connection 대기에서 각각 184초·304초 timeout되어 전체 완료 여부를 확인하지 못했다.
  - OpenAPI exact test는 신규 GET의 자동 `400`, PUT의 CSRF `403` 기준선을 순차 확인하며 두 번 실패했고, 마지막 `403` 보정은 재시도 제한에 따라 컴파일만 확인했다.
- Validation:
  - Profile/Workflow/Hash 집중 테스트, V19 fresh·V18 upgrade migration 테스트, 신규 structured provenance 통합 테스트와 Frontend `pnpm check`(67 files/269 tests)가 통과했다. OpenAPI final exact assertion은 미검증이며 실제 외부 Provider 호출은 0회다.
- Next steps:
  - OpenAPI exact test를 다음 검증 회차에 1회 확인하고, 기존 Job Analysis 통합 suite의 connection 대기 원인을 별도로 조사해야 전체 Backend `check`를 완주할 수 있다.

## [2026-08-02] Session Summary (공고 분석·문서 소재 한국어 출력 보정)

- What was done:
  - 공고 분석 결과 hero 문구를 사용자 중심 문장으로 교체하고 내부 공고 경로를 `공고 본문`으로 치환했다.
  - 공고 적합도·강점·부족한 점과 이력서·자기소개서 추출 소재가 한국어로 생성되도록 prompt와 structured output 검증을 보강했다.
- Key decisions:
  - 공개 API·DB·output schema와 저장된 분석 버전 구조는 유지하고 prompt identity만 v3로 올려 이전 영어 checkpoint 재사용을 차단했다.
- Issues encountered:
  - 문서 prompt 계약 테스트의 줄바꿈 경계 assertion 1건이 실패해 의미 단위 assertion으로 교정한 뒤 재검증했다.
  - Backend 전체 `check`는 기존 `JobAnalysisIntegrationTest`의 자동 분석 이벤트에서 Hikari connection 대기가 지속돼 120초 제한을 넘겼으며 thread dump로 위치를 확인한 뒤 해당 테스트 프로세스만 종료했다.
- Validation:
  - Backend 변경 범위 집중 테스트 28건 통과. Frontend 67 files/267 tests, ESLint·Prettier·Vue typecheck·production build 통과. Backend 전체 suite는 위 connection 대기로 미완료다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 분석 embedding capability route 분리)

- What was done:
  - 하나캐피탈 공고의 최신 실패 run을 추적해 Chat용 `gpt-5-mini` route가 embedding 요청에 재사용되어 가격 catalog 검증 전에 실패한 원인을 확인하고, 공고 분석과 자기소개서 근거 검색이 활성 embedding policy의 provider·product·dimension을 사용하도록 수정했다.
  - route identity를 retrieval checkpoint hash와 safe refs에 포함하고 회귀 테스트·활성 명세·아키텍처 문서를 갱신했다.
- Key decisions:
  - Chat `ModelTier`와 vector embedding route를 분리하되 기존 immutable embedding policy v2를 재사용하므로 API·DB·workflow version과 migration은 변경하지 않는다.
- Issues encountered:
  - 전체 `check`는 수정 중 잘못 좁힌 지역 변수 타입 때문에 compile 단계에서 두 번 중단됐다. 정확한 executor 선언을 교정한 뒤 compile과 핵심 workflow 테스트는 통과했으며, 저장소 재검증 한도에 따라 전체 suite는 다시 실행하지 않았다.
- Validation:
  - `compileJava`와 Job Analysis·Cover Letter workflow focused test 통과. 수정 전 통합 범위인 Job Analysis/Auto Analysis/Embedding policy validator도 통과했다. 진단 승인 범위에서 실제 OpenAI embedding 호출은 정확히 1회 성공했고 구현 검증 중 추가 외부 호출은 0회였다.
- Next steps:
  - 배포 후 기존 terminal 실패 run은 변경하지 않고 사용자가 retry하면 현재 embedding policy route를 사용하는 successor run으로 재실행한다.

## [2026-08-02] Session Summary (공고 이미지 reference 전달 계약 구조 보정)

- What was done:
  - 지정 계정의 최신 실패를 read-only 진단해 Spring AI `Media.id/name`이 OpenAI 요청에 직렬화되지 않아 v3 output의 `imageRef` 검증이 실패한 원인을 확인했다.
  - 각 local reference text와 이미지 하나를 같은 Provider-visible message에 결합하고 reference 형식·중복을 호출 전에 검증했으며 실제 SDK 직렬화 회귀를 추가했다.
- Key decisions:
  - v3의 strict output allowlist·순서 복원은 완화하지 않는다. workflow/schema는 유지하고 이미지 prompt와 checkpoint association policy만 v4 identity로 분리한다.
- Issues encountered:
  - Backend 전체 508 tests 중 범위 밖 `InterviewApiIntegrationTest` 1건이 DB 무결성 오류로 실패했으나 정확한 단일 테스트 격리 실행은 통과했다. 첫 격리 명령은 package 오기재로 테스트를 찾지 못했다.
- Validation:
  - image gateway·Job extraction workflow 집중 테스트와 실패한 Interview 단일 테스트 격리 실행 통과. 전체 `check`는 508건 중 무관한 1건 때문에 최종 green이 아니며 실제 Provider 호출은 0회다.
- Next steps:
  - 보정 코드 배포 후 사용자가 기존 실패 공고를 retry하면 최신 v3 successor가 새 image prompt/checkpoint identity로 실행된다.

## [2026-08-02] Session Summary (JOB_ANALYSIS Provider 출력 계약 분리)

- What was done:
  - requirements·eligibility·match의 OpenAI 출력에서 서버 소유 재사용 상태를 제거하고 검증된 Provider DTO를 기존 내부 workflow DTO로 매핑했다.
  - 실제 strict schema, 8단계 신규·재사용, 세부 safe reason과 P6/P7 Fake 회귀를 보강했다.
- Key decisions:
  - 공개 8단계와 `job-analysis-v1` workflow version은 유지하고 변경된 세 schema와 prompt identity로 과거 Provider checkpoint 재사용을 차단한다.
- Issues encountered:
  - 저장소에 `spotlessApply` task가 없어 해당 명령은 실패했다. 최종 Backend 전체 check에서는 범위 밖 `ObjectDeletionOutboxIntegrationTest` 2건이 `PENDING` 상태로 남아 실패했으며, 해당 클래스 격리 실행은 통과했지만 허용된 전체 재검증에서도 같은 2건이 재발했다.
- Validation:
  - `*JobAnalysis*`, `*StrictStructuredOutput*`, `*OpenAiStrictSchema*` 집중 테스트와 Outbox 실패 클래스 격리 실행은 통과했다. 최종 Backend `check`는 74 suites/506 tests 중 범위 밖 Outbox 2건 실패로 미통과했고, 실제 Provider 호출은 0회다.
- Next steps:
  - 사용자가 실제 공고 분석을 1회 실행해 Run·step·safe error·usage를 확인한다.

## [2026-08-02] Session Summary (Dashboard 탐색·공고 분석 UX 보정)

- What was done:
  - Dashboard 중복 제목·캘린더 보조 문구와 오늘 버튼을 제거하고 일반 흐름 우측 바로가기, self-hosted variable Noto Sans KR 제목 typography를 추가했다.
  - 긴 공고 제목 한 줄 slide, 분석 진행 문구 nowrap, `BALANCED` 고정 요청과 품질 옵션 미노출, safe code별 사용자 실패 안내를 구현했다.
  - 지정 owner의 최근 실패 Run을 read-only 조회해 `EXTRACT_REQUIREMENTS`의 Java record validation이 correction 1회 뒤에도 실패한 사실을 확인했다.
- Key decisions:
  - Backend quality mode API 계약은 유지하고 현재 Frontend에서만 `BALANCED` literal을 전송한다. 계정 진단 중 재시도·데이터 수정·원문 조회는 수행하지 않았다.
- Issues encountered:
  - 첫 전체 check는 E2E 테스트 format에서, 첫 Browser 회귀는 검증 순서와 journey 노출 조건에서 중단됐으며 각각 테스트 계약을 보정한 뒤 통과했다.
- Validation:
  - `corepack pnpm check` 67 files/265 tests·typecheck·lint·format·build 통과, Chromium UI shell 3/3과 Job analysis 1/1 통과.
- Next steps:
  - 해당 실패의 정확한 invalid field는 보안 정책상 raw invalid output을 저장하지 않아 현재 metadata만으로 확정할 수 없다.

## [2026-08-02] Session Summary (Dashboard 캘린더 UI 완성도·hover 회귀 보정)

- What was done:
  - 캘린더 header·월 controls·요약, 날짜 grid·event chip·상태 표현을 제품형 일정 카드로 개선하고 workspace 문단 간격을 보정했다.
- Key decisions:
  - Dashboard frontend presentation만 변경하고 날짜별 count·선택 상세·서울 시간 API 계약은 유지했다.
- Issues encountered:
  - 선택된 오늘과 바로 다음 hover cell의 외곽 효과가 맞닿는 문제를 실제 첨부 화면 기준으로 재현해 gap·inset 강조로 수정했다.
- Validation:
  - Dashboard unit 5 tests, Chromium UI shell 3/3과 1440·1024·390px 시각·overflow·인접 cell gap 검증 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 주말·건수·장문 가이드 UI 보완)

- What was done:
  - Dashboard에 별도 사람 SVG, 이름 단독 theme 강조, 주말 색상·날짜별 `N건`, workspace 하단 CTA와 장문 Career Guide modal을 반영하고 V18 콘텐츠 migration을 추가했다.
- Key decisions:
  - V17은 불변으로 보존하고 V18은 version 1·미수정 seed만 version 2 장문 본문으로 갱신한다.
- Issues encountered:
  - 전역에 없는 Dashboard color alias를 page·Teleport modal 범위에서 기존 brand token으로 연결했다. Backend 전체 check는 기존 Interview 시간 경계 test 1개가 183ms timing 차로 실패했으나 동일 test 격리 재실행은 통과했다.
- Validation:
  - Frontend `pnpm check` 67 files/265 tests·build, Chromium 3/3, Dashboard migration test, `docker compose config --quiet` 통과. Backend 전체는 499 tests 중 1개 최초 실패 후 해당 test 격리 통과.
- Next steps:
  - 기존 Interview integration fixture의 transaction `now()` timing flake는 별도 안정화 대상이다.

## [2026-08-02] Session Summary (행동 중심 Dashboard·마감 캘린더·Career Guide)

- What was done:
  - owner-scoped Dashboard 정확 집계·서울 월별 마감 API와 게시 Career Guide DB/read API를 추가하고 Dashboard를 커리어 카드·다음 행동·캘린더·가이드 modal 중심 B2C 화면으로 재구성했다.
  - route workspace focus 이동은 유지하면서 비상호작용 main의 파란 outline/box-shadow만 제거하고 명세·계층 문서를 동기화했다.
- Key decisions:
  - 날짜별 count는 paginated 목록으로 추정하지 않고 전용 projection을 사용하며 `CLOSED`를 제외한다. 가이드는 V17 전역 게시 데이터로 제공하고 관리자 mutation은 범위에서 제외했다.
- Issues encountered:
  - in-app Browser 인스턴스를 사용할 수 없어 공식 서비스는 공개 웹 페이지로 확인했다. 첫 Backend 전체 check는 5분 도구 제한으로 결과가 완성되지 않았고 더 긴 단일 재실행이 통과했다.
- Validation:
  - Backend `gradlew check`: 73 suites/498 tests, 실패 0. Frontend `pnpm check`: 67 files/264 tests와 build 통과. Playwright Landing/UI shell 10/10, Docker Compose config 통과.
- Next steps:
  - Career Guide 관리자 UI·mutation API는 별도 Backoffice 범위에서 구현한다.

## [2026-08-02] Session Summary (공개 Landing 카피·Hero 후속 조정)

- What was done:
  - Landing Hero headline을 약 80% 크기로 조정하고 서비스 소개·5단계·핵심 가치·AI 활용 원칙 heading을 새 문구로 변경했다.
  - 자동 DOM 데모에서 수동 일시 정지·재생 control을 제거하면서 offscreen·background tab·reduced motion 정지 lifecycle은 유지했다.
- Key decisions:
  - 변경은 Frontend 공개 Landing에만 한정했으며 Backend·API·DB·인증·AI workflow와 dependency는 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - Landing component Vitest 10/10, Chromium 7/7과 1440·390·320px screenshot 검수가 통과했으며 외부 Provider 호출은 없었다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공개 Landing Hero·제품 motion 데모)

- What was done:
  - 공개 Landing Hero의 1440px 2줄 정보 계층과 하단 설명/데모 2열을 확정하고, 실제 경험·공고·분석·자기소개서·면접 준비 흐름을 5개 DOM scene으로 자동 순환하도록 개선했다.
  - viewport·background tab·수동 pause·reduced motion 정지와 progressive section reveal을 구현하고 공개 route·Guide·Dashboard 계약을 유지했다.
- Key decisions:
  - 첨부된 타 서비스 MP4는 11.8초 motion pattern 분석에만 사용하고 video asset, 타 서비스 copy/UI, audio와 새 animation dependency를 포함하지 않았다.
- Issues encountered:
  - 로컬 ffmpeg PATH 부재는 임시 분석 바이너리로 대체했고, Playwright screenshot의 sticky/reveal timing은 테스트 내부 캡처 절차로 안정화했다.
- Validation:
  - `corepack pnpm check`: 66 files/268 tests, lint·format·typecheck·production build 통과.
  - Landing Chromium 7/7과 1440·390·320px overflow·Hero/대표 scene/reduced-motion screenshot 검수가 통과했다. 실제 외부 Provider 호출은 없었다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공개 Landing·첫 사용 체크리스트 도입)

- What was done:
  - anonymous `/`에 서비스 가치·5단계 이용 흐름·제품 preview·AI 활용 원칙과 login/signup CTA를 제공하는 공개 Landing을 추가했다.
  - authenticated `/`의 bootstrap 선행 redirect, 공통 route title, PublicLayout Landing 복귀와 `/guide` 보호 CTA를 유지했다.
  - Dashboard에 profile·Document·Job의 실제 query 상태 기반 0/3~3/3 체크리스트를 추가하고 일반 현황·다음 할 일·최근 활동과 공존시켰다.
- Key decisions:
  - Landing과 Guide는 canonical 5단계 정의만 공유하고 공개 CTA와 보호 route action·preview는 페이지별로 분리했다.
  - query 실패는 미완료나 0개로 계산하지 않고 항목별 unknown·재조회 상태로 표시했다. Backend·DB·AI workflow 계약은 변경하지 않았다.
- Issues encountered:
  - in-app Browser 인스턴스가 없어 외부 서비스 화면을 이번 세션에서 다시 열지 못했고, 2026-08-02 저장소의 실제 Playwright 레퍼런스 조사 기록만 설계 근거로 사용했다.
  - 첫 Playwright 실행의 reduced-motion 시간 문자열과 query URL encoding assertion 2건을 브라우저 표현에 독립적으로 보정했다.
- Validation:
  - Frontend `corepack pnpm check`: 65 files/258 tests, lint·format·typecheck·production build 통과.
  - Playwright Landing 6/6, 기존 UI shell 3/3, 구현 전 visual baseline 1/1 통과. 1440·390·320px overflow, keyboard skip link, anchor, reduced motion, auth-aware `/`, 0/3~3/3과 4개 화면 캡처를 확인했다.
- Next steps:
  - 별도 Browser 인스턴스가 제공되면 국내 취업 서비스 공개 Landing의 최신 화면을 다시 확인할 수 있다.

## [2026-08-02] Session Summary (전반 UI/UX 재설계·공고 자동 분석 전환)

- What was done:
  - 실제 Jumpit·자소설 공개 화면과 현재 28장 기준선을 조사하고, Hiresemble blue를 유지한 상단 journey navigation·mobile bottom navigation·계정 메뉴·page variant·공통 token으로 전반 화면을 재설계했다.
  - 공고 plain text document view, 등록→추출→BALANCED 분석 journey, 결과 중심 분석 화면과 실제 component preview 기반 `/guide`를 구현했다.
  - V16 durable intent·unique revision·lease reconciliation·deterministic Agent Run ID로 브라우저와 독립된 자동 분석을 연결하고 기능·API·DB·페이지·architecture 계약을 동기화했다.
- Key decisions:
  - 기존 extraction/analysis workflow·Agent Run·`AiQualityMode`·재분석 API는 유지하고 최초 제품 정책만 BALANCED로 고정한다.
  - 원문은 `v-html` 없이 deterministic heading·paragraph·list·link node로 표시하고 편집 모드는 별도로 유지한다.
- Issues encountered:
  - 첫 Backend 전체 check의 OpenAPI allowlist 누락은 수정 후 단독 suite가 통과했다. 두 번째 전체 check는 기존 Interview fixture의 transaction timestamp 경계로 493건 중 1건 간헐 실패했고 해당 테스트 단독 재실행은 통과했다.
  - 모바일 공고 tab의 4px overflow와 새 heading/navigation 계층에 맞지 않던 E2E locator를 수정했다.
- Validation:
  - Frontend `corepack pnpm check`: 64 files/249 tests, lint·format·typecheck·build 통과. 실행 가능한 Chromium 9/9와 변경 후 30장 visual capture 통과.
  - Backend 자동 분석 2건·Job 7건·분석 4건과 OpenAPI suite 통과. 전체 `check` 최종 green은 위 Interview fixture 1건 때문에 미확인이다. 실제 Provider 호출 0회.
- Next steps:
  - Interview fixture의 terminal timestamp를 statement clock과 `created_at` 기준으로 별도 안정화한 뒤 Backend 전체 `check`를 재실행한다.
  - 전용 격리 Backend/DB 환경 flag가 필요한 P4~P8 `*.actual.spec.ts` 13건은 skip되어 후속 검증으로 남긴다.

## [2026-08-01] Session Summary (이미지형 채용 공고 추출 후속 계약 v3)

- What was done:
  - trusted `imageRef`, OpenAI adapter parity, legacy retry 승격, WebP와 짧은 이미지 aggregate를 Backend·AI workflow·문서에 구현했다.
  - Frontend 공개 계약은 유지하고 기존 retry/manual CTA·step fallback·SSE 회귀를 전체 check로 확인했다.
- Key decisions:
  - canonical은 `job-posting-extraction-v3`, v1·v2는 non-executable legacy이며 DB migration·공개 OpenAPI 변경은 없다.
  - 실제 OpenAI·외부 채용 사이트 호출 없이 Fake·synthetic WebP·mock model과 local E2E만 사용했다.
- Issues encountered:
  - 첫 전체 check에서 legacy definition 개수 assertion 1건과 무관한 Interview DB 간헐 실패 1건이 발생했다. assertion을 v1·v2 계약으로 고쳤고 Interview 단독 재실행과 최종 전체 check는 통과했다.
  - 존재하지 않는 `spotlessApply` task와 두 번의 도구 실행 timeout은 검증 성공으로 기록하지 않았다.
- Validation:
  - Backend `check --rerun-tasks`: 70 suites/491 tests, failure/error/skip 0. Frontend `pnpm check`: 61 files/243 tests. P5 Chromium 5/5, Compose config, dependency/log/secret/diff 검증 통과.
- Next steps:
  - animated WebP와 이미지 기반 PDF OCR은 계속 제외하며 실제 Provider/live 공고 검증은 별도 경계로 남긴다.

## [2026-08-01] Session Summary (이미지형 채용 공고 자동 추출·문자셋 보정)

- What was done:
  - EUC-KR/CP949 meta-only HTML strict decode, DOM 품질·이미지 후보 자동 판정, SSRF-safe 이미지 fetch와 OpenAI image text gateway를 구현했다.
  - `JOB_POSTING_EXTRACTION`을 v2 9단계로 올리고 semantic null/U+FFFD/본문 품질 검증, 자동 부족 시 manual fallback과 사용자 단계 문구를 연결했다.
- Key decisions:
  - OCR 선택 UI 없이 text-only는 image Provider 0회, image-only/mixed만 자동 image branch를 사용하고 v1 active run은 executable 없는 legacy 정의로 안전 격리한다.
  - DB/API schema는 유지해 migration 없이 V15 최신과 P8.6 tentative V16을 유지한다.
- Issues encountered:
  - 첫 전체 check의 canonical-count 테스트 2건과 첫 P5 browser fixture의 새 품질 threshold 불일치를 계약/fixture 범위에서 보정했다.
  - 최종 cache-free check에서 무관한 Interview timestamp 경계 테스트가 1회 간헐 실패했으나 단독 재현과 허용된 마지막 전체 재검증은 모두 통과했다.
- Validation:
  - Backend `gradlew check --rerun-tasks --no-daemon --console=plain --max-workers=1`: 69 suites/479 tests 통과. Frontend `corepack pnpm check`: 61 files/243 tests와 build 통과. P5 actual Chromium 5/5, `docker compose config --quiet`, `git diff --check` 통과. 실제 Provider 호출 0회.
- Next steps:
  - WebP는 현재 Java decoder allowlist 밖이며, 실제 외부 Provider/하나캐피탈 live 확인은 별도 승인된 local 검증에서 수행한다.

## [2026-08-01] Session Summary (이력서 소재 검토·사용자 대외활동 B2C UX 보정)

- What was done:
  - 문서 등록·분석·소재 검토 흐름을 상태 중심으로 재구성하고 일괄 승인·제외·재검토, 전문 문서 패널, 접힌 AI 상세, 전역 토스트·확인 모달을 구현했다.
  - AI 추출 경험과 분리된 사용자 직접 등록 대외활동 V15·소유권 기반 CRUD·소재 후보 정책을 Backend와 Frontend에 연결하고 기능·API·DB·페이지 명세를 동기화했다.
- Key decisions:
  - 문서 근거는 사용자 승인 항목만 활용하고, 직접 등록 대외활동은 `useAsMaterial=true`일 때만 동일한 verified snapshot을 통해 자소서·면접 후보가 되도록 했다.
  - 월간 사용량 API가 없는 현재 계약에서는 USD 금액을 숨기고 작업 예약 한도 대비 집계 비율만 사실대로 표시한다.
- Issues encountered:
  - 실제 브라우저 검수에서 Agent Run 전체 오류의 기술 문구와 실패한 소재 요약의 부정확한 `정리 중` 표시를 발견해 사용자용 안전 문구와 실제 실패 상태로 보정했다.
- Validation:
  - Backend `gradlew check --rerun-tasks --no-daemon --console=plain --max-workers=1`: 69 suites/469 tests 통과. Frontend `corepack pnpm check`: 61 files/243 tests, lint·format·typecheck·build 통과. `docker compose config --quiet`, `git diff --check` 통과.
  - local-offline Backend 8081과 Frontend 5174에서 실제 가입·대외활동 등록·새로고침 유지·삭제 모달 focus/ESC·TXT 업로드·분석 실패·재분석 확인·AI 상세 accordion·390px overflow를 Playwright로 확인했으며 외부 AI 호출은 0회였다.
- Next steps:
  - 월간 누적·전체 한도는 P8.7 집계 API가 구현될 때 연결하고, 실제 Provider가 허용된 환경에서 성공 분석 자료의 다중 소재 브라우저 흐름을 추가 검증한다.

## [2026-08-01] Session Summary (문서 candidate rejection terminal 오분류 보정)

- What was done:
  - 문서 candidate filtering을 failed scope에서 분리하고 workflow별 terminal partial policy, stable rejection reason 집계와 일부·전체 rejection 회귀를 구현했다.
  - 공용 Orchestrator의 자기소개서 전용 오류 하드코딩을 제거하고 활성 명세·설계·운영 상태를 최신 live 증거와 동기화했다.
- Key decisions:
  - candidate 0건 적용도 정상 command/finalize라면 문서와 Run 성공이며, `failedScopeKeys`는 실제 독립 scope 실패에만 사용한다.
- Issues encountered:
  - 기존 run에는 reason별 count가 없어 과거 두 rejection의 정확한 분류는 복구하지 않는다.
- Validation:
  - Backend 68 suites/466 tests와 `git diff --check`를 검증하며 실제 Provider 호출은 0회다.
- Next steps:
  - 이미 성공한 capability를 반복하지 않고 문서 Run terminal 상태를 bounded live 1회로 재검증한다.

## [2026-08-01] Session Summary (문서 근거 의미 계약·재시도·진단 강화)

- What was done:
  - 문서 evidence Provider output을 semantic field와 `C1` local ref로 축소하고 trusted server mapper, typed validation phase/reason, finish reason 분류와 bounded repair retry를 구현했다.
  - 공개 API·DB metadata 계약과 V1~V14 migration은 유지하고 활성 명세·설계·운영 handoff를 동기화했다.
- Key decisions:
  - 사용처 없는 Provider metadata와 server-owned identifier는 output에서 제거하며 parse/schema/binding은 1회, correction guidance가 있는 record/workflow 오류만 최대 2 attempt로 제한한다.
- Issues encountered:
  - 과거 run의 정확한 invalid field와 truncation은 기존 단일 safe code만으로 `NOT_VERIFIED`다. 전체 check 1차의 test-only catalog 오염 1건을 fixture에서 제거했다.
- Validation:
  - Backend `check --rerun-tasks` 68 suites/459 tests, failure/error/skip 0. 실제 Provider 호출 0회, OpenAPI·migration·Frontend 변경 0건.
- Next steps:
  - persistent Chat cap 2를 우회하지 않고 사용자가 versioned 1회 allowance를 별도 승인한 뒤 synthetic Chat→문서 ingestion을 각각 1회 수행한다.

## [2026-08-01] Session Summary (OpenAI strict Structured Output 호환성 수정)

- What was done:
  - 수정 전 evidence `metadata` bare object와 warning nullability 불일치를 재현하고 Provider entry output·중앙 schema registry/validator·오류 분류·safe fingerprint 진단을 구현했다.
  - 전수 keyword 감사에서 P7 공개 TipTap DTO의 `default` 유입을 추가 발견해 Provider 전용 recursive output과 bounded domain mapper로 제한 보정했다.
  - 관련 코드·테스트·기술/설계/운영 문서와 계층별 추적 문서를 동기화했다.
- Key decisions:
  - 동적 scalar metadata 의미, 기존 JSONB·공개 API·Frontend 계약과 migration은 보존한다.
  - schema 요청 거절은 non-retryable `STRUCTURED_SCHEMA`, 응답 검증 실패는 기존 `STRUCTURED_OUTPUT`으로 분리한다.
- Issues encountered:
  - 당시 OpenAI raw error code·param/request ID가 영구 보존되지 않아 장애 원인은 `HIGH_CONFIDENCE`이고 live 직접 확정은 아니다.
- Validation:
  - focused schema/Gateway/document/P7 generation 회귀와 Backend `check` 68 suites/452 tests, `git diff --check`가 통과했으며 실제 Provider 호출은 0회다.
- Next steps:
  - 사용자가 `codexRealOpenAiChatTest` 1회 성공 뒤 일반 local 문서 ingestion 1회를 수행하고 capability와 vertical을 별도 판정한다.

## [2026-08-01] Session Summary (로컬 OpenAI 연결 오류 보정과 bounded smoke)

- What was done:
  - OpenAI SDK base URL을 `/v1`로 보정하고 embedding 정책 provider key를 V14로 `openai`에 canonicalize했으며 Chat/Embedding safe rejection 진단을 추가했다.
  - 기존 local Backend를 재시작해 V14 적용과 health `UP`을 확인했다.

- Key decisions:
  - 과거 embedding 정책 version 1은 immutable history로 보존하고 version 2만 활성화한다.
  - 빈 tool allowlist에서는 `tool_choice`와 `parallel_tool_calls`를 전송하지 않는다.

- Issues encountered:
  - 실제 Chat·Embedding은 OpenAI `429 insufficient_quota`로 차단됐고 Tavily BASIC만 성공했다.

- Validation:
  - Backend `check` 67 suites/427 tests, failure/error/skip 0; Compose config와 local health가 통과했다.
  - bounded smoke 누적은 Chat 2회 실패, Embedding 1회 실패, Tavily 1회 성공이며 추정 상한 합계는 USD 0.008201이다.

- Next steps:
  - OpenAI 프로젝트 크레딧/월 한도를 복구한 뒤 Chat·Embedding capability smoke를 사용자가 재실행한다.

## [2026-08-01] Session Summary (P8.5 이후 운영 기반 및 P9 이전 구현 계획 재설계)

- What was done:
  - 최신 `main`과 P8.5 Provider adapter·profile·V13 usage/cost 구현을 저장소에서 재검증하고, P8.5-V부터 P10-C까지의 단계·활성 명세·운영 계약을 문서로 재설계했다.
  - budget·quota·usage accounting·payment 경계를 분리하고 AC-14~AC-17, 공통 AI 실패 UX, ADMIN Backoffice와 미래 P9/P10 handoff를 추가했다.
- Key decisions:
  - 실제 결제·구독은 계속 제외하고, 제품 기능 한도와 Provider USD budget을 독립 적용하며 과금 가능 unit은 `feature_usage_events`의 immutable snapshot으로 저장하되 고객 청구액은 0으로 둔다.
  - P8.9-A는 읽기 전용 운영 조회, P8.9-B는 감사 가능한 제한 mutation의 후속 단계로 분리하고 P9의 필수 선행은 P3·P8.5-V·P8.6·P8.7·P8.8·P8.9-A로 고정했다.
- Issues encountered:
  - OpenAI Chat·Embedding과 Tavily 실제 호출 기록은 모두 0회여서 P8.5를 완료 처리하지 않았다. `gh` CLI가 없어 원격 CI 상태는 확인하지 못했고 원격 `main` HEAD와 저장소의 로컬 검증 기록만 구분해 확인했다.
- Validation:
  - `git diff --check`, `docker compose config --quiet`, 변경 Markdown Prettier 검사, 상대 링크·progress 형식·절대 로컬 경로·비밀 패턴 검사를 통과했다.
  - 문서 전용 작업이므로 Backend·Frontend 제품 테스트와 실제 Provider·P4~P8 E2E는 실행하지 않았으며 기존 67 suites/420 tests, 60 files/238 tests와 actual 결과는 기록값으로만 재검증했다.
- Next steps:
  - 사용자가 일반 `local` profile에서 P8.5-V capability smoke와 P4~P8 수직 흐름을 1회 검증하고, 병행 가능한 첫 코드 단계로 P8.6 제품 기능 한도·metering 기반을 구현한다.

## [2026-08-01] Session Summary (P8.5 외부 AI Provider 연결·로컬 활성화 gate)

- What was done:
  - Spring AI 2.0 OpenAI Chat·Embedding adapter, Tavily bounded stream, capability별 Bean, local/local-offline profile, V13 immutable 가격 catalog과 다중 usage ledger를 구현했다.
  - Codex bounded live task와 운영 문서를 추가하고 P9는 시작하지 않았다.
- Key decisions:
  - `local`은 real OpenAI/Tavily를 fail-closed로, `local-offline`·test·CI·E2E는 disabled/Fake로 고정하고 Spring AI provider retry는 0으로 둔다.
  - workflow 접수 예약은 기존 async run absolute cap USD 0.30 전액으로 고정한다.
- Issues encountered:
  - 전체 check의 Testcontainers 연결 상한을 test Hikari pool 3개로 조정했고, P5 actual의 과거 UI 문구 fixture를 현재 표시 계약에 맞췄다.
  - 1차 read-only self-audit에서 populated V12→V13·V1~V12 checksum 전용 검증 누락을 발견해 한 번의 제한 보정으로 추가했다.
- Validation:
  - Backend 67 suites/420 tests, Frontend 60 files/238 tests, P8 1/1·P7 1/1·P6 2/2·P5 5/5·P4 4/4 actual, Compose·diff check가 통과했다.
  - live gate/key가 없어 Codex real-provider task는 skip됐고 실제 외부 호출은 Chat 0, Embedding 0, Search 0이다.
- Next steps:
  - 승인된 key가 있는 환경에서 bounded live task를 capability별 1회 실행한 뒤 P9 착수 여부를 판정한다.

## [2026-07-31] Session Summary (P8 면접 조사·예상 질문·답변 피드백 구현)

- What was done:
  - V12 조사·질문·답변·피드백 schema, 공개 API 11개, 실행 가능한 준비 10단계·피드백 5단계 workflow와 Tavily opt-in adapter를 구현했다.
  - 공고 면접 tab, 질문 세트 목록·상세, 출처 coverage, immutable 답변 version CAS와 feedback UI를 기존 responsive shell에 연결했다.
  - P8 actual Browser E2E와 DB assertion을 추가하고 final source에서 P7/P6 actual 회귀까지 다시 실행했다.
- Key decisions:
  - 검색에는 공개 회사·직무 최소 정보만 전달하고 학력은 structured final education으로만 사용하며 education evidence tombstone을 positive provenance로 연결하지 않는다.
  - Agent Run history soft delete는 P8 domain 결과를 보존하고 resource/generic retry는 공통 predecessor claim을 사용한다.
  - 사용자 지시에 따라 분석부터 검증·문서화까지 서브 에이전트 없이 단일 에이전트가 순차 수행했다.
- Issues encountered:
  - actual E2E에서 persist 전 source allowlist 순서와 answer history SQL 공백 결함을 발견해 회귀 테스트와 함께 보정했다.
  - 전체 test fixture의 JVM·DB clock 차이는 제품 결함이 아닌 `TEST_HARNESS_DEFECT`로 분류해 terminal 시각 helper만 보정했다.
  - 1차 read-only self-audit에서 output 전용 `FOLLOW_UP` 거부와 foreign feedback/research retry의 quality 오류 선노출을 확인해 허용된 한 번의 제한 보정으로 owner 404 우선순위와 output 계약을 바로잡았다.
- Validation:
  - 제한 보정 뒤 Backend 61 suites/407 tests, Frontend 60 files/238 tests, OpenAPI 63 paths/84 operations, P8/P7/P6 actual Chromium 1/1·1/1·2/2와 DB assertions가 통과했다.
  - `docker compose config --quiet`, `git diff --check`가 통과했고 Fake Chat/Search만 사용해 실제 OpenAI·Tavily 호출은 0회였다.
  - 두 번째 single-agent read-only self-audit는 finding 0으로 통과했고 감사 전후 178개 변경 파일 fingerprint가 `6cc19fff43393713a8a1276297144f1bd916ca3bfe0155cc7140ef909d5eff08`로 동일했다.
- Next steps:
  - P9 모의 면접은 별도 요청에서 새 migration·API·workflow·화면으로 시작한다.

## [2026-07-31] Session Summary (헤더 닉네임·최종 학력 자동 판정 UI)

- What was done:
  - 대외활동 안내를 승인·거절 두 항목으로 정리하고, AI 작업 선택/삭제 문구와 관심 공고 active hover를 보정했다.
  - 기본 정보의 닉네임 field를 제거하고 상단 닉네임 클릭 Modal로 옮겼으며, 학력 단계와 서버 계산 최종 학력 배지를 추가했다.
  - V11에서 기존 학력 단계를 backfill하고 현재 개발 DB까지 단계·상태·날짜 순으로 최종 학력을 재계산했다.
- Key decisions:
  - 최종 학력은 `고등학교 < 전문학사 < 학사 < 석사 < 박사`를 우선하고 같은 단계에서는 상태·날짜·등록 순서로 결정하며 client의 수동 지정은 허용하지 않는다.
  - 사용자 요청대로 서브 에이전트 없이 단일 에이전트에서 구현·검증했다.
- Issues encountered:
  - AppLayout CSS media query 닫힘 누락을 production build에서 발견해 보정했다.
  - 연결 가능한 실제 브라우저가 없어 Browser 기반 시각 검증은 수행하지 못했고 component tests와 production build로 대체했다.
- Validation:
  - Backend `.\gradlew.bat check`, Frontend `corepack pnpm check`, V11 migration upgrade test와 개발 DB Flyway 11·최종 학력 invariant를 검증했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (대외활동·학력 제외와 AI 작업 내역 삭제)

- What was done:
  - 프로필 기본 정보 저장 영역을 Form 하단으로 이동하고, 경험 정보를 대외활동으로 바꾸며 filter 간격과 학력 상태 한국어 표시를 보정했다.
  - 학력 direct evidence 생성·동기화를 제거하고 문서 추출 교육 category까지 server·DB에서 차단했으며 기존 개발 DB의 학력 근거를 비식별 tombstone으로 전환했다.
  - terminal AI 작업 내역을 개별 또는 현재 페이지 선택으로 삭제하는 owner-scoped soft delete API·UI를 추가했다.
- Key decisions:
  - 승인·거절은 AI 문서 추출의 오탐을 검토하고 `VERIFIED`만 공고 분석·자기소개서·면접에 쓰는 핵심 경계라 유지하되 직접 입력 근거에서는 UI를 숨기고 API도 거부했다.
  - 신뢰도는 문서 추출 확신도이지 사실 보증 점수가 아니며, Agent Run 삭제는 비용·lineage·산출물 audit을 보존하는 terminal-only soft delete로 정의했다.
  - 사용자 요청에 따라 서브 에이전트 없이 단일 에이전트로 구현·검증했다.
- Issues encountered:
  - V9 초안의 deferred profile trigger 순서 문제는 적용 전 보정했고, V10 적용용 non-web bootRun은 migration 성공 뒤 `HttpSecurity` bean 부재로 종료됐다.
  - Frontend 전체 check 중 수정 파일 4개와 마지막 학력 상태 mapping 1개의 Prettier 차이를 각각 대상 파일 format으로 해소한 뒤 재검증했다.
- Validation:
  - Backend `.\gradlew.bat check`: 54 suites, 385 tests 모두 통과.
  - Frontend `corepack pnpm check`: 53 files, 215 tests, typecheck·lint·format·production build 모두 통과.
  - `docker compose config --quiet`, `git diff --check` 통과. 개발 PostgreSQL Flyway V9·V10 성공, active 학력 evidence 0건·sanitized tombstone 1건·차단 constraint 1개를 확인했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (프로필 UI 정리·닉네임 변경)

- What was done:
  - 프로필 하위 내비게이션의 부가 설명을 제거하고 첫 진입 scroll·focus와 sticky offset을 보정해 전체 항목이 고정 헤더 아래 한 화면에 노출되도록 했다.
  - 프로필 저장 바의 좌우 테두리·내부 여백을 복구하고 자기소개서 검색·상태·정렬 filter 사이에 12px 간격을 적용했다.
  - 프로필 기본 정보의 단일 저장 action에 닉네임 변경을 연결하고 `PATCH /api/v1/account/display-name`과 DB 기반 최신 사용자 projection을 구현했다.
- Key decisions:
  - 프로필 본문과 닉네임은 하나의 사용자 action으로 검증하되, 본문 저장 성공 뒤 닉네임만 실패하면 부분 저장을 알리고 닉네임 dirty 상태를 유지한다.
  - Session principal을 일괄 교체하지 않고 `GET /auth/me`가 사용자 ID로 DB를 다시 조회해 모든 Session에 최신 닉네임을 제공한다.
- Issues encountered:
  - OpenAPI 첫 검증에서 새 operation description 누락을 발견해 보완한 뒤 단일 재검증으로 통과했다.
  - 브라우저가 연결된 기존 8080 서버는 변경 전 process여서 새 경로가 404였고, 최신 source API는 Spring 통합 테스트로 검증했다.
- Validation:
  - Backend `.\gradlew.bat check --console=plain --no-daemon`: 54 suites/382 tests, failure·error·skip 0, OpenAPI 71 operations/52 paths.
  - Frontend `corepack pnpm check`: 53 files/214 tests, lint·format·typecheck·build 통과.
  - Playwright CLI 1440×1000 실브라우저 검수에서 profile sidebar 7개 전체 노출, save bar 좌우 1px border·16px padding, Cover Letter filter 간 12px gap을 확인했다.
- Next steps:
  - 로컬에서 이미 실행 중인 Backend process는 새 닉네임 endpoint를 사용하려면 최신 source로 재시작한다.

## [2026-07-30] Session Summary (P7 최종 validator PASS·완료)

- What was done:
  - 허용된 두 번째 read-only validator가 두 MAJOR 해소, P7 전체 계약·범위·DB·API·AI·Frontend와 actual 증거를 독립 재검증해 새 finding 없이 `PASS`를 반환했다.
  - validator 전후 204개 변경 항목의 all-file fingerprint가 `2006b09883a04155a4e90aeb0996f5b2f76d9ae5`로 동일함을 확인하고 P7 상태를 `DONE`으로 확정했다.
- Key decisions:
  - P7 완료는 구현 완료가 아니라 Backend·Frontend 전체 검사, P7 actual·DB assertion, 최종 source P6 회귀와 독립 validator가 모두 통과한 상태를 뜻한다.
  - P8은 다음 단계로만 기록하며 이번 작업에서는 route·API·migration·구현을 시작하지 않는다.
- Issues encountered:
  - `codex doctor`의 로컬 설치 경로·thread rollout scan 경고는 validator가 저장소 또는 P7 결함이 아닌 환경 경고로 확인했다.
- Validation:
  - Validator `PASS`, finding 0, files changed 0.
  - Backend 54 suites/380 tests, Frontend 53 files/211 tests, OpenAPI 51 paths/70 operations, P7 Chromium 1/1·DB assertions와 P6 Chromium 2/2·DB assertions가 통과했다.
- Next steps:
  - 다음 구현 단계는 P8 면접 준비이며 별도 요청에서 계약을 다시 고정한 뒤 시작한다.

## [2026-07-30] Session Summary (P7 validator MAJOR 보정·final-source 재검증)

- What was done:
  - 1차 read-only validator가 지적한 verification suggestion 계약 불일치와 일반화된 409 비교 UI를 공개 계약 기준으로 보정했다.
  - Backend OpenAPI, AI structured output, Frontend Zod를 suggestion 최대 20개·항목 1~1000자로 통일하고 TITLE·QUESTION·ORDER·ANSWER·LIFECYCLE별 immutable 사용자 snapshot과 최신 server CAS 재적용을 구현했다.
  - verification 집계가 두 유효 단계의 제안을 합칠 때도 공개 최대 20개를 넘지 않도록 제한하고 경계 회귀를 추가했다.
- Key decisions:
  - 409는 자동 재시도하지 않으며 실제 최신 server field와 최초 제출 snapshot을 비교한 뒤 사용자의 명시적 재적용에서만 최신 CAS를 결합한다.
  - 첫 validator FAIL은 숨기지 않고 허용된 단일 보정 라운드로 처리했으며 최종 validator 전에는 P7을 `DONE`으로 올리지 않는다.
- Issues encountered:
  - 질문 refetch가 편집 form을 갱신해도 재적용 대상이 reactive 값으로 바뀌지 않도록 작업별 snapshot을 mutation 전에 고정했다.
- Validation:
  - Backend `check` 54 suites/380 tests, Frontend `check` 53 files/211 tests, OpenAPI 51 paths/70 operations가 통과했다.
  - P7 actual Chromium 1/1은 실제 문항 `PUT 409 → 사용자 재적용 PUT 200`과 후속 DB assertion을 포함해 통과했고, 같은 source의 P6 회귀 Chromium 2/2와 DB assertion도 통과했다.
  - `docker compose config --quiet`, `git diff --check`, P8·raw HTML·prompt/response log·localStorage 본문 정적 감사가 통과했다.
- Next steps:
  - 변경 불가 read-only validator를 한 번 재실행해 최종 P7 판정을 고정한다.

## [2026-07-30] Session Summary (P7 자기소개서 수직 기능 구현·actual 검증)

- What was done:
  - V8 owner-scoped schema, 자기소개서·문항·immutable answer version·provenance·verification·finalize/archive API와 application port를 구현했다.
  - 고정 generation 8단계·verification 6단계, 문항별 partial success/retry·restart-safe apply와 `/cover-letters`·공고 tab·canonical editor·session draft·409 비교 UX를 연결했다.
  - 실제 PostgreSQL·MinIO·Fake AI·Spring·Vue·Chromium 시나리오로 생성→문항→부분 생성→수정 저장→검증→복원→최종화→보관·근거 수명주기·사용자 격리를 검증했다.
- Key decisions:
  - server가 answer source·TipTap canonical 글자 수·verification freshness·finalization을 결정하며 AI는 Backend query/command port만 사용한다.
  - 과거 provenance는 유지하고 현재 `REJECTED|SOURCE_DELETED` 근거는 새 생성·검증에서 제외한다. 공고 `SUBMITTED` 전이는 자기소개서 최종화와 분리한다.
- Issues encountered:
  - actual E2E에서 Vue number input을 문자열로만 처리한 form parser 오류를 발견해 string/number 공용 parsing과 component regression으로 보정했다.
  - 초기 E2E 대기는 mutation 응답과 UI postcondition을 명시적으로 기다리도록 보강했다.
  - 최종 개인정보 감사에서 verification provenance claim text의 checkpoint 경로를 발견해 해당 로컬 단계를 non-reusable로 만들고 hash·ID·count만 저장하도록 보정했다.
- Validation:
  - `backend\gradlew.bat check`: 54 suites/377 tests, 실패·오류·skip 0.
  - `corepack pnpm check`: 53 files/204 tests, lint·format·typecheck·build 통과.
  - P7 actual Chromium 1/1과 wrapper DB assertions, 최종 source P6 회귀 Chromium 2/2와 DB assertions, OpenAPI 51 paths/70 operations, `docker compose config --quiet`, `git diff --check`가 통과했다.
- Next steps:
  - 독립 read-only validator 판정 후 P7 완료 여부를 확정한다.

## [2026-07-30] Session Summary (P6 final-source actual 검증 게이트 종료)

- What was done:
  - 현재 `main@0ad9bdec5a2abf806b9c812c705c00c48e5217db`에서 P6 actual wrapper를 재실행해 정상 분석·reuse·OUTDATED·재분석·근거 부족과 공고·분석·evidence·Run owner 격리 시나리오를 닫았다.
  - 최초 실행에서 후속 DB assertion의 잘못된 `safe_error_code` 컬럼명을 실제 V4 계약인 `error_code`로 한정 보정했다.
- Key decisions:
  - 실패는 제품 동작이 아니라 `TEST_HARNESS_DEFECT`로 분류했고 assertion 1줄 외 P6 구현·공개 계약은 변경하지 않았다.
  - 기존 validator가 atomic apply와 historical evidence MAJOR 해소를 이미 확인했고 유일한 잔존 사유였던 final-source actual 증거가 닫혀 P6를 `DONE`으로 판정했다.
- Issues encountered:
  - 첫 wrapper에서 Playwright 2개는 exit 0이었지만 존재하지 않는 DB 컬럼 조회로 JUnit wrapper가 실패했다.
- Validation:
  - `.\gradlew.bat p6BrowserE2eTest --rerun-tasks --info --no-daemon --console=plain`: Chromium 2/2, JUnit 1/1과 모든 후속 DB assertion 통과, `BUILD SUCCESSFUL in 1m 18s`.
  - Spring·Vite·Chromium·Testcontainers 소유 프로세스가 종료됐고 `git diff --check`가 통과했다.
  - 고정 SHA-256: V7 `7D7B0088…EB217C`, P6 wrapper `8E1B74D3…205240`, actual spec `68FF147E…F559547`, JobAnalysisWorkflow `6D7B8242…A81C10`.
- Next steps:
  - P7 계약을 고정한 뒤 자기소개서 Backend → AI workflow·Frontend → 독립 validator 순서로 구현한다.

## [2026-07-29] Session Summary (P6 공고 분석·RAG 수직 기능 구현)

- What was done:
  - V7 immutable 분석·criterion·evidence provenance, 3개 Job Analysis API, 고정 8단계 `JOB_ANALYSIS`, verified evidence RAG와 `/jobs/:jobId/analysis` 결과·이력·OUTDATED UI를 구현했다.
  - `/agent-runs`는 `AI 작업 내역`으로 정리하고 분석 결과를 복제하지 않은 채 해당 공고 분석 화면으로 연결했다.
- Key decisions:
  - 최종 점수는 Java 정책만 계산하고 Eligibility와 분리하며, 동일 snapshot은 새 Run에 기존 analysis를 연결하고 `forceReanalyze=true`만 새 version을 만든다.
  - Agent Run primary resource는 접수 시 존재하는 `JOB`으로 유지하고 성공 뒤 immutable `JOB_ANALYSIS` secondary resource link를 추가한다.
  - 성공·재사용 step checkpoint와 domain apply는 외부 호출 뒤 `SERIALIZABLE` transaction에서 함께 commit하고, 과거 분석 근거의 현재 상태 변경은 결과를 숨기지 않고 OUTDATED 안내로 표시한다.
- Issues encountered:
  - 실제 P6 Browser E2E 첫 실행은 중복 제목 locator, 두 번째는 계약에 없는 evidence 단건 GET assertion 때문에 종료됐다. assertion은 공개 PUT endpoint의 타 사용자 404로 수정했지만 재검증 상한에 따라 세 번째 실행은 하지 않았다.
  - 1차 read-only validator는 checkpoint 뒤 별도 분석 적용과 변경·삭제된 historical evidence를 거부하는 Frontend 계약을 MAJOR로 판정했다. 허용된 보정 라운드에서 원자 완료 경계와 역사 결과 유지 계약·회귀 테스트를 추가했다.
- Validation:
  - 보정 후 Backend `check`: 44 suites, 352 tests, 실패 0. Frontend `check`: 42 files, 169 tests와 lint·format·type·build 통과.
  - P6 migration 3/3, 생성 OpenAPI 53 operations/37 paths, fixture Chromium 3/3, `docker compose config --quiet`와 `git diff --check`가 통과했다.
  - 2차 read-only validator는 atomic apply와 historical evidence finding 해소를 확인했지만 actual wrapper 최종 source가 실행되지 않아 전체 verdict를 `FAIL`로 유지했다. validator 전후 138개 변경 파일 지문은 `053fb0bf5a4adfd3005b4733a4c32ae82fe78688b871612ee9c3e4952e51ba15`로 동일했다.
- Next steps:
  - 이 요청에서는 추가 자동 수정·검증을 수행하지 않는다. 새로 승인된 검증 주기에서 current P6 wrapper의 두 Playwright scenario와 후속 DB assertion을 모두 통과시켜야 한다.

## [2026-07-28] Session Summary (지원 대시보드·프로필 관리 UI 전면 개선)

- What was done:
  - `/dashboard`를 소개형 링크 화면에서 프로필 완성도, 지원 상태, 문서, Agent Run, 다음 할 일과 최근 활동을 실제 사용자 데이터로 보여 주는 지원 관리 화면으로 재구성했다.
  - 프로필 기본 정보 화면을 평면 내비게이션과 단일 편집 영역으로 재구성하고 기본 정보, 자기소개, 희망 조건의 입력 위계와 저장 상태를 명확히 했다.
- Key decisions:
  - 명세에는 `DashboardDto`가 있지만 현재 Backend 계약에는 `/api/v1/dashboard`가 없으므로 API를 추가하거나 수치를 추측하지 않고 기존 Profile·Document·Job·Agent Run API의 `totalElements`와 반환된 최근 항목만 조합했다.
  - route, 공개 DTO, Profile version·409 conflict, 인증·CSRF, Vue Query·Pinia와 기존 mutation 책임은 유지했다.
- Issues encountered:
  - 연결 가능한 in-app browser가 없어 레퍼런스의 로그인 후 화면을 직접 탐색하지 못했고, 자소설닷컴 경력 관리 URL은 공개 접근이 되지 않아 공개 페이지와 현재 화면 계약을 기준으로 원칙만 적용했다.
- Validation:
  - `corepack pnpm check`: ESLint, Prettier, TypeScript, Vitest 40 files/154 tests와 production build 통과.
  - fixture Chromium UI shell 3/3 통과; 1920·1440·1280·1024·768·390px에서 Dashboard와 Profile을 확인하고 1440·390px 스크린샷을 직접 검토했다.
- Next steps:
  - 전용 Dashboard API가 구현되면 현재 조합 query를 `DashboardDto`로 교체하고 자기소개서·모의 면접 최근 활동을 서버 집계에 연결한다.
  - 실제 Backend를 포함한 Profile·Document·Job cross-stack 흐름과 screen reader 실기기 검증은 이번 fixture 기반 시각 작업 범위에서 재실행하지 않았다.

## [2026-07-28] Session Summary (첨부 화면 기반 Frontend 시각 완성도 보정)

- What was done:
  - 초대형 화면의 인증 레이아웃 폭과 간격, Dashboard 제목 줄바꿈, 지원 정보 outline 인접 hover 간격을 보정했다.
  - 프로필 작성 안내를 Hiresemble Blue 계열로 통일하고 기본 정보·지원 희망 조건의 시각적 구분과 자료·공고 필터 간격을 강화했다.
- Key decisions:
  - route, API, DTO, 상태 관리와 기존 입력·저장 동작은 바꾸지 않고 scoped layout·presentation 범위에서만 보정했다.
  - 작은 화면에서는 Dashboard 제목이 의미 단위 두 줄로 읽히도록 하고 480px 이상에서는 한 줄을 유지하도록 했다.
- Issues encountered:
  - 브라우저 mock route를 CLI 문자열로 전달할 때 PowerShell quoting 때문에 응답이 깨져 JavaScript route fulfillment로 전환했다.
  - 첫 390px 캡처에서 제목이 세 줄로 나뉘고 장식과 가까워져 font와 설명 폭을 한 차례 보정했다.
- Validation:
  - Frontend `corepack pnpm check`가 39 files/149 tests와 production build까지 통과했고 fixture Playwright UI shell 3/3이 통과했다.
  - 2500px 인증, 1574px·390px Dashboard, 1440px·390px 프로필, 1600px 자료·공고 필터를 직접 캡처했으며 390px Dashboard와 프로필의 horizontal overflow가 0임을 확인했다.
- Next steps:
  - 실제 Backend 연동 E2E는 이번 scoped 시각 보정에서 재실행하지 않았으므로 기존 cross-stack 검증 이력을 따른다.

## [2026-07-28] Session Summary (Hiresemble Frontend 전체 UI·UX 재설계)

- What was done:
  - 현재 18개 사용자 route와 404를 Hiresemble Blue 디자인 시스템으로 통합하고 프로필 가로 tab을 Career Profile Workspace의 세로 outline·mobile selector로 교체했다.
  - 자료·공고·분석 기록의 filter, form, 상태와 다음 행동을 취업 준비생 중심의 정보 계층으로 정리했다.
- Key decisions:
  - route·API·DTO·DB·Vue Query·CSRF·idempotency·409·SSE와 Document·Job 독립 상태 축은 변경하지 않았다.
  - 대외활동 생성과 공고 분석 route는 현재 계약에 없어 가짜 화면이나 저장 기능을 만들지 않았다.
- Issues encountered:
  - 대화형 브라우저 세션과 Behance 원문 접근이 불가능해 확인 가능한 공개 사이트와 Playwright 실제 화면 검수만 근거로 사용했다.
  - 실제 Document E2E는 실행 중인 Backend의 upload 오류로 첫 시나리오가 timeout됐고 후속 직렬 시나리오는 실행되지 않았다.
  - 실제 Profile E2E는 구 selector를 한 차례 보정한 뒤 완료율 `100%`의 strict locator 중복으로 두 번째 검증도 실패해 규칙상 중단했다.
- Validation:
  - Frontend `corepack pnpm check`가 39 files/149 tests와 production build까지 통과했고 fixture Playwright 5/5가 통과했다.
  - 1440·1024·768·390px 18개 화면 캡처와 overflow 검사가 통과했으며 실제 Profile 0/1·Document 0/4 완료로 남겼다.
- Next steps:
  - Backend를 현재 local 설정으로 재시작한 뒤 실제 Document E2E 4개와 fixture URL이 필요한 Job actual E2E를 재실행한다.

## [2026-07-28] Session Summary (이력서 업로드 복구와 프로필·자료 UX 고도화)

- What was done:
  - 실제 CSRF·가입·multipart 요청으로 `/api/v1/documents`의 500을 재현하고 로컬 멱등성 HMAC 설정을 복구했다.
  - 이메일 형식 검증, 닉네임·분석 기록 용어, 졸업(예정)일, 한국형 직무·지역 빠른 선택과 포함 검색 추천을 적용했다.
  - 프로필과 자료 등록을 편집형 지원 브리프와 파일 선택·분류·분석 흐름으로 재설계했다.
- Key decisions:
  - API·DB의 `displayName`, `expectedGraduationDate`와 Agent Run 계약은 유지하고 사용자 노출 언어만 바꿨다.
  - 알려진 개발 키는 명시적 `local` profile에만 두고 profile 미지정·비로컬 환경은 key 누락 시 시작을 거부한다.
- Issues encountered:
  - 대화형 브라우저 연결이 없어 저장소 Playwright와 실제 HTTP 재현으로 대체했다.
  - 실행 중인 8080 프로세스는 이전 설정을 사용하므로 새 설정 적용에는 재시작이 필요하다.
  - 1차 validator 지적은 모두 보정했지만 최종 독립 재검증은 모델 용량 부족으로 실행되지 않아 `NOT_VERIFIED`로 남겼다.
- Validation:
  - Backend `check`, Document 12 tests, fail-closed 설정 회귀와 실제 P4 Browser E2E 4/4가 통과했다.
  - Frontend `check` 37 files/145 tests, Playwright UI shell 3/3과 Agent Run fixture 2/2가 통과했다.
  - Root diff·설정·문구 검사는 통과했으며 최종 validator 판정만 인프라 오류로 확보하지 못했다.
- Next steps:
  - 백엔드를 명시적 local profile로 재시작하고 운영에서는 충분한 엔트로피의 HMAC secret을 주입한다.

## [2026-07-28] Session Summary (Frontend B2C UX Writing·Brand Experience 재설계)

- What was done:
  - 현재 구현된 18개 사용자 route와 전용 404의 사용자 문구, 브랜드 palette, 인증·보호 shell과 motion을 B2C 취업 준비 서비스 관점으로 재설계했다.
  - API·DTO·DB·route 범위와 P6 이후 미구현 기능은 변경하지 않았다.
- Key decisions:
  - electric cobalt, warm off-white, deep ink와 조립되는 H·연결 경로 motif를 사용하고 `AI 작업`, `경험 정보`, `이력서·자료`, `관심 공고`의 소비자 용어 체계를 적용한다.
- Issues encountered:
  - 필수 참고 사이트는 사용 가능한 in-app browser 세션이 없어 직접 열지 못했고, 공개 웹 페이지·공식 live site·시각 검색 결과를 대체 자료로 사용했다.
  - Before 캡처용 임시 script가 lint 대상에 들어가 산출물 디렉터리에서 제거하고 캡처·감사 표만 보관했다.
  - 최초 read-only validator의 내부 작업 key·인증 읽기 순서·문서 경계·대비·원시 형식 지적을 한 차례 보정하고 동일 검증을 재실행했다.
  - 2차 validator의 metadata 무손실 보존 지적까지 수정했으나 규칙상 세 번째 독립 validator를 실행하지 않아 최종 독립 상태는 `NOT_VERIFIED`로 남긴다.
- Validation:
  - 변경 전 `corepack pnpm check`가 Vitest 35 files/128 tests와 production build까지 통과했다.
  - 변경 후 전체 Frontend check, fixture Playwright, 전 route 1440·390px 직접 진입과 read-only validator 결과는 Frontend 기록에 상세히 남긴다.
- Next steps:
  - 격리 Backend·PostgreSQL·Object Storage 환경에서 profile·Document·Job actual E2E를 재실행한다.

## [2026-07-27] Session Summary (현재 Frontend route UI/UX 전면 개선)

- What was done:
  - 현재 router의 인증·onboarding·dashboard·profile·documents·jobs·Agent Run·404 19개 화면만 제품 design system과 responsive app shell로 전면 개선했다.
  - Backend, API·DTO·DB·route table과 P6 이후 기능은 변경하지 않고 공용 UI, 상태 표현과 접근성·반응형 검증을 추가했다.
- Key decisions:
  - graphite·blue-teal token, 얇은 border와 제한된 radius를 사용하고 사용자의 경력·문서·공고 작업을 시각적 중심으로 유지한다.
  - 실제 PrimeVue component가 없어 전역 plugin 초기화를 제거해 라이선스 배지와 불필요한 bundle을 없애되 dependency 계약은 변경하지 않았다.
- Issues encountered:
  - 브라우저 검수에서 PrimeUI 라이선스 배지를 발견해 사용처를 조사한 뒤 미사용 초기화를 제거했다.
  - 선택 E2E 인자가 잘못 전달돼 actual spec까지 대기한 첫 실행은 timeout됐고, fixture 파일을 직접 지정해 재검증했다.
- Validation:
  - `corepack pnpm check`의 ESLint·Prettier·TypeScript·Vitest 35 files/128 tests·production build와 fixture Chromium 4/4가 통과했다.
  - 1440·1024·768·390px overflow/focus E2E와 1440·390px 직접 캡처 검수를 수행했고 main JS는 370.31 kB(gzip 116.87 kB)다.
  - 실제 Backend·PostgreSQL·MinIO/Fake gateway 환경이 없어 profile·Document·Job actual E2E와 screen reader 실기 검수는 실행하지 않았다.
  - 최종 read-only validator는 `PASS WITH WARNINGS`를 반환했고 전후 변경 fingerprint는 `c6e379cbdd26c74fc171a501b783eadbbf034a036d7116dd4d2f9b6136c1aa16`으로 동일했다.
- Next steps:
  - 실제 Backend·PostgreSQL·Object Storage가 준비된 환경에서 기존 profile·Document·Job actual E2E를 다시 실행한다.

## [2026-07-27] Session Summary (P5 채용 공고 등록·추출·상태·Scheduler 통합 구현)

- What was done:
  - V6 Job schema, 공개 API 7개, canonical URL·SSRF-safe fetch, 고정 5단계 추출 workflow, 상태 history·Scheduler와 Vue 목록·등록·overview를 구현했다.
  - 실제 PostgreSQL·Spring·Vue·Fake fetch/Chat·Chromium으로 수동 201, 자동 202, WAITING_USER resume, owner 격리와 자동 마감 5개 여정을 검증했다.
- Key decisions:
  - 업무 상태와 추출 상태를 분리하고 Job·Run 최초 생성과 상태·history를 각 transaction으로 묶되 URL fetch는 transaction 밖에서 수행한다.
  - 검증된 DNS 주소로 실제 socket을 고정하고 HTTPS 원 hostname의 SNI·인증서 검증, redirect 재검사와 전체 fetch 절대 deadline을 적용한다.
- Issues encountered:
  - 최초 read-only validator가 DNS rebinding, streamed body timeout, reserved URL escape, P6 DTO 선행 구현과 NEEDS_MANUAL_INPUT retry를 지적해 허용된 한 차례 보정했다.
- Validation:
  - Backend 37 suites/322 tests, Frontend 32 files/122 tests·build, migration 6, P5 Browser E2E 5/5, Compose와 OpenAPI 50/34가 통과했다.
  - V1~V5 Git blob·SHA-256은 기준선과 동일하고 최종 read-only validator가 신규 finding 없이 `PASS`를 반환했다.
  - Validator 전후 173개 변경 파일 fingerprint는 `deacb3d70790bddf8baa27db3ec44eca10a7f6499a85f9477f1e8d3d96ed4212`로 동일했다.
- Next steps:
  - P6 공고 분석·RAG는 새 API·migration·workflow로 별도 착수한다.

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
