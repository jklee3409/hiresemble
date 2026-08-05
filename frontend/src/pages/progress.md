# Progress

## Overview

공개 Landing과 P1 인증부터 P8 Interview preparation·question set·answer feedback, `/guide`, 현재 route 기반 dashboard와 전용 404를 일관된 제품 UI로 관리한다.

## [2026-08-05] Session Summary (공고 분석 모바일 판단·차트 재우선순위)

- What was done:
  - 모바일 hero를 104px 단일 적합도 gauge와 결정 문장, 커버리지·분석 시각 meta로 압축하고 desktop 지원 가능성·커버리지 tile을 숨겼다.
  - primary CTA를 첫 viewport 전폭 action으로 유지하고 category chart 기본 접힘, 상태 legend 4행, 강점·보완 첫 항목 disclosure를 적용했다.
- Key decisions:
  - desktop의 216px 2중 ring과 전체 비교 정보는 유지하고 48rem 이하에서만 정보 우선순위를 바꾼다.
- Issues encountered:
  - 기존 모바일 E2E의 세 metric 844px 계약이 가이드와 충돌했다.
- Validation:
  - Frontend check 67 files/282 tests·production build와 관련 Chromium 2/2 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석 결과 화면 디자인 가이드 구현)

- What was done:
  - `JobAnalysisPage.vue`의 결과 영역을 카드 적층에서 단일 report surface로 재구성했다. 내부 블록은 1px 구분선과 여백으로만 나누고 그림자는 패널에 1회만 적용한다.
  - 적합도를 270° 2중 링 게이지(외부 `fitScore`, 내부 `analysisCoverage`)로 표현하고, 요건 분포는 2px 간격·직접 개수 라벨·상태별 텍스처를 가진 100% 누적 막대로, 카테고리 충족도는 단일 hue 가로 막대로, 분석 이력은 `analysisVersion` 정수 축 추이 라인 차트로 바꿨다.
  - 조건 행에 상태 marker와 score/weight meter를 추가하고, 강점·보완 항목에 의미 있는 icon을 붙였다. filter는 채움 pill로 교체했다.
  - `<style scoped>` 블록에 누적돼 있던 3세대 중복 CSS(약 3,260줄)를 단일 구현(약 1,260줄)으로 대체했다.
- Key decisions:
  - 기존 class 이름과 DOM 계약(`analysis-result__metrics > div` 3개, `abbr[title]`, `analysis-insight li > p`, `analysis-criterion`, pagination `aria-label` 등)을 유지해 화면 계약과 테스트 단언의 의미를 바꾸지 않았다. script setup의 상태·query·mutation·watch 로직은 그대로 두고 게이지·추이 계산 computed만 추가했다.
  - `fitScore`가 `null`이면 게이지를 렌더하지 않고 "산정하지 못함" 문구만 남긴다. 0점으로 그리지 않는다.
  - 이력이 1건이면 추이 차트를 렌더하지 않는다.
  - 막대 길이는 반올림하지 않은 비율을 쓰고 라벨만 기존 `roundToFive` 계약을 따른다.
- Issues encountered:
  - `animation-fill-mode: both`와 `from` 키프레임만 선언한 조합에서 게이지 아크와 카테고리 막대가 종료 후에도 0 상태로 고정됐다. `backwards`로 바꾸고 아크 전체 길이를 element별 CSS 변수로 분리해 해결했다.
  - `.analysis-breakdown__filter--active`가 `.analysis-breakdown__filters button`보다 specificity가 낮아 선택 상태가 적용되지 않았다. 선택자를 결합해 해결했다.
  - `minmax(19rem, 1fr)` grid가 320px에서 컨테이너를 넘어 가로 스크롤을 만들었다. `minmax(min(19rem, 100%), 1fr)`로 해결했다.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `prettier --check .`, `vite build` 통과.
  - build 산출 CSS로 결과 화면 DOM을 렌더해 computed style을 검증했다. panel radius 20px·shadow 1회, 게이지 dashoffset 107.44/43.83(70%/85%), 카테고리 막대 transform none(최종 상태 표시), 선택 filter `#0f1420`/흰 텍스트, PARTIAL 세그먼트 45° 텍스처, 320/375/768/1180/1440px 전부 가로 스크롤 0을 확인했다.
  - `vitest`는 실행하지 못했다. 이 저장소는 Node 24를 요구하는데 실행 환경이 Node 20이라 `corepack pnpm`이 `node:sqlite` 부재로 기동하지 않고, 로컬 `vitest` 직접 실행도 jsdom 의존 `html-encoding-sniffer`의 `ERR_REQUIRE_ESM`으로 실패한다. 변경하지 않은 `src/features/jobs/filters.test.ts`에서도 동일하게 실패해 환경 문제임을 확인했다.
- Next steps:
  - Node 24 환경에서 `corepack pnpm check`와 `frontend/e2e/job-analysis.spec.ts`를 실행해 회귀를 확인한다.

## [2026-08-04] Session Summary (공고 분석 판단·근거 정보 밀도 보정)

- What was done:
  - 최신 결과를 동적 판단 heading, 실제 summary, 적합도·지원 가능성·커버리지 행과 자기소개서 CTA로 압축하고 OUTDATED reason을 기본 접힘 disclosure로 변경했다.
  - 하위 요건·공고 핵심·강점/보완·근거·criterion·history를 반복 카드 대신 section divider와 compact row로 표시했다.
