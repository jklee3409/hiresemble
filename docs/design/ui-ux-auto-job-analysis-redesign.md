# Hiresemble UI/UX·공고 자동 분석 재설계 메모

## 문서 목적

이 문서는 2026-08-02 기준 저장소와 공개 채용 서비스 화면을 감사한 결과를 구현 전에 고정한다. 기준 명세를 대체하지 않으며, 자동 분석의 공개 계약은 같은 작업에서 `docs/spec/`와 OpenAPI에 동기화한다.

## 현재 구조 감사

### 레이아웃과 공통 컴포넌트

- `AppLayout.vue`는 240px 고정 좌측 사이드바, 문맥을 반복하는 상단 헤더, 사이드바 하단 온보딩 카드와 첫 글자 아바타, 별도 로그아웃 버튼을 함께 노출한다. 같은 사용자 정보와 페이지 제목이 중복되고 콘텐츠 폭이 줄어 B2C 취업 서비스보다 관리 화면에 가깝다.
- `PageHeader.vue`는 대부분의 페이지에서 eyebrow, 큰 제목, 설명을 같은 순서로 강제한다. 상세 탭 안에서도 큰 제목을 다시 표시해 공고명과 하위 기능명이 경쟁한다.
- `main.css`의 primary `#3157ff`는 브랜드 자산으로 유지할 수 있으나, 거의 모든 정보 단위가 흰색·1px 테두리·큰 radius 카드로 표현된다. 문서형 정보와 action resource의 시각적 무게가 구분되지 않는다.
- `StatePanel`, `StatusBadge`, button·field 기본 규칙, focus ring, reduced-motion 처리는 재사용 가치가 있다. 계층과 문구 variant를 보강하되 기능 계약은 유지한다.
- 제거 또는 재설계 대상은 고정 dark sidebar, 하단 profile card, initial avatar, 중복 문맥 헤더, 모든 section의 kicker 반복, nested card다.

### 핵심 사용자 여정

- 공고 등록은 수동 본문이면 201, URL 추출이면 `JOB_POSTING_EXTRACTION` run을 포함한 202를 반환한다. 등록 화면은 상세로 이동하지만 분석은 사용자가 별도 탭에서 품질 모드를 선택하고 `POST /jobs/{jobId}/analysis`를 호출해야 한다.
- URL 추출의 domain apply는 owner, 최신 run, 공고 version을 확인하고 본문·메타·content hash를 원자적으로 반영한다. `WAITING_USER` 본문 보완은 기존 run을 재개한다.
- 분석은 공고 version, content hash, 프로필, 확인한 경험, rubric, embedding policy, quality mode로 context hash를 만든다. run 생성·budget 예약은 한 transaction이고 Provider 호출은 commit 뒤 worker가 수행한다.
- 현재 자동 후속 의도나 공고 revision별 unique claim은 없다. 브라우저 연쇄 호출이나 after-commit 이벤트만 추가하면 브라우저 종료, process crash, extraction replay에서 누락·중복 가능성이 남는다.
- 공고 본문은 `<pre>`와 고정 최대 높이·내부 스크롤로 표시되어 heading/list 구조와 페이지 흐름을 잃는다. 편집 textarea와 읽기 화면의 인상이 거의 같다.
- `JobAnalysisPage.vue`는 최초 결과가 없을 때 큰 실행 카드와 `경제형/균형형` select를 핵심 CTA로 노출한다. 자동 분석 전환 뒤에는 진행 journey, 필요한 사용자 입력, 결과 요약과 다음 행동이 우선되어야 한다.

### 2026-08-04 공고 분석 결과 화면 재감사

- 사용자가 분석 탭에 들어오는 목적은 적합도 숫자를 구경하는 것이 아니라 지원 여부를 판단하고 자기소개서에서 강조할 경험과 보완할 조건을 정하는 것이다.
- 첫 화면은 지원 가능 여부, 적합도, 분석 커버리지와 결과 최신 여부를 함께 보여야 한다. 점수만 큰 색상 카드로 분리하면 지원 자격과 근거 부족이 뒤로 밀린다.
- 결과가 있을 때의 primary action은 `자기소개서 준비하기` 하나다. 재분석, 프로필 보완, 공고 수정과 면접 준비는 결과를 읽은 뒤 선택하는 secondary action으로 낮춘다. 결과가 없거나 실패한 상태에서는 `공고 분석 재실행`이 primary가 된다.
- 기존 결과 화면은 요약 지표, 상태별 개수, category 점수, 공고 요약, 강점·보완점, 경험, 기준과 이력을 각각 둥근 surface로 분리했다. 동일한 카드가 반복되고 파랑·초록·노랑 soft background가 넓게 사용되어 정보 중요도보다 컴포넌트 모양이 먼저 보였다.
- 상단의 중복 `공고 분석` 문맥 제목, 결과보다 앞선 재분석 카드, 강점·보완점의 번호 원형과 흰색 내부 행, 경험·기준별 독립 카드는 제거한다. 최신 결과는 하나의 report surface 안에서 `지원 판단 → 요건 매칭 → 공고 핵심 → 지원 전략 → 활용 경험 → 조건별 근거`로 이어지고 내부는 여백과 구분선으로 나눈다.
- desktop은 지원 판단과 다음 행동을 나란히 두고 요건 분포·category 점수를 compact row로 제공한다. mobile은 적합도를 먼저 보이는 2열 판단 요약, 가로 스크롤 상태 filter와 접힌 상세를 사용하고 보조 설명을 줄여 desktop 카드를 단순 세로 적층하지 않는다.

