# Progress

## Overview

- Vue 3, TypeScript, Vite, pnpm 기반 개발 환경과 주요 plugin이 구성되어 있다.
- P1 auth부터 P8 Interview typed client·Vue Query·답변 CAS·SSE terminal invalidation까지 구현되어 있다.
- `/guide`, `/agent-runs`, `/documents`, `/jobs`, `/cover-letters`, `/interviews`와 관련 child route는 lazy route이며 responsive AppLayout에는 Progress Drawer가 연결되어 있다.
- Vitest 67 files/284 tests와 공개 Landing·UI shell, P2~P8 actual E2E, 자동 분석·전반 화면 fixture Browser 회귀가 있다.

## [2026-08-05] Session Summary (내 지원 정보 화면 개편 통합)

- What was done:
  - `StructuredProfilePage.vue` 목록 항목을 icon·배지·사실 목록·설명을 가진 카드로 재구성하고, 경력 timeline이 목록 overflow에 잘리던 문제와 `ol` marker 노출, `정렬` label 줄바꿈을 함께 고쳤다.
  - `ProfileBasicPage.vue` 미완료 안내를 warning 카드로 바꾸고 form section 제목과 `ProfileTabs.vue` navigation 항목에 icon을 추가했다.
  - `docs/spec/page.md` 5장에 목록 항목 표현 규칙과 navigation·기본 정보 icon 계약을 반영했다.
- Key decisions:
  - 표시 값은 기존 DTO에서만 유도하고 새 API 호출이나 추정 상태를 만들지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Node 24에서 `corepack pnpm check`: lint·format·typecheck, Vitest 67 files/284 tests, production build 통과.
  - Chromium `profile`·`ui-shell` E2E는 기존 `profile suggestions` 실패 1건을 제외하고 통과.
- Next steps:
  - 기존 `profile suggestions` E2E 실패 원인 조사.

## [2026-08-05] Session Summary (Dashboard 시각 개편 통합)

- What was done:
  - `DashboardPage.vue` 요약·마감 캘린더·다음 할 일·최근 활동·가이드 카드의 시각 위계와 상호작용을 개편하고, `AppIcon.vue`에 자체 제작 icon 8종, `styles/main.css`에 motion·강조 token을 추가했다.
  - `docs/spec/page.md`의 Dashboard 절에 요약 보조 문구, 빠른 실행, D-day 배지와 legend, 가이드 icon 매핑, reduced-motion 계약을 반영했다.
- Key decisions:
  - 색은 기존 Hiresemble Blue와 semantic token만 사용하고, 조회 실패는 계속 `—`로 유지한다.
- Issues encountered:
  - 로컬 Node 20.18.0에서는 `pnpm`과 `vitest`가 실행되지 않아 component test를 미검증으로 남겼다.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `prettier --check`, `vite build`와 Chromium UI shell·Landing E2E(기존 실패 1건 제외) 통과.
- Next steps:
  - Node 22 이상에서 `corepack pnpm check` 재실행.

## [2026-08-05] Session Summary (공고 기간 반기 label 색상 보정)

- What was done:
  - 공고 기간 filter의 `상반기`, `하반기` label에서 brand 색상을 제거하고 기본 text 색상을 적용했다.
- Key decisions:
  - 반기 label은 큰 글자와 800 굵기만으로 강조하고 별도 강조 색상은 사용하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `corepack pnpm check`
  - `corepack pnpm exec prettier --check src/pages/JobListPage.vue progress.md src/pages/progress.md`
  - `git diff --check`
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 기간 menu 밀도·정렬 보정)

- What was done:
  - 기간 menu를 기존 폭의 80%로 축소하고 날짜 입력과 `~ 오늘`을 한 줄에 고정했으며 상하반기 label을 강조했다.
- Key decisions:
  - 연도는 보조 정보로 유지하고 상반기·하반기는 brand 색상, 큰 글자와 800 굵기로 우선 표시한다.
- Issues encountered:
  - 좁은 menu에서 날짜 input이 suffix 공간을 밀어 `오늘`이 두 줄로 보였다.
- Validation:
  - Job page 집중 9 tests와 `pnpm check` 67 files/284 tests·production build 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 목록 등록 반기 필터)

- What was done:
  - 공고 목록의 추출 상태·마감 시작/종료·임박 필터를 삭제하고 실제 보유 연도·상하반기 dropdown과 시작일~오늘 직접 설정을 구현했다.
- Key decisions:
  - 서버 `availablePeriods`만 preset으로 표시하고 URL에는 preset 또는 직접 시작일 중 하나만 canonical하게 유지한다.
- Issues encountered:
  - 새 page 응답 필드로 기존 typed fixture 두 곳을 보강했다.
- Validation:
  - `pnpm check`: lint·format·typecheck, 67 files/284 tests, production build 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석 디자인 가이드 모바일 계약 완성)

- What was done:
  - 공고 분석 redesign의 모바일 판단 영역을 104px 단일 gauge, 커버리지·시각 meta, 전폭 CTA와 접힌 category chart로 재우선순위화했다.
  - 상태 legend를 4행으로 바꾸고 강점·보완은 첫 항목과 `N개 더 보기` disclosure를 우선하며 작은 화면의 면접 tab 문구를 축약했다.
- Key decisions:
  - desktop 2중 gauge와 세 metric DOM은 유지하되 mobile에서는 score gauge만 표시한다.
- Issues encountered:
  - 기존 844px metric geometry 계약이 새 가이드와 충돌해 사용자 승인 뒤 E2E를 새 계약으로 전환했다.
- Validation:
  - Node 24 `corepack pnpm check` 67 files/282 tests·production build, Job Analysis·visual fixture Chromium 2/2 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석 페이지 디자인 가이드 적용)

- What was done:
  - `docs/design/job-analysis-page-design-guide.html`의 스펙을 실제 화면에 적용했다. `main.css` token 추가, `AppIcon.vue` icon 15종 추가, `JobAnalysisPage.vue` 결과 화면 재구성과 CSS 전면 교체, `JobPreparationJourney.vue` surface·모션 보정, `JobDetailLayout.vue` tab·제목 위계 보정.
- Key decisions:
  - 공개 API 계약, route, 화면 문구, 상태 분기, 접근성 이름을 바꾸지 않는 표현 계층 변경으로 한정했다. 기존 class 이름과 DOM 계약을 유지해 테스트 단언의 의미를 보존했다.
  - 차트 마크 색은 기존 semantic token과 분리한 `--chart-*` 4색을 사용하고, 색각 검증 WARN 구간을 상쇄하기 위해 아이콘·한글 라벨·2px 간격·텍스처의 2차 인코딩을 항상 함께 적용한다.
- Issues encountered:
  - 실행 환경이 Node 20이라 `corepack pnpm`(Node 22+ `node:sqlite` 필요)과 `vitest`(jsdom `ERR_REQUIRE_ESM`)를 실행하지 못했다. 변경하지 않은 테스트 파일에서도 동일하게 실패해 환경 제약임을 확인했다.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `prettier --check .`, `vite build` 통과. build 산출 CSS 기준 computed style과 320~1440px 가로 스크롤 검증 완료.
  - `vitest`와 Playwright는 미실행이다.
- Next steps:
  - Node 24 환경에서 `corepack pnpm check`와 job-analysis E2E를 실행한다.

## [2026-08-04] Session Summary (공고 분석 compact decision flow 구현)

- What was done:
  - `JobAnalysisPage`의 원형 점수·장식 icon·거대한 report surface를 제거하고 판단 문구, 세 metric row, 단일 primary CTA와 flat evidence section으로 재구성했다.
  - `JobDetailLayout`의 긴 제목을 hover slide 대신 최대 두 줄 clamp로 바꾸고 mobile에서 보조 metadata와 여백을 줄여 핵심 판단을 앞당겼다.
- Key decisions:
  - 분석 실행·OUTDATED reason·history·criterion pagination과 API 계약은 유지한다. 다시 분석·프로필·공고 수정·분석 과정은 text/disclosure 기반 secondary action으로 둔다.
