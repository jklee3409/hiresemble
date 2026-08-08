# Progress

## Overview

- `main.ts`가 Vue 앱과 Pinia, Router, Vue Query를 초기화하며 미사용 PrimeVue theme은 등록하지 않는다.
- `env.d.ts`에 Vite API base/proxy와 서로 독립적인 GitHub Source·Career Artifact feature flag 환경 변수 타입이 선언되어 있다.
- P1 auth·Session부터 P8 Interview와 Gate 4 `features/career-artifacts`, API·page·lazy route 구현·테스트가 있다.
- 공개 Landing과 실제 인증·onboarding·dashboard·profile·GitHub Source·Career Artifact·Agent Run·Document·Job·Cover Letter·Interview 화면 및 공용 제품 UI 기반이 있으며 Dashboard 전용 집계·게시 가이드 read가 연결되어 있고 AI 설정 기능은 아직 없다.

## [2026-08-08] Session Summary (Career Artifact Gate 4 소스 통합)

- What was done:
  - Career Artifact flag·route·page·feature·strict API를 조립하고 기존 layout, Agent Run, Document, GitHub, session cleanup에 필요한 연결을 추가했다.
- Key decisions:
  - 업로드 document와 생성 artifact의 route·cache·lifecycle을 합치지 않고 flag가 꺼지면 Career Artifact surface와 요청을 모두 제거한다.
- Issues encountered:
  - 상세 lifecycle refetch race와 browser fixture의 historical SSE replay를 실제 상태가 역행하지 않도록 보정했다.
- Validation:
  - 전체 Frontend check 94 files/422 tests와 production build, 통합 Chromium 4/4가 통과했다.
- Next steps:
  - Gate 5 private repository 연결은 현재 소스 범위에 추가하지 않는다.

## [2026-08-08] Session Summary (Career Artifact Agent Run 호환성)

- What was done:
  - 기존 Agent Run parser와 presentation에 두 generation workflow와 16개 step label을 additive하게 추가했다.
- Key decisions:
  - Career Artifact page·API client·route는 만들지 않았다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 전체 check 80 files/373 tests와 build 통과.
- Next steps:
  - Gate 4가 전용 UI를 소유한다.

## [2026-08-08] Session Summary (GitHub Source 소스 통합)

- What was done:
  - app flag, shared API, GitHub feature, profile/page/router와 기존 Agent Run·experience source 표시를 Gate 2 계약으로 통합했다.
- Key decisions:
  - route와 링크 허용 여부는 한 typed build-time flag를 공유하고 GitHub source server state는 Vue Query에만 둔다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 전체 `corepack pnpm check` 80 files/369 tests와 build 통과.
- Next steps:
  - focused Playwright의 수정된 delete dialog locator 재확인이 남았다.

## [2026-08-02] Session Summary (Dashboard source·layout·API 연결)

- What was done:
  - Dashboard page, AppLayout 폭·focus, typed API client와 router fixture를 현재 Dashboard/Career Guide 계약에 연결했다.
- Key decisions:
  - 전역 앱 폭은 유지하고 Dashboard route에만 넓은 canvas를 적용한다.
- Issues encountered:
  - 기존 router 회귀의 과거 Dashboard fixture를 새 projection으로 교체했다.
- Validation:
  - Frontend `pnpm check` 67 files/264 tests와 Playwright UI shell 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공개 진입과 제품 첫 사용 Source)

- What was done:
  - Landing page·test, auth-aware home route, 공통 journey 정의와 Dashboard 부분 완료 체크리스트 source를 추가했다.
- Key decisions:
  - 공개 설명과 보호 제품 action의 책임을 route/page 경계에서 분리하고 새 store·dependency를 만들지 않았다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 전체 65 files/258 tests와 production build, Chromium Landing·UI shell 회귀가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Cover Letter Vue source)

- What was done:
  - typed API, cover-letter feature, 세 lazy route/page, App/Job layout navigation과 actual E2E를 추가했다.
- Key decisions:
  - 서버 상태는 Vue Query, 브라우저 입력 중 본문만 sessionStorage로 관리하고 Pinia/localStorage에는 저장하지 않는다.
- Issues encountered:
  - number input runtime 타입을 string으로 가정한 parser를 string/number 공용으로 보정했다.
- Validation:
  - Frontend 53 files/204 tests, lint·format·typecheck·build와 P7 actual Chromium 1/1이 통과했다.
- Next steps:
  - 독립 validator 판정을 반영한다.

## [2026-07-28] Session Summary (Hiresemble Blue UI와 통합 프로필 Source 적용)

- What was done:
  - 전역 control token, Career Profile Workspace navigation, mobile filter disclosure, 첫 invalid focus와 사용자 행동 중심 CTA를 현재 Vue source 전반에 적용했다.
  - 분석 상세에서 model·quality tier·비용 예약값을 숨기고 진행·시간·예상 비용만 표시했다.