## 공개 레퍼런스에서 채택한 UX 원칙

2026-08-02에 Playwright로 점핏의 홈·공고 목록·상세와 자소설닷컴의 홈·채용 달력을 desktop/mobile에서 확인했다. 화면 자산이나 문구는 복제하지 않고 다음 원칙만 사용한다.

- 점핏은 상단 global navigation과 넓은 콘텐츠 열을 사용하고, 공고 상세에서 공고명·회사·태그를 먼저 제시한 뒤 `기술스택`, `주요업무`, `자격요건`, `우대사항`을 긴 페이지 흐름으로 분리한다. CTA는 desktop 우측 보조 열에 집중한다.
- 자소설닷컴은 상단 주요 여정, 검색·필터, 일정 정보를 높은 밀도로 배치하면서 날짜·마감·회사명처럼 판단에 필요한 메타를 반복 가능한 행으로 정렬한다.
- 두 서비스 모두 주요 기능을 좌측 관리자 메뉴에 고정하지 않고, 콘텐츠 종류에 맞춰 목록·문서·달력의 밀도를 달리한다.
- Hiresemble에는 상단 navigation, 판단에 필요한 메타 우선, 문서형 본문의 section 분리, 한 화면 한 primary CTA, mobile 핵심 여정 축약을 적용한다.

### 2026-08-04 사용자 지정 Landing 레퍼런스 적용

- 코들은 큰 두 줄 Hero와 짧은 가치 문장, 반복되는 기능 서사를 긴 scroll rhythm으로 연결한다. Hiresemble에는 큰 Hero, 단계별 DOM 제품 데모와 section reveal을 유지하고 가짜 도입 수치·수상·후기는 가져오지 않는다.
- 퓨어글로벌은 짧은 영문 kicker, 굵은 headline, 번호가 있는 사업 영역과 어두운 CTA로 장면을 전환한다. Hiresemble에는 실제 5단계 제목만 흐르는 motion band와 dark final CTA를 적용해 정보 장면을 구분한다.
- FLEX STUDY는 loading sequence, glow, 몰입형 portfolio 진입으로 강한 첫인상을 만든다. 서비스 진입을 막는 loader나 무거운 3D asset은 복제하지 않고 Hero orbit·glow·제품 demo progress처럼 작업 흐름을 설명하는 장식만 사용한다.
- 인앱 Browser runtime은 연결 가능한 browser가 없었으나 Playwright CLI의 실제 Chromium 1440px fallback으로 세 페이지를 직접 확인했다. 코들은 2,600px scroll 뒤 큰 문장과 기울어진 기능 카드 장면, 퓨어글로벌은 3D Hero·ticker와 3/5초 반복 ambient motion 8개, FLEX STUDY는 3D 진입·디자이너/개발자 분기와 3초 light-flow 4개를 확인했다. 효과를 그대로 복제하지 않고 Hiresemble의 지원 준비 흐름을 설명하는 motion으로 번역한다.

## 내비게이션 대안과 결정

| 대안                                  | 장점                                                        | 위험                                                                          | 결정                    |
| ------------------------------------- | ----------------------------------------------------------- | ----------------------------------------------------------------------------- | ----------------------- |
| 얇은 좌측 nav + 문맥 헤더             | 기존 route와 가까워 전환 비용이 작다                        | 아이콘 rail과 중복 header가 관리자 화면 인상을 유지하고 공고 본문 폭을 줄인다 | 제외                    |
| 상단 중심 nav + 페이지별 보조 nav     | 여섯 핵심 여정을 직접 노출하고 문서·공고 화면 폭을 확보한다 | 좁은 desktop에서 메뉴가 혼잡할 수 있다                                        | 채택                    |
| desktop 축약 rail + mobile bottom nav | 큰 화면에서 공간을 아낀다                                   | label 발견성이 낮고 initial/icon 중심 템플릿 인상이 남는다                    | mobile 패턴만 일부 채택 |