- Issues encountered:
  - 첫 mobile E2E에서 metric 영역 하단이 955.8px이었고 header/body/hero spacing을 조정한 뒤 844px 안으로 들어왔다.
- Validation:
  - 집중 Vitest 11건, type check, Playwright Job Analysis·visual fixture 2/2, 전체 check 67 files/282 tests와 production build 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Landing motion·공고 분석 visual report 완성)

- What was done:
  - `LandingPage`와 `LandingProductDemo`에 hero orbit, signal chip, journey flow band, scene progress와 responsive/reduced-motion 처리를 추가했다.
  - `JobAnalysisPage`에 score ring, coverage·match distribution·category chart, section icon과 disclosure button을 적용하고 조건별 결과를 5개씩 paging하도록 구현했다.
- Key decisions:
  - 기존 API·`BALANCED` 재분석·`jobVersion` 계약은 유지하고 presentation과 client-side paging만 바꾼다. 모바일 판단 board는 390px에서도 적합도 우선 2열을 유지한다.
- Issues encountered:
  - 인앱 browser는 연결되지 않았지만 Playwright CLI 실제 Chromium fallback으로 외부 reference의 scroll 장면과 3/5초 ambient·light-flow animation을 확인했다.
- Validation:
  - 집중 Vitest 20건, `vue-tsc`, Landing·Job Analysis Chromium 8/8, visual fixture 1/1, 전체 check 67 files/282 tests·build와 모바일 geometry를 포함한 최종 Job Analysis Chromium 1/1 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 단일 리포트·행동 위계 재설계)

- What was done:
  - `JobAnalysisPage`의 재분석 선노출과 반복 metric·status·insight·evidence·criterion card를 제거하고 지원 판단→다음 행동→근거 순서의 단일 report 화면으로 재구성했다.
  - `JobPreparationJourney`의 넓은 brand soft 배경과 번호 원형을 구분선 기반 진행 행과 상태 marker로 교체하고 component·E2E 계약을 갱신했다.
- Key decisions:
  - 최신 결과에서는 자기소개서 준비만 primary로 유지하고 재분석·프로필 보완·공고 수정·면접 준비는 secondary text/action으로 낮춘다. Mobile은 판단 정보를 2열로 압축하고 filter를 가로 scroll한다.
- Issues encountered:
  - 인앱 Browser가 unavailable이어서 변경 후 visual 확인과 Chromium 실행은 하지 못했다.
- Validation:
  - 집중 Vitest 18건, 별도 type check와 전체 `corepack pnpm check`의 lint·format·type check·67 files/281 tests·production build가 통과했다.
- Next steps:
  - Browser 연결이 가능할 때 updated fixture의 desktop/mobile 시각·overflow 회귀를 실행한다.

## [2026-08-04] Session Summary (공고 분석 결과 전문 UI 개선)

- What was done:
  - 매칭 현황 여백·점수 bento, AI 요약과 접힌 공고 상세, 강점·보완 insight, 조건 filter와 접힌 결과 기록을 구현했다.
- Key decisions:
  - 표시 점수만 가장 가까운 5점 단위로 반올림하고 서버 DTO와 계산값은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `corepack pnpm check`: lint·format·type check·67 files/281 tests·production build 통과.
  - Job Analysis Chromium 1/1과 전체 화면 desktop/mobile 시각 캡처 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 커버리지·시각 요약 UI)

- What was done:
  - 공고 분석에 커버리지 metric, match 상태별 개수, category별 점수 막대, 원문 항목 count badge와 접이식 판정 근거를 추가했다.
- Key decisions:
  - `UNKNOWN`을 불일치로 보이지 않게 커버리지 안내와 분리하고 과거 rubric의 null coverage는 `이전 분석`으로 표시한다.
- Issues encountered:
  - 기존 결과 화면은 장문 section이 순차 나열되어 전체 분포와 근거 부족을 빠르게 파악하기 어려웠다.
- Validation:
  - `corepack pnpm check`: 67 files/281 tests, type check, lint, format, production build 통과.
- Next steps:
  - 실제 데이터에서 긴 criterion과 모바일 접힘 사용성을 확인한다.

## [2026-08-04] Session Summary (공고 분석 최신 실행 상태 정합화)

- What was done:
  - 공고 분석 화면의 상태 카드와 상세 이동이 서버가 반환한 가장 최근 Run을 사용하도록 ID 선택 순서를 바로잡았다.
- Key decisions:
  - 최신 Run query의 `queuedAt,desc` 결과를 authoritative 상태로 사용하고 최초 자동 분석 ID는 fallback으로만 유지한다.
- Issues encountered:
  - 실제 플래티어 공고에는 최신 성공과 과거 실패 Run이 정상 저장돼 있었지만 Frontend가 과거 자동 Run을 고정 선택했다.
- Validation:
  - 집중 Vitest 9건과 전체 `corepack pnpm check` 67 files/281 tests·production build가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 실패 재실행 행동 경로 보완)

- What was done:
  - 실제 첨부 화면처럼 `FAILED`이지만 `retryable=false`인 공고 분석에서도 실패 카드의 `공고 분석 재실행` 버튼으로 새 분석을 요청할 수 있게 했다.
- Key decisions:
  - generic retry 가능 Run은 predecessor lineage를 유지하고, 불가능한 Run은 현재 Job version의 강제 `BALANCED` 분석으로 복구한다.
- Issues encountered:
  - 첫 작업에서는 generic retry 가능 분기만 확인해 버튼이 없는 실제 상태를 완료로 잘못 판단했다.
- Validation:
  - 집중 Vitest 8건과 전체 `corepack pnpm check` 67 files/280 tests·production build가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 실패 재시도 회귀·브랜드 로고 교체)

- What was done:
  - `JobAnalysisPage`가 실패·중단된 `JOB_ANALYSIS` Run 중 서버가 `retryable=true`로 허용한 경우에만 범용 retry를 호출하고 successor Run을 추적하는 기존 흐름을 확인했다.
  - 두 번째 로고를 공용 `BrandMark`의 모든 보호·공개 shell과 Landing 사용처에 연결하고 같은 자산을 파비콘으로 번들링했다.
- Key decisions:
  - 공고 분석 retry는 새 resource endpoint나 자동 재호출을 만들지 않고 기존 idempotent Agent Run lineage를 유지한다. 로고는 원본의 투명 여백을 정리한 50KB PNG 한 개를 재사용한다.
- Issues encountered:
  - None.
- Validation:
  - 집중 Vitest 2 files/12 tests와 전체 `corepack pnpm check`의 lint·format·typecheck·67 files/279 tests·production build가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 안내 용어·동의 Modal 개선)

- What was done:
  - Signup의 이메일 보조 안내를 제거하고 비밀번호 안내·오류에서 기술 용어를 숨겼다.
  - 필수 동의 Modal을 요약 카드, 쉽은 상세 문장, 독립 scroll 본문과 고정 확인 영역으로 개편했다.
- Key decisions:
  - 실제 client/server credential 제약은 유지하고, 동의 상세는 전문 명칭을 사용하지 않는다.
- Issues encountered:
  - 집중 Chromium 두 번 모두 중복 텍스트 locator strict 오류로 중단됐고, 두 locator를 정확한 문장으로 수정했으나 재검증 상한에 따라 세 번째는 실행하지 않았다.
- Validation:
  - 집중 Vitest 20/20과 전체 `corepack pnpm check` 67 files/279 tests·build 통과. 수정 후 전체 Chromium 완주는 `NOT_VERIFIED`.
- Next steps:
  - 다음 회차에 `public authentication shell` Chromium 시나리오를 재실행한다.

## [2026-08-04] Session Summary (가입 검증·온보딩 지원 자격·마감 선택 UX)

- What was done:
  - 회원가입 credential blur 검증과 새 비밀번호 조합 안내, 온보딩 첫 단계의 지원 자격 입력, 공고 등록의 날짜·오전/오후·30분 단위 마감 선택을 구현했다.
- Key decisions:
  - 기존 profile eligibility API와 Job `Instant` API 계약을 재사용하고 공개 DTO는 변경하지 않았다.