- Key decisions:
  - shared API·store·query·SSE source는 수정하지 않고 page·layout·presentation 책임 안에서 변경했다.
- Issues encountered:
  - profile navigation 도입으로 기존 generic select test가 mobile selector를 선택해 의미가 분명한 selector로 보정했다.
- Validation:
  - Vue typecheck, 39 test files/149 tests, production build와 fixture browser 5 tests가 통과했다.
- Next steps:
  - 실제 Backend actual test 실패 원인은 Frontend source 범위 밖 실행 환경에서 재확인한다.

## [2026-07-28] Session Summary (현재 Vue 화면 B2C 언어·브랜드 적용)

- What was done:
  - 현재 route의 Vue template, route meta, validation·error·status presentation과 aria 문구를 취업 준비생 중심의 자연스러운 한국어로 정리했다.
  - 전역 token과 shell, page·feature 표현에 BrandMark와 motion system을 연결했다.
- Key decisions:
  - query·mutation·SSE·cleanup 구현은 유지하고 소비자에게 보이는 문자열과 시각 계층만 변경한다.
- Issues encountered:
  - 공용 copy 변경 뒤 기존 테스트의 사용자 의미를 동일한 새 문구로 갱신했으며 assertion 범위는 약화하지 않았다.
- Validation:
  - 전체 Frontend check와 fixture Playwright가 통과했고 기술 용어 노출·미구현 route 추가 여부를 정적 검사했다.
- Next steps:
  - 새 기능은 대응 API와 route가 실제 구현될 때 같은 용어·motion 체계를 확장한다.

## [2026-07-27] Session Summary (현재 Vue source 디자인 시스템 적용)

- What was done:
  - `styles`, `shared/ui`, layout, page와 현재 feature presentation에 token·공용 상태·responsive 구조를 적용했다.
  - 미사용 PrimeVue plugin bootstrap을 제거해 브라우저 라이선스 배지와 초기 bundle 포함을 해소했다.
- Key decisions:
  - API/query/store/router table은 그대로 두고 SFC의 정보 계층·markup·style과 접근성 연결만 변경했다.
  - 설치 dependency는 변경하지 않고 실제 component 사용이 생길 때 정식 설정과 함께 초기화한다.
- Issues encountered:
  - 초기 화면 전반에 분산된 utility 조합이 많아 공통 책임과 page 전용 배치를 구분해 단계적으로 정리했다.
- Validation:
  - lint·format·typecheck, Vitest 35 files/128 tests와 342 modules production build가 통과했다.
- Next steps:
  - 미래 feature source와 route는 해당 API 계약이 구현될 때 별도 추가한다.

## [2026-07-27] Session Summary (P5 Job Vue source 추가)

- What was done:
  - Job shared API·feature·lazy pages·routes와 AppLayout navigation을 추가했다.
- Key decisions:
  - server state는 Vue Query, stream 수명주기는 기존 Agent Run feature를 재사용한다.
- Issues encountered:
  - P6 DTO와 NEEDS_MANUAL_INPUT retry를 validator 보정에서 제거했다.
- Validation:
  - Frontend 32 files/122 tests·production build와 실제 P5 Chromium 5/5가 통과했다.
- Next steps:
  - P6 이후 source directory를 선행 생성하지 않는다.

## [2026-07-19] Session Summary (P4 Document Vue source 추가)

- What was done:
  - document shared API·feature·lazy pages·routes와 P2 evidence document selector를 추가했다.
- Key decisions:
  - server state는 Vue Query, stream 수명주기는 기존 Agent Run feature를 재사용한다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 95 tests·production build와 실제 Backend Playwright 4/4가 통과했다.
- Next steps:
  - P5 이후 source directory를 선행 생성하지 않는다.

## [2026-07-19] Session Summary (P3 Agent Run Vue source 추가)

- What was done:
  - shared API, agent-runs feature, lazy pages·routes와 AppLayout drawer를 추가했다.

- Key decisions:
  - 새 page와 drawer를 dynamic import해 기존 경고가 있는 initial bundle의 정적 의존을 막는다.

- Issues encountered:
  - None.

- Validation:
  - lint·Prettier·typecheck·78 tests·production build가 통과했다.

- Next steps:
  - P4 이후 기능은 실제 Backend 계약이 고정된 뒤 추가한다.

## [2026-07-19] Session Summary (P2 프로필 Vue 애플리케이션 구현)

- What was done:
  - 사용자별 profile query, typed API, Zod, 기본·구조화·evidence page와 P2 onboarding·route를 구현했다.
  - 409 비교·field 재적용, loading·empty·error·success·disabled 상태와 logout/401 cache cleanup을 연결했다.

- Key decisions:
  - 모든 profile query key에 user ID를 포함하고 서버 상태는 Vue Query, form draft는 local state로 유지한다.
  - profile 미완료는 표시·권고만 하고 보호 route를 차단하지 않는다.