채택 구조는 다음과 같다.

- desktop: 72px top bar에 brand와 `홈 / 내 정보 / 이력서·자료 / 관심 공고 / 자기소개서 / 면접 준비`를 둔다. `AI 작업`, 이용 가이드, 닉네임 변경, 로그아웃은 진행 상태와 account menu에서 접근한다.
- 좁은 화면: brand와 account trigger가 있는 compact header, 하단에 `홈 / 공고 / 자기소개서 / 면접 / 더보기`를 둔다. 더보기 sheet에서 내 정보, 이력서·자료, AI 작업, 이용 가이드, 계정 기능을 모두 제공한다.
- 페이지마다 별도 global 문맥 헤더를 반복하지 않는다. 목록은 제목·filter·primary action, 상세는 resource header·status·action·subnav, 편집은 대상·저장 상태·action variant를 사용한다.

## 디자인 토큰

- 브랜드 primary는 기존 hue를 유지한 `#3157ff`를 기준으로 50–900 scale, hover `#2448e8`, active `#1d3bc2`, focus ring의 반투명 값을 정한다.
- canvas는 차가운 회색 한 면으로 채우지 않고 `#f7f8fc`와 white document surface를 구분한다. border는 구조 경계에만 쓰고 section 계층은 spacing과 typography를 우선한다.
- text는 strong/default/muted/subtle 네 단계, semantic은 success/warning/danger/info를 primary와 분리한다.
- typography는 12/14/16/18/24/32px 역할 scale, 본문 line-height 1.65, 공고 본문 최대 72ch를 기준으로 한다.
- spacing은 4px base의 4/8/12/16/20/24/32/40/48/64 역할 scale을 사용한다. `resource header → tabs` 24px, `tabs → body` 32px, `page heading → first section` 32px, `section → section` 48px를 공통 변수로 둔다.
- control height는 compact 36px, default 44px, large 48px이며 mobile tap target은 최소 44px다. radius는 control 10px, resource card 16px, pill full만 사용한다.
- content max width는 일반 1180px, 읽기 열 760px, 넓은 editor 1280px다. desktop navigation 전환 breakpoint는 1120px, content breakpoint는 768px다.

## 주요 화면 wire-level 구성

### 홈

`인사말 + 공고 등록 CTA` → `지금 준비 중인 지원` → `다음 할 일` → `마감이 가까운 공고` → `최근 활동`. 동일 너비 지표 카드 다섯 개 대신 중요한 항목을 2:1 계층으로 배치한다.

### 공고 목록과 등록

- 목록: 제목·등록 CTA → compact search/filter → 공고 행. 회사·직무·마감·분석 상태를 먼저 표시하고 기술 상태는 사용자 문구로 변환한다.
- 등록: URL을 primary 입력으로 두고 `직접 입력`은 disclosure로 제공한다. 제출 뒤 “공고 내용을 읽고 분석까지 이어진다”는 짧은 기대만 안내한다.

### 공고 상세

`뒤로 가기` → `회사 / 공고명 / 핵심 메타 / 상태 / 원본 링크` resource header → 독립 sticky tabs → 32px gap → 하위 body.

- 정보 탭: 선택적으로 표시되는 메타 strip → 진행 journey 또는 안내 → 읽기 전용 document view → 수정 시에만 editor.
- 분석 탭: 자동 진행 journey → 지원 판단과 단일 primary CTA → 요건 매칭 → 공고 핵심 → 강점·보완점 → 활용 경험 → 조건별 근거 → 접힌 분석 이력 → 보조 재분석.
- 자기소개서·면접 탭은 이미 보이는 공고명을 큰 제목으로 반복하지 않는다.

### 가이드

`/guide`는 서버 완료 상태 없이 언제든 다시 보는 route다. 실제 제품 component와 같은 token·문구를 사용하는 mini preview를 포함하고, `내 정보 → 자료 → 공고 자동 분석 → 자기소개서 → 면접` 순서를 설명한다. 강제 tour나 영구 localStorage dismiss는 추가하지 않는다. 가입 직후 onboarding 마지막과 홈의 작은 진입점에서 연결한다.

## 자동 분석 backend 설계

### 내구성 경계

다음 available migration인 V16에 공고 revision별 자동 분석 의도를 저장한다.