- Issues encountered:
  - 첫 전체 check는 온보딩의 신규 eligibility 조회를 누락한 기존 router mock 1건에서 실패했으며 mock 보완 후 재검증했다.
- Validation:
  - 집중 Vitest 30건, Chromium 2건과 전체 `corepack pnpm check` 67 files/279 tests·production build가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 입력 규칙·동의 상세 UX)

- What was done:
  - 회원가입에 이메일 형식 예시, 실제 비밀번호 byte 규칙·현재 길이 상태와 두 필수 동의의 사용자 친화적 상세 Modal을 추가했다.
- Key decisions:
  - 숫자·특수문자 강제 없이 Backend 10..72 UTF-8 byte 계약을 그대로 사용하고, 동의 상세 확인과 checkbox 선택은 분리했다.
- Issues encountered:
  - 인앱 Browser runtime이 비어 있어 저장소의 Playwright Chromium으로 반응형·상호작용을 검증했다.
- Validation:
  - 집중 Vitest 18건, 회원가입 Chromium 1건, 전체 `corepack pnpm check` 67 files/276 tests·production build 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (활성 AI 작업 route 복구와 중복 실행 차단)

- What was done:
  - 문서 재분석, 공고 추출·분석, 자기소개서 생성·검증, 면접 준비·답변 피드백 화면이 서버의 활성 Agent Run을 조회해 route 재진입 후 monitor를 복구하고 동일 실행 버튼을 잠근다.
  - 공고 분석 OUTDATED 알림의 중복 재분석 버튼을 제거해 상단 CTA 한 개만 유지했다.
- Key decisions:
  - 로컬 mutation pending뿐 아니라 persisted `QUEUED/RUNNING/WAITING_USER`와 조회 loading/error도 실행 금지 상태로 취급한다.
- Issues encountered:
  - 최초 전체 check에서 5개 파일 format과 테스트의 `get().exists()` 타입 오류 2건을 보정했다.
- Validation:
  - `corepack pnpm check`가 lint·format·typecheck·67 files/275 tests·production build까지 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 본문 중심 기준 정렬)

- What was done:
  - Dashboard 헤더·CTA·본문을 바로가기와 독립된 중앙 열로 정렬하고 우측 sticky 바로가기와 좁은 화면 가로 탐색을 유지했다.
  - UI shell에 중심선·우측 경계 geometry 회귀를 추가했다.
- Key decisions:
  - 전역 AppLayout과 다른 page 폭은 변경하지 않고 Dashboard page의 88rem 내부 grid만 조정했다.
- Issues encountered:
  - 최초 check의 두 파일 format 실패는 범위 formatter로 보정했다. 전체 UI shell 병렬 실행에서는 무관한 프로필 제안 테스트 1건이 timeout됐으나 Dashboard 격리 회귀는 통과했다.
- Validation:
  - `corepack pnpm check`가 ESLint·Prettier·typecheck·67 files/269 tests·production build까지 통과했고 Dashboard Chromium 1/1과 responsive screenshot 검수도 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (지원 자격 확인 정보 입력)

- What was done:
  - 기존 프로필 화면에 근무 가능일·병역·해외여행·채용 결격 자기신고 입력 영역과 additive typed API client를 추가했다.
- Key decisions:
  - 공고 분석 결과 UI와 기존 profile form은 유지하고 별도 version/mutation 경계의 작은 form만 추가했다.
- Issues encountered:
  - 최초 check에서 formatting과 API mock 누락이 발견되어 최소 수정 후 재검증했다.
- Validation:
  - `corepack pnpm check`가 67 files/269 tests, typecheck, build, formatting까지 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 분석 결과 문구·내부 경로 보정)

- What was done:
  - 최신 결과 hero에서 분석 버전 대신 `공고와 잘 맞는 강점을 분석했어요.`를 표시하고 저장된 버전 이력은 유지했다.
  - 과거 requirement 출처의 JSONPath·내부 field path를 화면에서 `공고 본문`으로 치환했다.
- Key decisions:
  - API DTO와 분석 이력 구조를 변경하지 않고 presentation helper에서 이전 저장 결과까지 안전하게 처리한다.
- Issues encountered:
  - None.
- Validation:
  - Job Analysis page 집중 테스트 7건, 전체 67 files/267 tests, ESLint·Prettier·Vue typecheck·production build 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 분석 사용자 단계명 완성)

- What was done:
  - 진행 중인 `JOB_ANALYSIS` 상세가 순번 대신 실제 도달한 과정의 사용자용 이름을 표시하도록 누락된 단계명과 회귀 테스트를 보강했다.
- Key decisions:
  - 공고 추출·분석 Run과 공개 API 계약은 유지하고 Agent Run presentation 범위에서만 수정했다.
- Issues encountered:
  - None.
- Validation:
  - Agent Run 상세 집중 테스트 9개와 Frontend 표준 check 67 files/267 tests·production build가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 스크롤 바로가기·문구 흐름 보정)

- What was done:
  - Desktop Dashboard의 우측 바로가기를 문서에 고정하지 않는 container `sticky` 방식으로 바꿔 페이지 스크롤을 따라오게 했다.
  - 준비 workspace 제목을 의미 단위 두 묶음으로 유지하고 공고 분석 실패 CTA를 `재시도`로 줄였다.
- Key decisions:
  - 74rem 이하에서는 기존 가로형 일반 흐름을 유지하고 상호작용 요소의 focus 표현은 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 표준 check 67 files/265 tests·build, Chromium UI shell 3/3과 1440·390px 시각 검수가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 진행 단계 간격·제목 크기 보정)

- What was done:
  - Desktop 공고 분석 4단계의 각 내용 폭을 기준으로 단계 블록 사이 여백을 균일하게 재배치했다.
  - 공고 상세 제목 크기를 기존 범위의 80%인 `1.4–2.2rem`으로 낮췄다.
- Key decisions:
  - 기존 한 줄 slide, reduced motion, 70rem 2열과 48rem 1열 반응형 계약은 유지했다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 표준 check 67 files/265 tests·build, Job analysis Chromium 1/1과 1440·390px visual capture 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 바로가기·공고 분석 표시 개선)

- What was done:
  - Dashboard에 비고정 섹션 바로가기와 self-hosted variable Noto Sans KR 제목 typography를 추가하고 중복 요약 제목·오늘 이동·서울 기준 보조 문구를 제거했다.
  - 공고 header 긴 제목 slide와 분석 진행 한 줄 표현, 품질 선택 제거, safe error 사용자 문구를 적용했다.
- Key decisions:
  - 공고 분석 API 요청은 선택 UI 없이 항상 `BALANCED`를 보내며 reduced motion 사용자는 제목 자동 slide 대신 직접 가로 scroll을 사용한다.
- Issues encountered:
  - 첫 check에서 E2E format 한 건이 발견됐고 Prettier 적용 후 재검증했다.
- Validation:
  - Frontend 표준 check 67 files/265 tests·build, UI shell Chromium 3/3, Job analysis Chromium 1/1 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 캘린더 제품 UI 개선)

- What was done:
  - 캘린더 월 header·요약·이동 controls를 재구성하고 날짜 cell gap·경계·밀도, 오늘·선택·마감 chip과 hover/focus 상태를 정돈했다.
  - 준비 workspace 제목과 설명 사이에 명시적인 간격을 추가했다.
- Key decisions:
  - 날짜 선택과 상세 panel 데이터 흐름은 유지하고 CSS·presentational markup만 개선했다. 선택 강조는 외부 shadow 대신 내부 border를 사용한다.
- Issues encountered:
  - 선택된 오늘 바로 다음 날짜 hover가 gap 없는 grid에서 맞닿아 겹쳐 보였고 0.35rem cell gap과 내부 shadow로 해소했다.
- Validation:
  - Dashboard unit 5 tests와 Chromium UI shell 3/3, 1440·1024·390px screenshot 및 인접 cell 간격 assertion 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 세부 시각·장문 Guide modal 보완)