- Key decisions:
  - 점수와 분석 데이터는 변환하지 않고 기존 5점 단위 표시만 유지한다. 결과가 있을 때 재분석은 상단 보조 action으로 제공하며 하단 중복 command는 제거한다.
- Issues encountered:
  - 초기 mobile geometry에서 마지막 metric이 첫 viewport 밖이었고 mobile 전용 spacing 보정으로 해결했다.
- Validation:
  - `JobAnalysisPage.test.ts` 포함 집중 Vitest와 전체 unit 282건, desktop/mobile Chromium·visual capture, type/lint/format/build 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Landing·공고 분석 결과 presentation 완성)

- What was done:
  - Landing hero에 떠 있는 product signal과 journey flow를 추가하고 demo scene 전환에 진행 상태·depth 효과를 연결했다.
  - 공고 분석 결과에 시각적 판단 요약, 구분된 section, 5개 단위 criterion pagination, 회전 indicator disclosure와 visible keyboard focus를 적용했다.
- Key decisions:
  - chart는 API 원값을 바꾸지 않는 SVG/CSS presentation으로 만들고 service blue와 의미 색상 token을 사용한다. filter 변경 시 첫 페이지로 돌아가며 데이터 축소 시 유효한 마지막 페이지로 보정한다.
- Issues encountered:
  - 인앱 browser 부재는 Playwright CLI 실제 Chromium fallback으로 보완했고 외부 reference의 scroll 장면과 반복 animation timing을 확인했다.
- Validation:
  - page 집중 Vitest 20건, type check, 실제 Chromium 1440·390px와 reduced-motion·overflow·pagination·2열 geometry 검증 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 결과 판단·다음 행동 중심 재구성)

- What was done:
  - `JobAnalysisPage`의 중복 제목과 상단 재분석 card를 제거하고 결과 hero에 적합도·지원 가능 여부·커버리지와 자기소개서 primary CTA를 통합했다.
  - 요건 분포·category 점수·공고 핵심·강점/보완·활용 경험·조건 근거를 하나의 report 안의 compact row와 구분선 목록으로 바꿨다.
- Key decisions:
  - 성공·주의색은 강점/보완의 작은 상단선과 실제 상태 badge에만 사용한다. Mobile은 결과 요약 2열, 상태 filter horizontal scroll과 접힌 상세로 desktop과 다른 밀도를 사용한다.
- Issues encountered:
  - 변경 후 Browser visual 검증은 browser unavailable로 미실행이다.
- Validation:
  - `JobAnalysisPage.test.ts`와 `jobPages.test.ts` 18건, type check, Frontend 전체 check·281 tests·build가 통과했다.
- Next steps:
  - 390px에서 긴 criterion·secondary link wrapping과 full-page 높이를 시각 확인한다.

## [2026-08-04] Session Summary (공고 분석 결과 요약·필터·인사이트 UI)

- What was done:
  - `JobAnalysisPage`의 매칭 보드에 padding을 보강하고 공고 추출 목록을 AI 핵심 요약과 세 개의 접힌 상세로 바꿨다.
  - 강점·보완점은 번호형 insight panel로, 기준 결과는 상태 filter와 친화적 상세 문구로, 이력은 날짜 중심의 접힌 card로 재구성했다.
- Key decisions:
  - 저장된 `analysisSummary`의 제거 요청 문장은 presentation에서 숨기고 나머지 AI 요약은 보존한다.
  - 숫자 version은 사용자 주 제목에서 제외하고 현재 결과·분석 시각으로 식별한다.
- Issues encountered:
  - None.
- Validation:
  - 집중 Vitest 9/9, Frontend 전체 check와 Chromium desktop/mobile 회귀 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 시각 요약·접이식 근거)

- What was done:
  - `JobAnalysisPage`에 coverage metric, match count cards, category progress bars, source item count와 접이식 판정 근거를 추가했다.
- Key decisions:
  - 요건 원문은 세 section card로 유지하고 세부 설명은 필요할 때 펼쳐 읽게 한다.
- Issues encountered:
  - 긴 criterion 설명이 항상 펼쳐져 핵심 분포보다 텍스트가 먼저 보였다.
- Validation:
  - Frontend 전체 67 files/281 tests·production build와 최종 Job Analysis 9 tests/type check 통과.
- Next steps:
  - 실제 장문 공고의 모바일 레이아웃을 확인한다.

## [2026-08-04] Session Summary (공고 분석 최신 Run 표시 우선순위 수정)

- What was done:
  - `JobAnalysisPage`가 최초 자동 분석 Run보다 `queuedAt,desc`로 조회한 최신 `JOB_ANALYSIS` Run을 우선해 상태와 상세 link를 표시하도록 수정했다.
  - 최신 성공 Run과 과거 실패 자동 Run이 함께 있는 회귀 fixture를 추가했다.
- Key decisions:
  - 방금 접수한 local Run, 서버 최신 Run, 최초 자동 Run 순서만 사용하며 별도 상태 복제나 추가 API를 만들지 않는다.
- Issues encountered:
  - 기존 ID 우선순위가 최초 자동 Run을 서버 최신 Run보다 먼저 선택해 성공 결과와 과거 실패 카드가 동시에 노출됐다.
- Validation:
  - Job Analysis 집중 Vitest 9건과 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 실패 카드 재실행 버튼 보완)

