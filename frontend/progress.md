# Progress

## Overview

- Vue 3, TypeScript, Vite, pnpm 기반 개발 환경과 주요 plugin이 구성되어 있다.
- P1 auth부터 P5 Job typed client·Vue Query·SSE 복구까지 구현되어 있다.
- `/agent-runs`, `/documents`와 `/jobs` 목록·등록·overview는 lazy route이며 responsive AppLayout에는 Progress Drawer가 연결되어 있다.
- Vitest 39 files/149 tests, UI shell 3개·Agent Run fixture 2개, profile E2E 1개, 실제 Document E2E 4개와 Job E2E 5개가 있다. Dashboard 집계·P6 분석·AI 설정은 아직 없다.

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