- What was done:
  - 사람 SVG, 이름 단독 blue 강조, 일요일 red·토요일 blue, 날짜별 `N건`, workspace 하단 CTA와 장문 guide modal hierarchy를 구현했다.
- Key decisions:
  - PageHeader title slot과 AppIcon을 additive하게 확장하고 Dashboard 전용 color alias로 공통 토큰 계약을 유지했다.
- Issues encountered:
  - 실제 Browser에서 정의되지 않은 page color alias가 상속색으로 떨어지는 현상을 발견해 Dashboard와 Teleport modal 범위에서 보정했다.
- Validation:
  - `corepack pnpm check`: 67 files/265 tests와 production build 통과. Chromium UI shell 3/3 및 1440·1024·390px screenshot 확인.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard B2C 워크스페이스·캘린더·가이드)

- What was done:
  - 새 Dashboard typed client를 연결하고 커리어 카드, 행동 요약, 서울 월간 캘린더, 최근 활동과 서버 가이드 modal로 화면을 재구성했다.
  - Dashboard 전용 폭과 route workspace focus 회귀를 보정하고 1440·1024·390px fixture를 갱신했다.
- Key decisions:
  - 실제 count·deadline은 Dashboard projection, 최근 활동은 기존 owner 목록을 사용한다. 가이드 본문은 프론트 상수로 두지 않는다.
- Issues encountered:
  - 전체 check에서 기존 router test가 과거 문구·source를 기대해 실패했고 새 API fixture와 문구로 보정 후 통과했다.
- Validation:
  - `pnpm check`: 67 files/264 tests, lint·format·typecheck·build 통과. Playwright Landing/UI shell 10/10 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Landing Hero 크기·카피 후속 조정)

- What was done:
  - 공개 Landing Hero heading 크기를 약 80%로 낮추고 문제·이용 흐름·핵심 가치·AI 원칙 heading 네 곳을 요청 문구로 변경했다.
  - DOM 제품 데모의 수동 pause/play UI와 상태를 제거하고 자동 timeline의 viewport·문서 visibility·reduced motion 정지는 유지했다.
- Key decisions:
  - 새 dependency, video asset, API·DB·AI workflow 변경 없이 Vue state와 기존 CSS만 조정했다.
- Issues encountered:
  - None.
- Validation:
  - 관련 component Vitest 10/10, Landing Chromium 7/7과 1440·390·320px 시각 검수가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공개 Landing Hero motion 개선)

- What was done:
  - 공개 Landing Hero를 전체 폭 2줄 heading과 하단 2열로 재구성하고, 실제 구현 기능만 표현하는 5-scene 자동 DOM 제품 데모와 section reveal을 추가했다.
  - timer·Intersection Observer·Page Visibility·pause/resume·reduced motion·점진적 향상 회귀를 Vitest와 Playwright로 고정했다.
- Key decisions:
  - reference MP4와 별도 video asset은 제품에 포함하지 않고 Vue state와 CSS opacity·blur·transform만 사용했으며 dependency를 변경하지 않았다.
- Issues encountered:
  - reference 분석은 PATH의 ffmpeg 부재로 시스템 임시 경로의 도구를 사용했고, Playwright full-page 캡처는 section 진입을 명시적으로 완료한 뒤 생성하도록 보정했다.
  - 첫 표준 check는 `e2e/index.md` Prettier 정렬 1건에서 중단됐고 formatter 적용 후 재실행해 통과했다.
- Validation:
  - `corepack pnpm check`: 66 files/268 tests, lint·format·typecheck·production build 통과.
  - Landing Chromium 7/7과 1440·390·320px screenshot 검수가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공개 Landing·Dashboard 첫 사용 흐름)

- What was done:
  - `LandingPage.vue`와 공개 route, 공통 title hook, 정확한 metadata를 추가하고 인증 form·onboarding·보호 guide 계약을 유지했다.
  - Dashboard 시작 영역을 부분 완료와 query unknown을 구분하는 3항목 체크리스트로 바꾸고 일반 제품 현황을 항상 함께 표시했다.
- Key decisions:
  - 5단계 번호·아이콘·제목·핵심 설명만 `productJourney.ts`로 공유하고 공개 페이지에는 보호 route link를 두지 않았다.
- Issues encountered:
  - Chromium의 CSS duration 직렬화와 query slash encoding 차이를 E2E assertion에서 정규화했다.
- Validation:
  - `corepack pnpm check`: 65 files/258 tests, lint·format·typecheck·build 통과.
  - `landing.spec.ts` Chromium 6/6, `ui-shell.spec.ts` 3/3과 1440·390·320px·4개 화면 캡처 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (전반 B2C UI·공고 자동 분석 UX)

- What was done:
  - 상단 journey navigation·계정 메뉴·mobile bottom navigation, page variant와 token을 도입하고 dashboard부터 profile·documents·jobs·cover letters·interviews·AI 작업·onboarding까지 문구와 계층을 통일했다.
  - 공고 document view·자동 분석 journey·결과 요약·보조 재분석 옵션과 실제 component 기반 `/guide`를 구현했다.
- Key decisions:
  - 최초 분석은 서버 BALANCED projection을 읽으며 브라우저가 분석 command를 연쇄 호출하지 않는다. 원문은 `v-html` 없이 deterministic node로 렌더링한다.
- Issues encountered:
  - mobile 공고 탭의 4px bleed를 35rem gutter token에 맞춰 제거하고 기존 E2E heading 기대값을 새 semantic hierarchy와 동기화했다.
- Validation:
  - `corepack pnpm check`: 64 files/249 tests, lint·format·typecheck·build 통과. 실행 가능한 Chromium 9/9와 변경 후 화면 캡처 30장 통과.
- Next steps:
  - 전용 환경 flag가 필요한 P4~P8 `*.actual.spec.ts` 13건은 skip됐다.

## [2026-08-01] Session Summary (Job extraction v3 UI 회귀)

- What was done:
  - Backend v3가 공개 상태·step key를 바꾸지 않음을 확인하고 관련 index를 동기화했다.
- Key decisions:
  - OCR 선택 UI를 추가하지 않고 successor Run 갱신·safe fallback·manual/retry CTA·SSE reconnect 의미를 유지한다.
- Issues encountered:
  - None.

## [2026-08-01] Session Summary (이미지형 공고 자동 처리 UX)

- Validation:
  - `corepack pnpm check`: 61 files/243 tests, lint·format·typecheck·build 통과. P5 Chromium 5/5.
- Next steps:
  - None.

- What was done:
  - v2 Job workflow step을 사용자용 `공고 페이지 불러오기/내용 확인/이미지 읽기/채용 정보 확인/결과 저장` 문구로 매핑했다.
  - 진행·자동 판독 부족 문구를 보정하고 기존 manual input/retry CTA와 SSE 복구 의미를 유지했다.
- Key decisions:
  - 공고 등록 폼에 OCR·이미지 공고 선택 control을 추가하지 않는다.
- Issues encountered:
  - 기존 문구 assertion 2건을 새 fallback 메시지로 동기화했다.
- Validation:
  - `corepack pnpm check`: 61 files/243 tests, lint·format·typecheck·build 통과. P5 actual Chromium 5/5.
- Next steps:
  - None.

## [2026-08-01] Session Summary (자료 검토·대외활동·알림 UX 보정)

- What was done:
  - 자료 목록·상세·미리보기와 소재 검토를 행동 순서 중심으로 재설계하고 개별/선택/전체 승인·제외·재검토를 추가했다.
  - 직접 등록 대외활동 CRUD 화면과 전역 토스트·접근 가능한 확인 모달을 구현하고 브라우저 기본 alert/confirm/prompt를 제거했다.
  - AI 상세를 기본 접힘과 사용자 단계명으로 바꾸고 USD 및 기술 오류를 숨겼으며 중복 결과 영역을 통합했다.
- Key decisions:
  - 성공은 toast, 필드 문제는 inline, 삭제·재분석·승인 취소는 alertdialog로 역할을 분리했다.
- Issues encountered:
  - 실제 실패 run에서 run-level safe message도 기술적으로 보일 수 있어 원문을 직접 렌더링하지 않도록 추가 보정했다.