- What was done:
  - `JobAnalysisPage`의 terminal 실패 카드에 `공고 분석 재실행` 버튼을 항상 표시하도록 보완했다.
  - 범용 retry 불가 Run은 현재 공고 version과 `forceReanalyze=true`로 새 `BALANCED` 분석을 요청하고, 실패 카드가 보일 때 하단 중복 분석 command는 숨긴다.
- Key decisions:
  - 서버가 허용한 generic retry는 기존 lineage를 유지하고, 그 외 실패의 재실행은 최신 resource snapshot을 사용하는 명시적 분석 요청으로 분리한다.
- Issues encountered:
  - 기존 구현은 `retryable=false`인 실패에서 버튼을 렌더링하지 않아 실제 첨부 화면에 행동 경로가 없었다.
- Validation:
  - Job Analysis 집중 Vitest 8건과 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 사용자 문구·동의 상세 레이아웃)

- What was done:
  - `SignupPage`에서 이메일 형식 보조 문구를 제거하고 비밀번호 안내를 요청된 세 문장과 두 개의 충족 표시로 간소화했다.
  - 서비스·AI 동의 상세를 한눈에 보기, 번호 상세 카드, 강조 안내, 고정 footer 구조로 재설계했다.
- Key decisions:
  - desktop은 중앙 dialog, mobile은 bottom sheet를 유지하되 Modal 전체 대신 본문만 scroll하도록 했다. 상세 확인은 checkbox를 자동 선택하지 않는다.
- Issues encountered:
  - 인앱 Browser가 없어 저장소 Chromium 회귀로 대체했고, 중복 문구 locator 두 건을 보정했으나 재검증 상한 때문에 보정 후 완주는 확인하지 못했다.
- Validation:
  - `authFlow.test.ts` 포함 집중 Vitest 20건과 Frontend 전체 check·build가 통과했다. Chromium 최종 완주는 `NOT_VERIFIED`.
- Next steps:
  - 수정된 공개 인증 shell Chromium 회귀를 다음 회차에 확인한다.

## [2026-08-04] Session Summary (가입·온보딩·공고 등록 입력 UX 보강)

- What was done:
  - Signup field 이탈 검증과 동적 password checklist, Onboarding 지원 자격 fieldset, JobNew 날짜·오전/오후·30분 시각 control을 추가했다.
  - 온보딩 첫 저장에서 기본 프로필과 지원 자격을 각각 현재 version으로 저장하고 eligibility conflict 시 최신 값을 다시 불러온다.
- Key decisions:
  - 지원 자격의 상세 사유는 수집하지 않고 미선택 값을 허용하며, 마감 기본값은 오후 11:30으로 두되 날짜가 없으면 `null`을 전송한다.
- Issues encountered:
  - 신규 eligibility query 때문에 전체 test의 기존 router mock 1건을 보완했다.
- Validation:
  - Page 집중 테스트, Chromium desktop/mobile 회귀와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 비밀번호 안내·동의 상세 Modal)

- What was done:
  - `SignupPage`에 실제 비밀번호 byte 수 안내와 이용약관·개인정보 및 AI 처리 상세 Modal을 추가했다.
  - Modal에 수집 항목·목적·보유 기간·거부 영향, AI 처리 대상·masking·외부 API 보관 가능성·사용자 검토 책임을 사용자 문장으로 구성했다.
- Key decisions:
  - Modal은 checkbox를 자동 선택하지 않고 ESC·배경·닫기·focus trap·trigger focus 복귀·body scroll lock과 mobile sheet를 지원한다.
- Issues encountered:
  - None.
- Validation:
  - `authFlow.test.ts` 포함 집중 Vitest와 전체 Frontend check 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (AI page 활성 실행 복구·단일 재분석 CTA)

- What was done:
  - Document detail, Job overview/analysis/interview와 Cover Letter edit가 persisted active Run을 복구하고 동일 resource의 새 AI command를 비활성화한다.
  - 문서 재분석 확인 문구에 기존 경험 즉시 제거와 downstream 미사용을 명시하고 Job Analysis OUTDATED CTA를 한 개로 줄였다.
- Key decisions:
  - Run 상태 조회를 확인할 수 없는 동안에도 중복 실행보다 안전한 버튼 비활성화를 우선한다.
- Issues encountered:
  - None.
- Validation:
  - 관련 6개 test file 42개 회귀와 최종 Frontend 전체 67 files/275 tests·build가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 중앙 본문·CTA 정렬)

- What was done:
  - Dashboard를 동일한 좌우 레일과 중앙 본문으로 나눠 우측 바로가기를 제외한 헤더·CTA·본문의 중심을 viewport 중심에 맞췄다.
  - `자료 등록`·`공고 등록` CTA의 우측 끝을 중앙 본문 우측 경계에 맞추고 87rem 이하에서는 기존 가로형 바로가기로 전환했다.
- Key decisions:
  - Dashboard의 88rem 외곽 폭과 container sticky 바로가기를 유지하고 page 범위의 CSS grid만 조정했다.
- Issues encountered:
  - 최초 전체 check에서 변경한 Vue·E2E 파일의 Prettier 형식 검사만 실패해 두 파일만 formatter로 정리했다.
- Validation:
  - Dashboard Vitest 5/5, Frontend 전체 check 67 files/269 tests·production build와 Chromium Dashboard 회귀 1/1이 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (프로필 지원 자격 입력 영역)