- `job_auto_analysis_requests`는 owner, job, `job_version`, `job_content_hash`, 고정 `BALANCED`, 상태, claim lease, safe failure, deterministic request/run ID를 가진다.
- `(user_id, job_posting_id, job_version)` unique 제약으로 같은 revision의 자동 접수를 최대 한 번으로 제한한다.
- 수동 본문 공고 생성, 사용할 수 있는 본문으로 수정, extraction 성공 domain apply가 자신의 transaction에서 의도를 insert한다. `NEEDS_MANUAL_INPUT`과 실패 상태에는 만들지 않는다.
- request ID를 `WorkflowLaunchCommand.requestedAgentRunId`로 사용한다. run commit 뒤 projection update 전에 process가 종료되어도 reconciliation이 같은 ID를 조회해 `LAUNCHED`로 복구하므로 두 번째 run을 만들지 않는다.
- 짧은 `PENDING → CLAIMED → LAUNCHED | BLOCKED | SUPERSEDED` 상태와 lease를 사용한다. scheduler는 pending과 만료 claim을 재조정하고, 이미 존재하는 deterministic run을 먼저 확인한다.
- launch transaction은 기존 `JOB_ANALYSIS` workflow, budget reservation, typed resource link를 그대로 사용하며 Provider를 호출하지 않는다. 실제 모델 호출은 기존 worker 경계에서만 일어난다.
- budget/quota와 prerequisite 실패는 공고·추출 결과를 rollback하지 않고 safe blocked projection으로 남긴다. 공고 version이 바뀐 claim은 `SUPERSEDED`로 끝내고 새 revision 의도를 사용한다.
- 자동 최초 분석과 자동 재접수는 `BALANCED`다. 기존 public 수동 endpoint, `AiQualityMode`, ECONOMY, force reanalysis, analysis history는 유지한다.

### 공개 projection

기존 DTO 필드는 삭제하지 않고 `JobDetailDto`에 additive `automaticAnalysis` projection을 추가한다. 상태, 고정 품질, run ID, 안전한 오류만 전달한다. `JobCreationAcceptedDto.agentRunId`는 URL 등록 시 기존 extraction run 의미를 유지해 idempotency replay와 기존 client를 깨지 않는다.

Frontend는 extraction workflow와 analysis workflow run을 각각 조회하되 하나의 사용자 journey로 합친다. 내부 step key, provider, model, prompt, raw error는 표시하지 않는다.

## 공고 본문 parser

- pure deterministic parser가 원문을 줄 단위로 정규화하고 독립 heading, `-`·`•`·`·` 목록, 숫자 목록, 빈 줄 문단, 안전한 `http/https` URL만 node로 만든다.
- heading 사전은 공고에서 자주 쓰는 `주요 업무`, `지원 자격`, `우대 사항`, `전형 절차`, `근무 조건`, `지원 방법` 계열로 제한한다. 확신하지 못한 줄은 paragraph로 fallback한다.
- `v-html`과 HTML 신뢰는 사용하지 않는다. 링크는 새 창과 `noopener noreferrer`, 본문은 72ch와 충분한 행간을 사용한다.
- 원문은 바꾸지 않는다. 편집 버튼을 눌러야 textarea가 나타나며 저장·취소·409 version conflict를 기존 API로 처리한다.

## 주요 수정 범위

- Frontend 기반: `styles/main.css`, `layouts/AppLayout.vue`, `PageHeader.vue`, 공통 상태·button·field 규칙, router, 관련 unit test.
- 공고 여정: jobs contracts/API/queries, `JobDetailLayout.vue`, `JobNewPage.vue`, `JobOverviewPage.vue`, `JobAnalysisPage.vue`, document parser와 journey component, unit/E2E test.
- 확장: Dashboard, profile shell, documents, cover letters, interviews, agent runs, onboarding, auth 최소 일관성, `/guide`.
- Backend: V16, 자동 분석 domain/application/store/coordinator/projection, creation·mutation·extraction apply 연결, integration test.
- 계약: functional/page/api/db/tech stack, system architecture, implementation plan, OpenAPI와 추적 문서.

## 검증 계획

- Frontend: parser와 safe fallback, AppLayout account/mobile navigation, tab active·hover·focus, edit mode, auto journey, result summary, blocked/manual CTA, guide와 keyboard unit test 후 `corepack pnpm check`.
- Backend: manual create, extraction apply, replay/retry, waiting user, update, budget failure, deterministic crash recovery, restart reconciliation, owner isolation, force reanalysis를 PostgreSQL/Testcontainers와 Fake gateway로 검증 후 `gradlew check`.
- Browser: 같은 fixture와 1440×1000, 390×844에서 dashboard, profile, documents, jobs list/new/detail/analysis, cover letters, interviews, agent runs, onboarding/guide를 before/after 캡처한다. 핵심 등록→추출→자동 분석→자기소개서 이동 actual E2E는 실제 Provider 없이 실행한다.
- 접근성: semantic heading, focus order·복귀, account/more menu Escape, tab keyboard, 44px target, overflow, contrast, reduced motion을 확인한다.