- Issues encountered:
  - 실제 E2E의 성공 메시지와 카드 제목이 같은 텍스트를 포함해 role·heading locator로 판정식을 좁혔다.

- Validation:
  - 13개 파일 57개 Vitest와 typecheck·lint·format·production build가 통과했다.
  - 실제 Chromium P2 두 사용자 Cookie·CSRF·cache 격리 흐름 1개가 통과했다.
  - 최종 read-only validator가 API/DB/TypeScript/Zod parity와 E2E 근거를 `PASS`로 판정했다.

- Next steps:
  - P2는 완료 상태이며 P4 전까지 document UI를 비활성으로 유지한다.

## [2026-07-19] Session Summary (P1 Vue 인증 애플리케이션 구현)

- What was done:
  - 전역 Pinia·QueryClient bootstrap을 모듈화하고 auth store와 session cleanup port를 연결했다.
  - typed 계약·오류·Axios auth client, signup/login 검증·Form, layout·page·router를 구현했다.
  - store, client, validation, cleanup, route와 component 흐름 테스트를 추가했다.

- Key decisions:
  - auth 상태를 `unknown`, `authenticated`, `anonymous`로 명시하고 최초 guard가 `/auth/me` bootstrap 완료를 기다린다.
  - 현재 사용자 전환 시 이전 query cache와 draft namespace를 지워 사용자 간 상태를 격리한다.
  - `App.vue`는 기존처럼 root RouterView만 유지하고 layout은 route component에서 선택한다.

- Issues encountered:
  - async submit 중 접근성 focus 순서를 component test로 보정했다.
  - Session 만료 시 store reset뿐 아니라 현재 보호 URL을 안전한 `returnTo`로 보존하는 router 연동이 필요했다.

- Validation:
  - `corepack pnpm check`가 lint, format, type check, 7개 파일 35개 test와 production build를 모두 통과했다.
  - 두 사용자 인증 상태·cache 분리, logout cleanup과 typed field error 표시를 자동 테스트로 확인했다.

- Next steps:
  - P2 기능은 backend OpenAPI가 고정된 뒤 실제 feature 단위로 추가한다.
  - EventSource 구현이 생기면 기존 cleanup port에 연결하고 사용자 namespace별 draft 저장을 도입한다.

## [2026-07-17] Session Summary (Vue 애플리케이션 부트스트랩 구성)

- What was done:
  - 당시 구현 상태:
    - `main.ts`가 Vue 앱과 Pinia, Router, Vue Query, PrimeVue를 초기화한다.
    - `App.vue`는 `RouterView`만 제공하고 route 목록은 비어 있어 표시할 제품 화면이 없다.
    - `env.d.ts`에 Vite API base/proxy 환경 변수 타입이 선언되어 있다.
    - 도메인 page, component, API client, query, store와 프론트엔드 테스트는 아직 없다.
  - 완료된 작업:
    - Vue 애플리케이션 진입점과 `#app` mount를 구성했다.
    - PrimeVue Aura theme, TanStack Vue Query client, Pinia와 Vue Router를 전역 plugin으로 등록했다.
    - Tailwind 전역 style과 Vite 환경 변수 타입을 연결했다.
    - 작업 목적에 따라 `src/index.md`와 이 문서를 생성해 최소 shell과 미구현 영역을 구분했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 page, component 또는 API 연동 구현은 없다.
    - 소스와 하위 router/style 영역의 초기 문서 추적 기반은 구성됐다.

- Key decisions:
  - `App.vue`는 route outlet 중심의 최소 root로 유지하고 page 기능을 넣지 않는다.
  - 전역 plugin 조립은 `main.ts`에 집중시키되 도메인 상태와 규칙은 각 기능 영역에 둔다.
  - 실제 기능 요구가 생길 때 page/component/api/query/store 경계를 추가하고 빈 구조를 선행 생성하지 않는다.

- Issues encountered:
  - 빈 route 배열 때문에 `RouterView`가 렌더링할 component가 없고 현재 앱은 실질적인 UI를 제공하지 않는다.
  - unit/component test가 없어 현재 bootstrap 이후 동작을 자동으로 검증하지 못한다.
  - 명세에 많은 화면이 정의되어 있지만 소스 구현 상태와 동일하지 않다.

- Validation:
  - 기본 검증 명령: `Set-Location frontend; corepack pnpm check`
  - 이 명령은 ESLint, Markdown을 포함한 Prettier, TypeScript, Vitest와 production build를 실행한다.
  - 명령은 성공했으며 207개 module을 변환해 production build를 생성했다. Vitest는 test file이 없어 `--passWithNoTests`로 종료 코드 0을 반환했다.

- Next steps:
  - Public/App layout과 도메인별 route page 구현
  - typed API DTO/client, 오류 정규화, query/mutation과 최소 Pinia store 구현
  - 공용 UI component와 loading/empty/error/success 상태 구현
  - 순수 logic, form, component interaction 테스트 추가