- What was done:
  - `ProfileBasicPage`에 기존 화면 구조를 유지한 지원 자격 확인 정보 section을 추가했다.
- Key decisions:
  - 자기신고이며 실제 지원 단계에서 재확인이 필요하다는 안내를 form 내부에 표시한다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 분석 결과 hero 문구 보정)

- What was done:
  - 최신 공고 분석 결과 hero의 `최신 분석`·`분석 버전 N` 노출을 단일 사용자 문장으로 교체했다.
  - requirement 내부 출처 경로가 사용자에게 노출되지 않는 page 회귀를 추가했다.
- Key decisions:
  - 분석 버전은 과거 이력 선택 영역에서 계속 제공한다.
- Issues encountered:
  - None.
- Validation:
  - Job Analysis page 집중 테스트 7건과 Frontend 전체 67 files/267 tests 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard sticky 탐색·workspace 문구 보정)

- What was done:
  - Dashboard 우측 섹션 바로가기를 Desktop container sticky로 전환하고 좁은 화면에서는 기존 일반 흐름을 유지했다.
  - 준비 workspace 제목을 중간 단어가 끊기지 않는 두 의미 묶음으로 렌더링하고 Job Analysis 재시도 CTA를 간결하게 변경했다.
- Key decisions:
  - `fixed` positioning이나 전역 focus 변경 없이 Dashboard page 범위의 layout·문구만 조정했다.
- Issues encountered:
  - None.
- Validation:
  - Dashboard·JobAnalysis Vitest 12/12와 Chromium UI shell 3/3 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard section 탐색·Job Analysis 실패 UX)

- What was done:
  - Dashboard의 시각적 중복 제목을 숨기고 screen reader heading은 유지했으며 self-hosted variable Noto Sans KR 제목과 섹션 anchor를 연결하는 비고정 바로가기를 추가했다.
  - Job Analysis의 재분석 품질 control을 제거하고 `BALANCED` 요청으로 고정하며 내부 structured output 문구를 사용자 안내로 변환했다.
- Key decisions:
  - Dashboard 바로가기는 Desktop 우측 일반 flow, 좁은 화면 가로 navigation으로 제공한다.
- Issues encountered:
  - Journey는 완료된 분석 page에서는 숨겨져 Overview에서 노출되므로 Browser nowrap 검증을 실제 노출 route로 이동했다.
- Validation:
  - Dashboard·JobAnalysis·JobDetail unit 13 tests, Frontend 전체 265 tests와 Browser 회귀 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (마감 캘린더 밀도·상태 hierarchy 개선)

- What was done:
  - 캘린더 상단 summary와 월 toolbar, 고정 간격 날짜 grid, today marker, selected surface와 deadline event chip을 B2C dashboard tone으로 재설계했다.
- Key decisions:
  - 기존 click·month navigation·today·desktop/mobile detail 연동과 서울 시간 계약은 변경하지 않았다.
- Issues encountered:
  - 오늘 다음 셀 hover가 선택 외곽선과 시각적으로 겹치는 문제를 grid gap과 inset selection으로 보정했다.
- Validation:
  - Dashboard unit test, Chromium responsive 3/3과 hover cell bounding-box regression 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 사람 icon·주말 캘린더·Guide modal 개선)

- What was done:
  - 커리어 avatar를 사람 SVG로 교체하고 제목 이름만 theme color로 강조했으며 주말과 날짜별 마감 건수, workspace CTA 위치, 장문 modal을 보완했다.
- Key decisions:
  - Calendar cell에 weekday를 명시해 색상 규칙을 testable하게 만들고 modal 본문은 서버의 빈 줄 기준 문단으로 렌더링한다.
- Issues encountered:
  - Dashboard 내부의 미정의 color alias를 기존 brand·muted token에 연결했다.
- Validation:
  - Dashboard·shared UI unit test, Frontend 67 files/265 tests·build와 Chromium 1440·1024·390px 회귀 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 지원 워크스페이스 재구성)

- What was done:
  - 자연스러운 개인화 제목, 커리어 카드·첫 준비·다음 할 일·행동형 요약, 월별 마감 캘린더와 5개 서버 가이드 modal을 구현했다.
- Key decisions:
  - loading·partial error·empty·unknown을 분리하고 mobile deadline은 native `details`, guide는 focus trap dialog를 사용한다.
- Issues encountered:
  - None.
- Validation:
  - Dashboard component/API/router tests와 Frontend 전체 264 tests, Chromium 반응형 회귀 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Landing Hero 크기·카피·데모 control 정리)

- What was done:
  - Hero headline 크기를 기존 대비 약 80%로 낮추고 서비스 소개·이용 흐름·핵심 가치·AI 활용 원칙 heading을 요청 문구로 교체했다.
  - 자동 DOM 데모의 일시 정지·재생 button과 수동 정지 상태를 제거하고 viewport·Page Visibility·reduced motion 기반 lifecycle은 유지했다.
- Key decisions:
  - 명시적인 최신 요청에 따라 수동 control을 제거하되 visual demo는 `aria-hidden`, 전체 흐름은 고정 screen reader 설명으로 제공하고 reduced motion에서는 대표 scene을 정적으로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Landing component Vitest 10/10과 Chromium Landing 7/7, 1440·390·320px screenshot 검수가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Landing Hero·자동 DOM 제품 데모)

