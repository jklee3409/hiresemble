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
- 공개 Spring/OpenAPI는 P7 자기소개서 17개, 계정 닉네임 변경과 Agent Run history delete를 포함해 총 73 operations·53 paths다.
- Dashboard 전용 집계·면접과 실제 provider는 아직 없다.

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