- Validation:
  - `corepack pnpm check`: ESLint, Prettier, vue-tsc, 61 files/243 tests, production build 통과. Playwright desktop/mobile 실제 흐름과 focus/ESC/overflow를 확인했다.
- Next steps:
  - 실제 성공 분석의 복수 소재 브라우저 검증은 외부 Provider 사용 승인을 받은 환경에서 수행한다.

## [2026-07-31] Session Summary (P8 면접 준비·질문·답변 피드백 UI)

- What was done:
  - 공고 면접 tab, 질문 세트 목록·상세, 출처 coverage, immutable 답변 version과 feedback 진행·이력을 기존 shell에 추가했다.
- Key decisions:
  - 답변 409는 mutation을 자동 재시도하지 않고 최초 사용자 snapshot과 최신 server content를 비교한 뒤 명시적 재적용만 허용한다.
  - `qs*` URL namespace를 canonical API filter로 변환하며 P9 mock session UI는 만들지 않았다.
- Issues encountered:
  - P8 projection을 거부하던 과거 Job contract fixture를 현재 활성 계약으로 갱신했다.
- Validation:
  - `corepack pnpm check`: 60 files/238 tests, lint·format·typecheck·production build 통과. P8 actual은 desktop·mobile·200% scale과 overflow/focus를 검증했다.
  - 두 번째 single-agent read-only self-audit는 새 finding 없이 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (닉네임 Modal·최종 학력 UI 보정)

- What was done:
  - 상단 닉네임을 accessible Modal trigger로 바꾸고 기본 정보 Form에서는 닉네임 입력을 제거했다.
  - 대외활동 승인·거절 안내, AI 작업 선택/삭제 문구, 관심 공고 active hover와 최종 학력 단계·배지를 반영했다.
- Key decisions:
  - 최종 학력은 response의 read-only `isPrimary`만 표시하고 수동 대표 지정 control은 제공하지 않는다.
- Issues encountered:
  - production build에서 AppLayout media query 닫힘 누락을 발견해 수정했다.
- Validation:
  - `corepack pnpm check`: 53 files/216 tests, lint·format·typecheck·production build 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (대외활동 UI·AI 작업 내역 삭제)

- What was done:
  - 기본 정보 savebar를 Form 하단으로 이동하고 경험 정보를 대외활동으로 변경했으며 filter gap과 학력 상태 한국어 mapping을 적용했다.
  - 문서 AI 근거에만 승인·거절을 노출하고 직접 입력·AI confidence 의미를 구분해 안내했다.
  - Agent Run 목록에 terminal row 개별 삭제, 현재 페이지 선택·전체 선택과 일괄 삭제를 추가했다.
- Key decisions:
  - active Agent Run checkbox와 삭제 action은 비활성화하고 mutation 성공 시 detail cache 제거와 owner root invalidation을 수행한다.
  - server enum은 list subtitle과 409 conflict 양쪽에서 사용자 문구로 변환한다.
- Issues encountered:
  - 전체 check 중 수정 파일 4개와 마지막 학력 상태 mapping 1개의 Prettier 경고를 각각 대상 파일만 format해 해소했다.
- Validation:
  - `corepack pnpm check`: ESLint, Prettier, typecheck, 53 files/215 tests, production build 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (프로필 화면·닉네임 편집 보정)

- What was done:
  - 프로필 navigation을 설명 없는 7개 항목으로 축약하고 첫 route 진입 scroll과 focus를 보정했다.
  - 기본 정보 save bar 좌우 잘림과 자기소개서 filter 간격을 수정하고, 기존 저장 action에 nickname validation·API·store projection 갱신을 연결했다.
- Key decisions:
  - 새 전용 설정 화면을 복제하지 않고 프로필 기본 정보에서 profile DTO와 account display-name 요청을 조정한다.
  - route 이동 focus는 `preventScroll`을 사용하고 browser history 저장 위치 외 새 진입만 상단으로 이동한다.
- Issues encountered:
  - 실브라우저의 기존 Backend process가 변경 전 source여서 닉네임 API 결합은 unit·Spring integration으로 분리 검증했다.
- Validation:
  - `corepack pnpm check`: ESLint·Prettier·vue-tsc·53 files/214 tests·Vite build 통과.
  - Playwright CLI 1440×1000에서 profile sidebar 전체 노출, save bar 좌우 inset과 Cover Letter filter 12px gap을 확인했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Frontend 최종 validator PASS)

- What was done:
  - 최종 read-only validator가 route 3개, canonical editor, sessionStorage draft, archived read-only, historical evidence와 작업별 409 비교·재적용을 독립 재검증했다.
- Key decisions:
  - 자기소개서 결과는 `/agent-runs`에 복제하지 않고 resource link만 유지한다.
- Issues encountered:
  - 새 finding 없음.
- Validation:
  - Validator `PASS`; Frontend 53 files/211 tests와 lint·format·typecheck·build, P7 actual Chromium 1/1 PASS.
- Next steps:
  - P8 UI는 Backend 공개 계약이 별도 단계에서 고정되기 전 추가하지 않는다.

## [2026-07-30] Session Summary (P7 409 비교·suggestion 계약 보정)

- What was done:
  - verification suggestion schema를 최대 20개·항목 1~1000자로 고정하고 20/21개·1000/1001자 경계 테스트를 추가했다.
  - TITLE·QUESTION·ORDER·ANSWER·LIFECYCLE mutation마다 최초 사용자 입력을 immutable snapshot으로 보존하고 실제 최신 server field 비교, 최신 CAS 재적용과 취소 동작을 분리했다.
  - actual E2E에 제목 외 실제 문항 충돌과 refetch 뒤 사용자 snapshot 재적용을 추가했다.
- Key decisions:
  - 충돌 refetch가 form/editor를 갱신해도 retry closure는 reactive 값을 읽지 않으며 사용자가 `최신 버전에 재적용`을 선택할 때만 저장된 snapshot을 사용한다.
- Issues encountered:
  - Vue Proxy를 직접 `structuredClone`할 수 없는 답변 취소 테스트를 plain TipTap snapshot 복제로 보정했다.
- Validation:
  - `corepack pnpm check`: 53 files/211 tests와 ESLint·Prettier·vue-tsc·Vite build 통과.
  - P7 actual Chromium 1/1에서 문항 `409`의 server field 비교와 immutable 재적용 `200`, 1440/390px overflow가 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 기다린다.

## [2026-07-30] Session Summary (P7 자기소개서 목록·공고 tab·편집 화면)

- What was done:
  - `/cover-letters`, `/jobs/:jobId/cover-letter`, `/cover-letters/:coverLetterId/edit` route와 navigation, 목록 filter·archive, 공고 상태 tab과 canonical editor를 구현했다.
  - TipTap 답변, question CRUD/order, evidence 선택, generation partial result, version save/compare/restore, verification·finalize, archive read-only와 sessionStorage draft·409 재적용을 연결했다.
- Key decisions:
  - URL query가 목록 filter 원천이고, browser draft는 user/question/base version별 sessionStorage에만 두며 server save·delete·archive·logout·401에서 제거한다.
- Issues encountered:
  - 실제 number input의 Vue runtime 값 타입 불일치를 string/number parser와 form 제출 component test로 보정했다.
- Validation:
  - `corepack pnpm check`에서 53 files/204 tests, lint·format·typecheck·production build가 통과했다.
  - P7 actual Chromium 1/1에서 전체 시나리오와 1440/390px overflow·사용자 격리가 통과했다.
- Next steps:
  - 독립 validator 판정 후 P7 상태를 확정한다.

## [2026-07-30] Session Summary (P6 final-source Frontend actual E2E 종료)

- What was done:
  - Backend 주도 wrapper에서 현재 `job-analysis.actual.spec.ts`의 정상 분석·reuse·OUTDATED·재분석·근거 부족·owner 격리를 실행했다.
- Key decisions:
  - 공개 PUT evidence endpoint를 통한 타 사용자 404 assertion을 유지하고 비계약 단건 GET을 다시 도입하지 않았다.