- What was done:
  - Hero를 전체 폭 2줄 heading과 하단 설명·CTA/제품 데모 2열로 재구성하고, 정적 preview를 경험 준비→공고 등록→자동 분석→결과→다음 준비의 5개 DOM scene으로 교체했다.
  - `LandingProductDemo.vue`에 단일 timeout loop, viewport·Page Visibility·수동 pause/resume, reduced motion 정지와 unmount cleanup을 구현했다.
  - Hero stagger와 후속 section 최초 진입 reveal을 기본 visible·mount 후 opt-in 방식으로 추가했다.
- Key decisions:
  - 다른 서비스 MP4는 motion 참고에만 사용하고 production asset·문구·UI·오디오는 포함하지 않았으며 새 animation dependency도 추가하지 않았다.
  - 자동 scene은 `aria-live`로 알리지 않고 전체 흐름을 한 번의 screen reader 설명으로 제공한다.
- Issues encountered:
  - 시스템 PATH에 ffmpeg가 없어 임시 `imageio-ffmpeg` 바이너리로 reference metadata와 1초 간격 frame을 분석했다.
- Validation:
  - 관련 ESLint·`vue-tsc`, Landing component/Vitest 11/11, Chromium Landing 7/7과 1440·390·320px 시각 검수가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (서비스 소개 Landing·Dashboard 체크리스트)

- What was done:
  - 서비스 가치, 문제, 5단계, 핵심 가치, AI 활용 원칙과 CTA를 semantic section으로 구성한 `LandingPage`를 추가했다.
  - Dashboard의 신규 사용자 전용 분기를 제거하고 profile·Document·Job별 완료·미완료·unknown 상태를 표시하는 체크리스트를 일반 현황 위에 배치했다.
- Key decisions:
  - 세 항목 모두 완료할 때만 체크리스트를 숨기며 AI 작업 유무와 영구 dismiss 상태는 사용하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Landing·Dashboard·Guide component tests와 1440·390·320px Playwright, 시각 캡처가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (핵심 페이지 정보 구조·가이드)

- What was done:
  - 목록·상세·편집 page header variant와 자연스러운 한국어 문구를 적용하고 공고 등록·overview·analysis 화면을 자동 분석 흐름으로 재구성했다.
  - 가입 직후 흐름과 다시 볼 수 있는 5단계 `/guide`를 실제 제품 component preview로 구현했다.
- Key decisions:
  - guide dismiss 영속 상태를 새로 만들지 않고 언제든 진입 가능한 도움말 route로 제공한다.
- Issues encountered:
  - 분석 화면의 nav와 child CTA 중복 locator는 landmark scope로 구분했다.
- Validation:
  - Dashboard·JobAnalysis·Guide component tests, 전체 Frontend check와 30장 visual capture 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (공고 자동 판독·수동 fallback 안내)

- What was done:
  - Job overview의 자동 처리 중·자동 부족 안내와 CTA 회귀를 보정했다.
- Key decisions:
  - URL/사용자 입력 필드를 보존하고 깨진 자동 text를 표시하지 않는다.
- Issues encountered:
  - 기존 문구 assertion 2건을 새 사용자 메시지로 갱신했다.
- Validation:
  - job page component test와 Frontend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (자료·대외활동 화면 B2C 흐름)

- What was done:
  - 문서 목록/상세 정보 구조와 사용자 문구를 개선하고 직접 대외활동 등록·수정·삭제 화면을 추가했다.
  - 자료·공고·프로필·AI 내역 위험 작업을 공통 확인 모달로 옮기고 성공 toast를 연결했다.
- Key decisions:
  - `/profile/activities`를 canonical route로 두고 과거 evidence route는 redirect한다.
- Issues encountered:
  - 실제 브라우저 실패 화면에서 소재 요약이 `정리 중`으로 남아 상태 label을 직접 표시하도록 보정했다.
- Validation:
  - Frontend 전체 check와 Playwright desktop/mobile 흐름 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 세 화면·답변 409 UX)

- What was done:
  - Job Interview tab, `/interviews`, question set 상세에서 준비·coverage/source·질문·답변 version·feedback 흐름을 구현했다.
- Key decisions:
  - `LIMITED|NONE`은 경고가 있는 성공으로, provider 장애는 안전한 오류·retry로 구분한다.
  - 답변 충돌 취소는 server state로 동기화하고 재적용은 최신 parent와 최초 사용자 snapshot을 명시적으로 결합한다.
- Issues encountered:
  - None.
- Validation:
  - page tests와 actual E2E의 SUFFICIENT·409·feedback·responsive 흐름이 통과했다.
- Next steps:
  - P9 mock interview 화면은 구현하지 않는다.

## [2026-07-31] Session Summary (최종 학력·UI 문구와 hover 보정)

- What was done:
  - 기본 정보 닉네임 field와 수동 대표 학력 action을 제거하고 학력 단계·최종 학력 badge를 추가했다.
  - 승인·거절 안내 card, AI 작업 `선택`/`삭제(n)` 문구와 관심 공고 active hover를 보정했다.
- Key decisions:
  - 서버가 계산한 `isPrimary`만 최종 학력 badge로 표시한다.
- Issues encountered:
  - None.
- Validation:
  - page/layout targeted 24 tests와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (프로필 하단 저장·대외활동·작업 삭제)