- Issues encountered:
  - Browser 시나리오는 첫 gate부터 exit 0이었고 실패는 wrapper DB 컬럼명에 한정됐다.
- Validation:
  - 최종 wrapper의 Playwright Chromium 2/2 통과, Vite process 정상 종료.
- Next steps:
  - P7 Frontend는 Backend DTO가 고정된 뒤 세 신규 route와 자기소개서 draft·version·verification UX를 구현한다.

## [2026-07-29] Session Summary (P6 공고 분석 화면·AI 작업 내역 연결)

- What was done:
  - `/jobs/:jobId/analysis`의 실행·진행·실패·성공·OUTDATED·이력 상태와 user-scoped typed query/client를 구현했다.
  - Eligibility와 fit score, 강점·부족한 점·criterion·VERIFIED evidence를 분리해 표시하고 사용자 용어를 `AI 작업 내역`으로 통일했다.
  - 분석 당시 근거가 이후 `PENDING`, `REJECTED`, `SOURCE_DELETED`로 바뀌어도 기존 결과와 점수를 유지하고 현재 상태·재분석 제외 안내를 텍스트로 표시한다.
- Key decisions:
  - 공고 상세 tab은 `공고 정보`와 `공고 분석`만 제공하며 P7/P8 가짜 tab을 만들지 않는다.
  - `/agent-runs`에는 결과를 복제하지 않고 `JOB_ANALYSIS` Run에서 공고 분석 화면으로 가는 resource link만 표시한다.
- Issues encountered:
  - actual E2E의 중복 제목 locator는 최신 결과 ID로 제한했고, 비공개 evidence GET assertion은 공개 PUT owner 404로 교체했다. 교체 후 actual E2E는 재실행하지 않았다.
  - 1차 validator가 current evidence 상태를 이유로 historical detail 전체를 거부하는 schema를 MAJOR로 판정해 canonical 상태는 수용하되 현재 positive proof로 오해하지 않는 UI로 보정했다.
- Validation:
  - 보정 후 `corepack pnpm check`: lint·Prettier·TypeScript·42 files/169 tests·production build 통과.
  - Job Analysis 1440px/390px와 Agent Run reconnect fixture Chromium 3/3, horizontal overflow·keyboard tab 검증이 통과했다.
  - 최종 validator는 historical evidence detail·UI finding 해소를 확인했지만 final-source actual P6 wrapper 미실행으로 전체 `FAIL`을 유지했다.
- Next steps:
  - 새로 승인된 검증 주기에서 current actual P6 spec을 wrapper로 실행한다. 이 요청에서는 추가 자동 재검증하지 않는다.

## [2026-07-28] Session Summary (데이터 기반 지원 홈·프로필 편집 경험 재설계)

- What was done:
  - Sidebar·header·route meta의 `오늘의 준비`를 `지원 홈`으로 통일하고 Dashboard를 상태 카드, 동적 다음 할 일, 마감 임박 공고, 최근 활동과 신규 사용자 시작 안내로 재설계했다.
  - Profile 기본 정보는 얕은 section 구조, 일관된 저장 상태, field error, 추천 축약형 tag 입력과 반응형 내비게이션을 적용했다.
  - 공통 content 폭, sidebar 폭과 Profile navigation 폭을 조정하고 Dashboard unit test와 6개 viewport UI fixture를 보강했다.
- Key decisions:
  - 보라색 장식과 중첩 card를 늘리지 않고 기존 Hiresemble Blue를 primary CTA·현재 위치·선택 상태에 한정했다.
  - 현재 Backend에 없는 Dashboard endpoint를 만들지 않았으며 목록 길이로 전체 건수를 추정하지 않고 `totalElements`만 사용했다.
- Issues encountered:
  - 첫 1440px 시각 검사에서 지표 grid의 빈 칸을 발견해 12-column 비대칭 배치로 보정한 뒤 desktop·mobile을 다시 확인했다.
- Validation:
  - `corepack pnpm check`: ESLint, Prettier, TypeScript, Vitest 40 files/154 tests와 production build 통과.
  - `UI_SCREENSHOTS=true` Chromium UI shell 3/3 통과; Dashboard·Profile 1440px와 390px 결과를 직접 검토했다.
- Next steps:
  - 실제 Backend cross-stack E2E와 보조 기술 실기기 검증은 별도 실행이 필요하다.

## [2026-07-28] Session Summary (첨부 화면 기반 반응형·정보 계층 보정)

- What was done:
  - 인증 shell의 최대 콘텐츠 폭, Dashboard hero 제목의 반응형 줄바꿈, profile outline의 인접 상태 간격을 보정했다.
  - 기본 정보 form의 두 정보군을 명확히 분리하고 안내 surface, 자료·공고 filter grid 간격을 Hiresemble Blue 체계에 맞췄다.
- Key decisions:
  - 기능 계약과 selector는 유지하고 기존 공용 token 및 scoped CSS만 활용해 dependency를 추가하지 않았다.
- Issues encountered:
  - 390px 첫 시각 검수에서 Dashboard 제목의 과도한 줄바꿈을 발견해 의미 단위 두 줄로 다시 조정했다.
- Validation:
  - `corepack pnpm check`: ESLint·Prettier·TypeScript·Vitest 39 files/149 tests·production build 통과.
  - fixture Chromium UI shell 3/3과 2500·1600·1574·1440·390px 시각 검수, 390px horizontal overflow 검사가 통과했다.
- Next steps:
  - 실제 Backend가 필요한 profile·document·job cross-stack E2E는 이번 시각 보정에서 재실행하지 않았다.

## [2026-07-28] Session Summary (전체 Frontend Career Workspace·Control System 재설계)

- What was done:
  - 모든 현재 route의 navigation, form, filter, loading·empty·error·status 표현을 `#3157ff` Hiresemble Blue와 공용 44px control language로 통합했다.
  - profile deep link는 유지하면서 desktop 세로 outline·mobile selector·이전/다음 action을 갖는 Career Profile Workspace로 재구성했다.
- Key decisions:
  - native select·checkbox·date semantics를 유지해 keyboard와 mobile 사용성을 보존하고 미구현 대외활동·공고 분석 route는 추가하지 않았다.
- Issues encountered:
  - 시각 재검토에서 자료 등록의 영어 eyebrow를 발견해 사용자용 한국어로 한 차례 보정했다.
  - actual Document E2E는 실행 중 Backend의 upload 일반 오류로 첫 test가 timeout됐고 Job actual은 필수 fixture URL이 없어 실행하지 않았다.
  - actual Profile E2E는 selector 보정 후 완료율 `100%` strict locator 중복으로 재검증도 실패해 추가 보정하지 않았다.
- Validation:
  - `corepack pnpm check`: ESLint·Prettier·TypeScript·Vitest 39 files/149 tests·production build 통과.
  - fixture Playwright 5/5와 1440·1024·768·390px 18개 화면 overflow·캡처 검수 통과; actual Profile 0/1·Document 0/4 완료.
- Next steps:
  - Backend 재시작·fixture URL 준비 후 actual Document·Job Playwright를 재실행한다.

## [2026-07-28] Session Summary (프로필·자료 등록 전문 UX와 추천 입력 적용)

- What was done:
  - 회원가입·로그인 이메일 형식, 닉네임·분석 기록·졸업(예정)일 문구를 사용자 화면에 적용했다.
  - 한국형 희망 직무·지역 preset과 포함 검색 combobox를 추가하고 프로필·자료 등록 정보 구조를 재설계했다.
- Key decisions:
  - 기존 API DTO·route·문자열 배열·Session/CSRF 계약과 design token을 유지했다.
- Issues encountered:
  - 변경된 제목을 참조하던 Playwright selector 한 건을 새 사용자 용어로 동기화했다.
- Validation:
  - `corepack pnpm check`, Playwright UI shell 3/3, Agent Run 2/2와 실제 Document E2E 4/4가 통과했다.
- Next steps:
  - 재시작한 실제 Backend·MinIO 환경에서 긴 파일명과 실제 데이터 밀도를 추가 검수한다.