- What was done:
  - 기본 정보 savebar를 모든 입력 뒤로 이동하고 대외활동 filter gap, 학력 상태 한국어 표시와 승인·confidence 안내를 적용했다.
  - legacy 응답에 EDUCATION source가 섞여도 대외활동 card 목록에서 렌더링하지 않는 client guard를 추가했다.
  - terminal Agent Run 개별 삭제, 현재 페이지 선택·전체 선택 및 선택 삭제 UI를 추가했다.
- Key decisions:
  - 직접 입력 근거는 이미 VERIFIED라 승인·거절을 숨기고 문서 AI 추출 근거에만 검토 action을 표시한다.
  - 작업 삭제 확인문은 실행 결과와 비용 audit이 보존됨을 알린다.
- Issues encountered:
  - 전체 check 중 수정 파일 4개와 마지막 학력 상태 mapping 1개의 Prettier 경고는 대상 파일 format 뒤 해소했다.
- Validation:
  - page targeted 13 tests와 Frontend 전체 53 files/215 tests·production build 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (프로필 기본 정보·필터 간격 보정)

- What was done:
  - Profile 기본 정보 저장 bar에 좌우 border·radius·16px padding을 적용하고 닉네임 입력을 기존 단일 저장 action에 통합했다.
  - Cover Letter 목록의 검색·상태·정렬·적용 control grid에 12px gap을 추가했다.
- Key decisions:
  - Profile 본문 저장 성공 뒤 nickname만 실패하면 부분 성공 alert를 보여 주고 nickname dirty 상태를 유지한다.
- Issues encountered:
  - 실행 중인 기존 Backend가 새 account endpoint 전 source여서 browser API 결합 대신 Frontend unit과 Backend integration을 각각 검증했다.
- Validation:
  - Page tests와 Frontend 53 files/214 tests, 1440×1000 save bar inset·filter gap 실측이 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 editor 409 비교·재적용 보강)

- What was done:
  - 문항 field, 전체 정렬, current answer content와 lifecycle 상태를 operation별 최신 server snapshot과 최초 사용자 snapshot으로 비교한다.
  - 질문·정렬·답변 충돌의 재적용과 취소를 각각 검증하고 actual E2E에 실제 문항 409를 추가했다.
- Key decisions:
  - 재적용은 사용자의 명시적 버튼 동작이며 refetch나 Vue Query mutation이 자동으로 overwrite하지 않는다.
- Issues encountered:
  - answer 취소 시 Vue Proxy를 직접 복제하지 않고 canonical plain document로 동기화하도록 보정했다.
- Validation:
  - page 대상 409 tests와 전체 Frontend 53 files/211 tests, P7 actual Chromium 1/1이 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 기다린다.

## [2026-07-30] Session Summary (P7 자기소개서 세 화면)

- What was done:
  - 전체 목록, 공고별 상태/생성 진입과 문항 navigator·TipTap·근거·검증·version drawer를 갖춘 canonical editor를 구현했다.
  - question CRUD/order, generation partial result/retry, 명시적 save/restore/verify, warning acknowledgement/finalize와 archive read-only를 연결했다.
- Key decisions:
  - 공고 tab에 editor를 복제하지 않고 archived 상세은 mutation을 비활성화하며 조건부 unarchive만 제공한다.
- Issues encountered:
  - 실제 question maxLength number input parser 오류와 mutation UI race를 보정했다.
- Validation:
  - page/component tests, P7 actual 전체 시나리오와 1440/390px overflow가 통과했다.
- Next steps:
  - 독립 validator 판정을 반영한다.

## [2026-07-29] Session Summary (P6 공고별 분석 결과 페이지)

- What was done:
  - 분석 없음·선행 조건·진행·WAITING_USER·실패·성공·OUTDATED·history 상태를 단일 Job Analysis page에 구현했다.
  - Eligibility, fit score 안내, requirement, strength/gap, verified evidence와 criterion breakdown을 분리해 표시했다.
  - historical evidence의 현재 상태가 바뀌어도 분석 당시 결과를 유지하고 재분석 제외 상태를 텍스트로 표시한다.
- Key decisions:
  - 프로필 미완료는 경고만 표시하고 usable 공고 분석을 차단하지 않으며 OUTDATED 기존 결과도 숨기지 않는다.
- Issues encountered:
  - 실제 E2E의 같은 버전 제목 locator와 비공개 evidence GET assertion을 공개 계약에 맞게 보정했다.
  - 1차 validator의 historical detail 거부 finding을 canonical current evidence 상태 수용과 OUTDATED 안내로 보정했다.
- Validation:
  - Job Analysis component, 1440px/390px Chromium·keyboard·overflow와 Frontend 전체 169 tests가 통과했다.
- Next steps:
  - 수정된 actual P6 E2E assertion은 재검증 상한으로 아직 실행하지 않았다.

## [2026-07-28] Session Summary (지원 현황 Dashboard·기본 프로필 단일 편집 구조)

- What was done:
  - Dashboard를 사용자 이름 기반 제목, 핵심 빠른 작업, 실제 집계, 상태 기반 다음 할 일, 마감 임박 공고, 최근 활동과 신규 사용자 전용 시작 안내로 재구현했다.
  - Profile 기본 정보 화면을 기본 정보·자기소개·희망 조건의 세 section과 단일 저장 action으로 재구성하고 dirty·saving·success·error·409 conflict 상태를 분리했다.
  - Dashboard의 기존 사용자, 신규 사용자 이름 fallback, 부분 query 오류를 검증하는 unit test를 추가하고 Profile 저장·field error 회귀 test를 갱신했다.
- Key decisions:
  - Dashboard 최근 목록은 현재 query가 반환한 항목만 표시하고, 전체 수치는 pagination의 `totalElements`만 사용한다.
  - 서버 field error는 해당 control 가까이에 유지하고 저장 완료 뒤 version baseline을 갱신하되 auto-save는 도입하지 않았다.
- Issues encountered:
  - Cover Letter와 Mock Interview는 현재 연결 가능한 Dashboard API가 없어 가짜 최근 활동이나 수치를 만들지 않았다.
- Validation:
  - 관련 Vitest 6 files/25 tests와 전체 40 files/154 tests, TypeScript, production build 통과.
  - 1440px·390px Dashboard와 Profile 스크린샷에서 overflow, CTA 우선순위와 상태 구분을 확인했다.
- Next steps:
  - 전용 Dashboard 계약 구현 뒤 자기소개서·모의 면접·검증 경고 집계를 연결한다.

## [2026-07-28] Session Summary (Dashboard·필터·기본 정보 화면 완성도 보정)

- What was done:
  - Dashboard hero 제목을 desktop 한 줄, 390px 의미 단위 두 줄로 조정하고 장식과 설명의 충돌을 제거했다.
  - 자료·관심 공고 filter control 사이의 여백을 늘리고 프로필 기본 정보의 공통 정보·희망 조건을 번호, eyebrow, surface와 section divider로 구분했다.
  - 노란 프로필 작성 안내를 브랜드 blue soft surface로 교체하고 희망 조건 입력군의 구조적 rail을 추가했다.
- Key decisions:
  - 기존 form ID, label, mutation, query, route와 오류·conflict 동작은 유지했다.
- Issues encountered:
  - 첫 mobile 캡처에서 제목이 세 줄이 되어 한 차례 typography를 보정했다.
- Validation:
  - 1574px·390px Dashboard, 1440px·390px 기본 정보, 1600px 자료·공고 필터를 직접 검수했고 390px 가로 넘침이 없었다.
  - Frontend 전체 check와 fixture UI shell 3/3이 통과했다.
- Next steps:
  - 실제 데이터가 필요한 cross-stack 시나리오는 이번 visual-only 보정 범위에서 재실행하지 않았다.

## [2026-07-28] Session Summary (현재 전체 Page 정보 구조·Form 재설계)

- What was done:
  - 인증·onboarding·dashboard·7개 profile·documents·jobs·분석 기록·404에 Hiresemble Blue control과 B2C action copy를 적용했다.
  - 자료 등록은 dropzone→분류→분석, 공고 등록은 URL 우선→직접 입력 disclosure, 목록 filter는 mobile 접기 흐름으로 재구성했다.
- Key decisions:
  - Document·Job 상태 축, 201/202, idempotency, 409, SSE, ID·test selector와 입력값 보존을 유지했다.
- Issues encountered:
  - 실제 Document E2E는 upload API 일반 오류로 첫 시나리오가 timeout되어 완료하지 못했다.
  - 실제 Profile E2E는 현재 온보딩 문구까지 동기화했지만 완료율 text·progressbar strict locator 중복에서 중단됐다.
- Validation:
  - page component test, 전체 149 tests, fixture Playwright 5/5와 18개 화면 네 viewport 시각 검수가 통과했다.
- Next steps:
  - Profile 완료율 locator를 명시적으로 한정하고 실행 Backend 설정을 갱신한 뒤 actual pipeline을 재검증한다.

## [2026-07-28] Session Summary (프로필·자료 등록 화면 전문 서비스화)

- What was done:
  - 프로필을 지원 방향 brief와 단계형 form으로, 자료 등록을 파일 선택·분류·분석 안내 흐름으로 재구성했다.
  - 닉네임, 분석 기록과 졸업(예정)일을 전체 현재 route의 사용자 언어로 통일했다.
- Key decisions:
  - 기존 DOM ID·API mutation·route와 자유 입력 기능을 유지하고 정보 계층과 반응형 표현만 강화했다.
- Issues encountered:
  - 모바일에서는 sticky guide를 일반 흐름으로 바꾸고 file card action을 wrap해 overflow를 제거했다.
- Validation:
  - Page tests, Frontend 전체 145 tests와 390px Playwright 검증이 통과했다.
- Next steps:
  - 실제 장문 경력·파일명 데이터로 시각 밀도를 추가 확인한다.

## [2026-07-28] Session Summary (현재 Route B2C UX Writing 전면 적용)

- What was done:
  - 18개 사용자 route와 404의 제목, 설명, CTA, helper, loading·empty·error·success·conflict 문구를 사용자 결과와 다음 행동 중심으로 다시 작성했다.
  - Dashboard를 가상 KPI 없이 네 가지 실제 작업을 잇는 numbered path로 재구성했다.
  - 경험 정보의 원시 JSON 입력을 타입 보존 항목형 편집기로 바꾸고 자료 목록·상세에서 MIME type을 숨겼다.
- Key decisions:
  - `근거`는 문맥에 따라 경험 정보·자료에서 찾은 정보로 바꾸고 대표 학력은 `먼저 보여 줄 학력`으로 설명한다.