## [2026-07-28] Session Summary (B2C UX Writing·Brand Experience 통합)

- What was done:
  - 인증, onboarding, dashboard, profile, documents, jobs, Agent Run과 404의 사용자 문구와 시각 언어를 현재 route 범위 안에서 전면 재설계했다.
  - 공용 BrandMark, cobalt 중심 token, 비대칭 인증 canvas, 연결형 dashboard와 기능적인 motion을 추가했다.
- Key decisions:
  - 내부 route·타입은 유지하되 화면에서는 `AI 작업`, `작업 종류`, `예상 사용 비용`, `경험 정보`, `이력서·자료`, `지원 상태`, `공고 불러오기`를 일관되게 사용한다.
  - 비밀번호 byte 검증은 보존하고 화면에는 길이 조정 행동만 안내한다.
- Issues encountered:
  - 실제 Backend 없이 수행한 headed 인증 검수에는 예상된 network 오류가 있었으며, 인증·AI 작업 상호작용은 fixture E2E로 별도 검증했다.
  - 최초 read-only validator가 원시 작업 단계·JSON 편집기·MIME type 노출, 인증 DOM 순서, muted 대비와 touch target을 지적해 소비자용 표현과 접근성 기준으로 한 차례 보정했다.
  - 2차 validator가 metadata key 무손실 보존과 빈 typed value 검증을 추가 지적해 임의 key 제한·trim을 제거하고 primitive round-trip 테스트를 추가했다. 저장소의 재검증 상한 때문에 이 최종 보정 뒤 세 번째 독립 검증은 실행하지 않았다.
- Validation:
  - `corepack pnpm check`가 lint·format·typecheck, Vitest 36 files/134 tests와 production build까지 통과했다.
  - fixture Chromium 4/4와 1440·1024·768·390px shell, 1440·390px 인증 화면, heading 순서·44px password toggle·reduced-motion을 검증했다.
  - main JS 377.15 kB(gzip 118.95 kB), entry CSS 61.58 kB(gzip 12.41 kB)이며 dependency 증가는 없다.
  - 최종 보정 뒤 로컬 검증은 통과했지만 마지막 독립 validator 판정은 보정 전 `NEEDS_CHANGES`이므로 독립 최종 상태는 `NOT_VERIFIED`다.
- Next steps:
  - 실제 Backend 환경의 cross-stack E2E와 screen reader 실기 검수는 후속 수행한다.

## [2026-07-27] Session Summary (현재 구현 화면 제품 UI/UX 통합)

- What was done:
  - router에 존재하는 18개 사용자 route와 전용 404를 하나의 graphite·blue-teal 제품 언어로 개선했다.
  - token·공용 UI, desktop sidebar·mobile drawer, 2열 인증 shell, 업무별 정보 구조와 loading·empty·error·status 표현을 연결했다.
- Key decisions:
  - Dashboard는 가상 KPI 없이 profile·documents·job 등록·Agent Run으로 이동하는 실제 작업 공간만 제공한다.
  - 문서 parse/근거 추출, 공고 업무/추출, Run business/연결 상태를 계속 분리하고 query·mutation·cleanup 순서는 유지했다.
- Issues encountered:
  - 직접 캡처에서 미사용 PrimeVue 전역 초기화가 라이선스 배지를 렌더링해 초기화만 제거하고 dependency·lockfile은 유지했다.
  - full E2E 선택 실행이 실제 Backend 의존 spec까지 포함해 timeout되어 fixture 두 파일을 직접 지정했다.
- Validation:
  - `corepack pnpm check`가 35 files/128 tests와 production build까지 통과했고 fixture Chromium 4/4가 통과했다.
  - 1440·1024·768·390px overflow와 mobile drawer focus를 자동 검증하고 1440·390px 화면을 직접 시각 검수했다.
  - 실제 Backend·PostgreSQL·MinIO/Fake gateway가 필요한 profile·Document·Job actual E2E와 screen reader 실기 검수는 현재 환경에서 실행하지 않았다.
  - read-only validator가 미구현 기능·계약·접근성·반응형·문서 검토를 `PASS WITH WARNINGS`로 완료했다.
- Next steps:
  - cross-stack actual E2E와 screen reader 수동 검수는 필요한 서비스가 준비된 환경에서 수행한다.

## [2026-07-27] Session Summary (P5 Jobs 화면·실제 E2E 구현)

- What was done:
  - strict Zod Job 계약·API·Vue Query와 `/jobs`, `/jobs/new`, `/jobs/:jobId/overview`를 구현했다.
  - URL query filter, 201/202 생성, 상태·retry·manual·delete, 409 비교·재적용과 기존 Agent Run stream invalidation을 연결했다.
- Key decisions:
  - 업무/추출 상태 badge를 분리하고 NEEDS_MANUAL_INPUT은 수동 입력, FAILED는 retry와 수동 입력을 제공한다.
  - P5 projection은 `null`, `false`, `[]`, `0`으로 엄격히 검증하고 P6 분석 DTO·route를 만들지 않는다.
- Issues encountered:
  - 최초 validator가 P6 DTO 선행 구현과 NEEDS_MANUAL_INPUT retry 노출을 찾아 한 차례 보정했다.
- Validation:
  - `corepack pnpm check`가 32 files/122 tests와 production build까지 통과했고 P5 실제 Chromium E2E 5/5가 통과했다.
- Next steps:
  - P6 계약 구현 시 Job detail child route 아래 analysis 화면을 별도 추가한다.

## [2026-07-19] Session Summary (P4 Documents 화면·실제 E2E 구현)

- What was done:
  - Document DTO/API, user-scoped query key, lazy 목록·상세 route와 upload·manual·reparse·download·delete UI를 구현했다.
  - 두 상태 축과 partial success, evidence 편집·승인·거절·SOURCE_DELETED read-only, P2 증빙 문서 selector를 연결했다.

- Key decisions:
  - SSE는 invalidation 신호이며 REST Document 상태가 최종 원천이다. logout·401·사용자 전환·delete에는 cache와 EventSource를 정리한다.

- Issues encountered:
  - 빠른 WAITING_USER 전이에서 detail이 stale일 수 있어 해당 SSE event에도 document query invalidation을 추가했다.

- Validation:
  - `corepack pnpm check`가 26 test files/95 tests와 production build를 통과했다.
  - main bundle은 517.24 kB(gzip 143.50 kB), 기준선 대비 +8.77 kB(+2.67 kB gzip)이고 Document route는 lazy chunk다.
  - P3 Playwright 2/2와 실제 Backend P4 Playwright 4/4가 통과했다.
  - 최종 read-only Validator가 상태 UI·owner cache·SSE cleanup과 실제 E2E 근거를 포함해 `PASS`했다.

- Next steps:
  - P4 Frontend는 완료됐으며 P5 이후 화면과 Dashboard·AI settings는 추가하지 않는다.

## [2026-07-19] Session Summary (P3 Agent Run 목록·상세·SSE Frontend 구현)

- What was done:
  - exact enum·nullable DTO, repeatable filter·pagination·sort client와 retry·cancel mutation을 구현했다.
  - lazy list/detail page, 안전한 단계·비용 projection과 최근 active Run Drawer를 구현했다.
  - snapshot-first SSE, reconnect·polling과 session boundary cleanup을 구현했다.

- Key decisions:
  - reconnect는 1/2/5초 총 3회 뒤 5초 REST polling이며 10/30초 값은 이번 threshold에서 사용하지 않는다.
  - 실제 비용은 고정 catalog 기반 billable estimate로 안내하며 provider/model/prompt 내부값을 표시하지 않는다.
  - Header count는 같은 owner-scoped 목록 query의 `totalElements`를 사용하고 Drawer 목록만 최근 5개로 제한해 Dashboard 집계처럼 추정하지 않는다.

- Issues encountered:
  - 첫 E2E의 중복 progress locator와 첫 전체 check의 repeatable query expectation을 보정했다.