- Issues encountered:
  - 일부 성공·충돌 문장에 남은 `-습니다`형을 루트 시각·문구 감사에서 찾아 `-해요/-해 주세요`형으로 통일했다.
- Validation:
  - page component test와 18개 보호 route+404의 1440·390px 직접 진입·overflow smoke가 통과했다.
- Next steps:
  - Backend가 필요한 실제 데이터 밀도와 긴 파일명·공고명 검수는 actual E2E 환경에서 수행한다.

## [2026-07-27] Session Summary (현재 Route Page 정보 구조 개선)

- What was done:
  - 인증, onboarding, dashboard, 7개 profile route, Documents, Jobs, Agent Run과 404의 typography·form·action·state hierarchy를 개선했다.
  - Dashboard 개발 문구를 제거하고 실제 route 빠른 작업만 제공했으며 onboarding 마지막 단계는 구현된 문서 업로드 또는 추후 입력만 제공한다.
- Key decisions:
  - 가상 집계·최근 활동·미구현 analysis/cover-letter/interview/settings 화면과 API는 추가하지 않았다.
  - 기존 form ID, `data-testid`, accessible name, 상태별 CTA와 mutation/query 흐름을 보존했다.
- Issues encountered:
  - 구조화 profile의 반복 form은 하나의 generic page 안에 있어 동작을 분할하지 않고 공통 scoped style로 시각 일관성만 맞췄다.
- Validation:
  - 기존 literal DOM ID와 `data-testid` 누락 0건, page component와 전체 128 tests가 통과했다.
- Next steps:
  - cross-stack 환경에서 긴 실제 문서명·URL·공고 본문 조합의 수동 시각 검수를 보강한다.

## [2026-07-27] Session Summary (P5 Job 목록·등록·overview Page 구현)

- What was done:
  - 상태 tab·filter·pagination 목록, 201/202 생성과 편집·상태·retry·manual·delete 상세를 구현했다.
- Key decisions:
  - 업무/추출 badge를 분리하고 submittedAt 이력이 있는 CLOSED 공고를 표시한다.
- Issues encountered:
  - NEEDS_MANUAL_INPUT retry를 제거하고 수동 입력만 강조하도록 validator 보정했다.
- Validation:
  - page component test와 실제 Chromium Job E2E 5/5가 통과했다.
- Next steps:
  - P6 전까지 분석 버튼·가짜 page를 추가하지 않는다.

## [2026-07-19] Session Summary (P4 Document 목록·상세 Page 구현)

- What was done:
  - upload·filter·pagination·sort 목록과 metadata·text·manual·reparse·download·delete·evidence 상세를 구현했다.
- Key decisions:
  - `PARSED + evidence FAILED`는 업로드 실패가 아니라 text preview를 유지하는 partial success로 표시한다.
- Issues encountered:
  - None.
- Validation:
  - page component tests와 실제 성공·manual·failure·isolation Browser 시나리오가 통과했다.
- Next steps:
  - Dashboard와 P5 이후 pages는 미착수다.

## [2026-07-19] Session Summary (P3 Agent Run 목록·상세 Page 구현)

- What was done:
  - workflow/status/retryable filter, pagination·sort 목록과 URL canonicalization을 구현했다.
  - REST detail 뒤 SSE controller를 연결하고 retry successor 이동과 cancel CAS를 조정했다.

- Key decisions:
  - WAITING_USER action·FAILED retry·active cancel은 server boolean과 상태를 함께 사용한다.

- Issues encountered:
  - None.

- Validation:
  - list page·detail panel component와 browser fixture가 통과했다.

- Next steps:
  - Dashboard 집계나 AI 설정 page는 P10까지 추가하지 않는다.

## [2026-07-19] Session Summary (P2 프로필·온보딩·evidence Page 구현)

- What was done:
  - 기본 프로필, 다섯 구조화 resource, evidence 목록·편집·검토와 4단계 onboarding을 구현했다.
  - 완료·부족 항목, 대표 학력, timeline/list, pagination·sort, 삭제 확인과 409 재적용 UI를 연결했다.

- Key decisions:
  - `SOURCE_DELETED`는 read-only로 렌더링하되 P2 data에서는 생성하지 않는다.
  - document 연결·filter는 후속 단계 안내만 표시하고 입력 control을 활성화하지 않는다.

- Issues encountered:
  - onboarding fetch 오류가 성공 단계로 진행되지 않도록 실패 상태를 테스트로 보정했다.

- Validation:
  - page component·onboarding flow와 frontend 전체 check, 실제 Chromium E2E가 통과했다.

- Next steps:
  - Dashboard는 P10 전까지 shell로 유지하고 document 업로드는 P4에서 구현한다.

## [2026-07-19] Session Summary (P1 인증 Page와 shell 구현)

- What was done:
  - signup/login Form, onboarding/dashboard shell, root 대기와 404 page를 구현했다.

- Key decisions:
  - signup은 항상 onboarding, login은 검증된 returnTo 또는 dashboard로 이동한다.

- Issues encountered:
  - server field 오류 시 disabled input에 focus할 수 없는 접근성 결함을 test로 발견해 제출 상태 해제 후 focus하도록 수정했다.

- Validation:
  - authFlow component test와 route shell·404 test, Frontend check가 통과했다.

- Next steps:
  - P2에서 onboarding 실제 Form과 API를 별도 범위로 구현한다.