- Validation:
  - `corepack pnpm check`에서 78 tests와 production build가 통과했고 Chromium Agent Run fixture 2/2가 통과했다.
  - main JS는 508.47 kB/gzip 140.83 kB, CSS는 18.52/4.44 kB이며 Agent Run UI는 별도 lazy chunk다.
  - 최종 read-only Validator가 reconnect·polling·session cleanup과 lazy route 회귀를 포함해 `PASS`로 판정했다.

- Next steps:
  - P4 이후 typed resource deep link·query invalidation을 실제 domain route와 연결한다.

## [2026-07-19] Session Summary (P2 프로필·온보딩·evidence Frontend 구현)

- What was done:
  - Backend 25개 profile operation의 TypeScript 계약·client와 모든 user-scoped query key를 구현했다.
  - 기본 form, 프로필 5종 CRUD, 대표 학력, evidence filter·편집·검토, `SOURCE_DELETED` read-only와 409 비교·재적용 UI를 구현했다.
  - P2 onboarding과 profile route·returnTo·404 회귀, document 기능 비활성 안내를 구현했다.

- Key decisions:
  - 서버 상태는 Vue Query, 인증 사용자·전역 reset만 Pinia, form draft는 component local state로 유지한다.
  - profile incomplete는 경고·진행률 표시이며 route hard gate가 아니다.
  - document 선택과 filter control은 P4 전까지 활성화하지 않는다.

- Issues encountered:
  - GPA conflict snapshot 변환과 onboarding fetch 오류 진행을 unit/component test로 보정했다.
  - Playwright webServer의 Windows pnpm 탐색을 `corepack pnpm dev`로 고치고 중복 text locator를 정확한 heading으로 좁혔다.
  - 첫 최종 `pnpm check`에서 Vitest가 `e2e/profile.spec.ts`를 수집해 실패했으므로 기본 exclude에 `e2e/**`를 추가했다.

- Validation:
  - `Set-Location frontend; corepack pnpm check`에서 lint·format·typecheck, 13개 파일 57개 Vitest와 production build가 통과했다.
  - Playwright Chromium 1개 E2E가 가입→프로필→두 사용자 403/404→cache cleanup→재로그인 흐름으로 통과했다.
  - 최종 read-only validator가 Frontend parity·cache·E2E 근거를 BLOCKER·MAJOR·MINOR 없이 `PASS`로 판정했다.

- Next steps:
  - P2는 완료 상태다.
  - P4 Backend 문서 계약이 확정될 때 document 연결 UI를 활성화한다.

## [2026-07-19] Session Summary (P1 프론트엔드 인증·route 기반 구현)

- What was done:
  - Axios `/api/v1` client, Cookie·CSRF bootstrap/교체, typed 오류·field mapping과 QueryClient를 구현했다.
  - unknown/authenticated/anonymous auth store, me bootstrap, signup·login·logout과 401·logout cleanup을 구현했다.
  - 두 Form, PublicLayout·AppLayout, 안전한 `returnTo`, route guard, onboarding/dashboard shell과 404를 구현했다.

- Key decisions:
  - 백엔드 직접 성공 DTO를 소비하고 성공 envelope를 가정하지 않는다.
  - logout·401 시 EventSource cleanup port, query 취소·cache clear, Pinia reset과 현재 사용자 draft purge 순서를 보장한다.
  - P1에는 resource별 draft와 프로필 Form·Dashboard 카드·문서 UI를 만들지 않는다.

- Issues encountered:
  - server field error 시 disabled 입력을 focus할 수 없던 문제를 component test로 재현해 submitting 해제 후 focus하도록 수정했다.
  - 401 또는 logout 뒤 보호 route에 남지 않도록 auth store 변화를 router가 관찰해 안전한 login returnTo로 이동하게 했다.

- Validation:
  - 구현 에이전트와 루트에서 `Set-Location frontend; corepack pnpm check`를 각각 실행해 ESLint, Prettier, vue-tsc, 7개 파일 35개 Vitest, 361 module production build가 통과했다.
  - auth 상태·Form·returnTo·guard·401 cleanup·두 사용자 cache 분리·shell·404를 unit/component test로 검증했다.
  - 실제 브라우저 cross-stack Playwright는 실행하지 않았고 외부 provider 호출도 없었다.

- Next steps:
  - P2 프로필·dashboard 실제 UI는 새 backend 계약이 고정된 뒤 typed client와 함께 추가한다.
  - 브라우저 기반 통합 환경이 준비되면 실제 Cookie·CSRF signup→login→logout smoke flow를 추가한다.

## [2026-07-17] Session Summary (Vue 프론트엔드 초기 환경 구성)

- What was done:
  - 당시 구현 상태:
    - Vue 3, TypeScript, Vite, pnpm 기반 개발 환경과 주요 plugin이 구성되어 있다.
    - `src/main.ts`가 Pinia, Vue Router, Vue Query, PrimeVue Aura theme을 등록한다.
    - `App.vue`는 `RouterView`만 제공하고 router의 `routes`는 비어 있어 제품 화면은 아직 없다.
    - unit/component test와 E2E test 파일은 아직 없으며 실제 비즈니스 API client도 구현되지 않았다.
  - 완료된 작업:
    - 프론트엔드 초기 개발 환경과 고정된 pnpm 의존성 구성을 마련했다.
    - Vite 개발 서버의 `/api` proxy, TypeScript strict 설정, ESLint, Prettier, Vitest, Playwright 설정을 추가했다.
    - 애플리케이션 진입점과 전역 plugin 등록, Tailwind와 PrimeVue theme 연결을 구성했다.
    - 작업 목적에 따라 `frontend/index.md`와 이 문서를 생성해 모듈 책임, 주요 파일, 실제 미구현 범위를 문서화했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 프론트엔드 비즈니스 화면이나 사용자 여정 구현은 없다.
    - 프론트엔드와 하위 영역의 초기 문서 계층 구성은 이번 작업에서 완료했다.

- Key decisions:
  - 서버 상태는 TanStack Vue Query, 최소한의 전역 클라이언트 상태는 Pinia로 분리한다.
  - 기능 구조가 확정되기 전 빈 page/component/store 계층을 미리 대량 생성하지 않는다.
  - `package.json`의 통합 `check`를 프론트엔드 기본 검증으로 사용하며, 이 명령의 Prettier 단계가 모듈 내 Markdown도 검사하도록 유지한다.
  - API는 `/api/v1` 직접 성공 DTO와 표준 오류 응답 계약을 기준으로 typed client를 구성할 예정이다.

- Issues encountered:
  - router route가 비어 있고 `App.vue`에 `RouterView` 외 UI가 없어 현재 앱은 실제 제품 화면을 제공하지 않는다.
  - `pnpm check`는 `--passWithNoTests`로 unit test가 없어도 통과할 수 있으므로 check 성공을 기능 테스트 존재로 해석하면 안 된다.
  - `pnpm check`에는 Playwright 실행이 포함되지 않으므로 E2E 검증은 별도로 수행해야 한다.

- Validation:
  - 기본 검증 명령: `Set-Location frontend; corepack pnpm check`
  - 문서 링크·형식 확인 명령: `corepack pnpm exec prettier --check index.md progress.md e2e/index.md e2e/progress.md src/index.md src/progress.md src/router/index.md src/router/progress.md src/styles/index.md src/styles/progress.md`
  - 두 명령 모두 성공했다. 통합 check에서 ESLint, Prettier, TypeScript 검사와 production build가 통과했고, Vitest는 test file이 없어 `--passWithNoTests`로 종료 코드 0을 반환했다.
  - 10개 문서의 필수 섹션과 상대 링크도 PowerShell 정적 검사로 확인했다. E2E는 테스트가 없어 이번 문서 작업에서 실행하지 않았다.

- Next steps:
  - 페이지 명세에 따른 layout, 인증·온보딩, 대시보드와 도메인별 route/page 구현
  - typed API client, 공통 오류 정규화, Vue Query query/mutation과 필요한 Pinia store 구현
  - loading, empty, error, success 상태와 접근성·반응형 UI 구현
  - Vitest/Vue Test Utils 테스트와 핵심 사용자 여정 Playwright 테스트 추가
