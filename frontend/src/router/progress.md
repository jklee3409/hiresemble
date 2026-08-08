# Progress

## Overview

- `index.ts`가 `createWebHistory(import.meta.env.BASE_URL)`로 router를 생성한다.
- anonymous `/` 공개 Landing, 인증, onboarding, `/guide`, dashboard, profile, feature-gated GitHub와 lazy Agent Run·Document·Job·Cover Letter·Interview route 및 전용 404가 구현되어 있다.
- `returnTo.ts`가 same-origin 등록 보호 path와 UUID Agent Run·Document·Job·Cover Letter·Interview detail child를 허용하고 GitHub path는 flag 활성 시만 허용한다.
- 새 route 진입은 상단으로 이동하고 browser history의 저장 위치는 복원한다.

## [2026-08-08] Session Summary (GitHub Source route gate)

- What was done:
  - `/profile/github` lazy route를 build-time flag로 조건부 등록하고 `returnTo` allowlist에 같은 조건을 적용했다.
- Key decisions:
  - 비활성 build에서는 route record 자체가 없으며 AppLayout 최상위 menu는 추가하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - router·returnTo flag on/off test와 전체 `corepack pnpm check` 통과.
- Next steps:
  - None.

## [2026-08-07] Session Summary (경험 보관함 보호 route)

- What was done:
  - `/profile/experiences` lazy route·title·profileRecommended metadata와 안전한 `returnTo` 허용을 추가했다.
- Key decisions:
  - 기존 `/profile/evidence` redirect는 직접 대외활동 호환 경로로 유지하고 새 canonical 경험 route를 별도로 등록했다.
- Issues encountered:
  - None.
- Validation:
  - router·returnTo·profile navigation test와 전체 `pnpm check` 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (온보딩 eligibility route fixture 보강)

- What was done:
  - 보호 route shell 테스트의 Onboarding mock에 eligibility 조회 응답을 추가했다.
- Key decisions:
  - route 정책은 바꾸지 않고 화면의 실제 초기 조회 계약만 fixture에 반영했다.
- Issues encountered:
  - 전체 check에서 누락 mock을 재현한 뒤 단독 router test로 먼저 확인했다.
- Validation:
  - Router Vitest 7건과 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard route fixture 계약 갱신)

- What was done:
  - 보호 shell 회귀에 새 Dashboard·Career Guide API fixture와 자연스러운 대시보드 문구 assertion을 반영했다.
- Key decisions:
  - route 접근 정책은 변경하지 않고 화면 데이터 source만 현재 공개 계약과 맞췄다.
- Issues encountered:
  - 전체 check에서 과거 Dashboard 문구 assertion이 실패해 대상 test로 재현·보정했다.
- Validation:
  - Router 7 tests와 Frontend 전체 `pnpm check` 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공개 home·공통 route title 정책)

- What was done:
  - home component를 Landing으로 바꾸고 anonymous navigation을 허용하며 authenticated session은 bootstrap 뒤 dashboard로 replace했다.
  - AppLayout의 title side effect를 Router `afterEach`로 옮겨 Landing·login·signup·보호 화면·404에 공통 적용했다.
- Key decisions:
  - 기존 auth bootstrap, publicOnly, requiresAuth, safe returnTo와 401/logout 보호 route 이탈 계약을 유지했다.
- Issues encountered:
  - None.
- Validation:
  - Router component test와 authenticated Landing flash 감시·anonymous returnTo Playwright가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (다시 볼 수 있는 이용 가이드 route)

- What was done:
  - 인증 보호 lazy `/guide` route, title과 route test를 추가했다.
- Key decisions:
  - onboarding 완료 여부나 dismiss 상태를 localStorage에 만들지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Router unit tests와 desktop/mobile guide navigation 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (대외활동 canonical route)

- What was done:
  - `/profile/activities` lazy route와 title/allowlist/returnTo를 추가하고 `/profile/evidence`를 redirect했다.
- Key decisions:
  - 북마크 호환성을 유지하되 직접 활동 화면을 canonical URL로 사용한다.
- Issues encountered:
  - None.
- Validation:
  - Router tests와 실제 route 진입·모바일 navigation 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 route·title·active state)

- What was done:
  - `/jobs/:jobId/interview`, `/interviews`, `/interview-question-sets/:questionSetId` lazy route와 사용자용 title을 추가했다.
- Key decisions:
  - P9 route는 추가하지 않고 safe returnTo·404·scrollBehavior 계약을 유지한다.
- Issues encountered:
  - None.
- Validation:
  - router/navigation tests와 P8 actual route 진입이 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (대외활동 route meta)

- What was done:
  - `/profile/evidence`의 shell/header meta title을 `경험 정보`에서 `대외활동`으로 바꾸고 route test를 추가했다.
- Key decisions:
  - route path·name·guard·returnTo는 유지해 deep link 호환성을 보존한다.
- Issues encountered:
  - None.
- Validation:
  - Router/profile targeted 16 tests와 Frontend 전체 53 files/215 tests·production build 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (첫 route 진입 scroll 기준)

- What was done:
  - Router `scrollBehavior`에 saved position 복원과 새 navigation `top: 0` 기준을 추가했다.
- Key decisions:
  - Browser back/forward는 기존 위치를 보존하고 새 profile 진입만 상단에서 시작한다.
- Issues encountered:
  - jsdom은 `window.scrollTo` 미구현 안내를 출력하지만 test failure 없이 browser 동작은 정상이다.
- Validation:
  - Frontend 전체 check와 Playwright CLI profile 첫 진입 `scrollY=0` 검수가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 자기소개서 route 3개)

- What was done:
  - `/jobs/:jobId/cover-letter`, `/cover-letters`, `/cover-letters/:coverLetterId/edit` lazy route·meta와 returnTo allowlist를 추가했다.
- Key decisions:
  - 공고 child는 context page, 별도 edit route는 canonical editor이며 P8 route를 추가하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - router/returnTo tests와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 공고 분석 child route)

- What was done:
  - `/jobs/:jobId/analysis` lazy child와 title, returnTo 허용 경계를 추가하고 `/jobs/:jobId` canonical overview redirect를 유지했다.
- Key decisions:
  - P7/P8 route·disabled tab은 만들지 않고 Agent Run route path/name도 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - router/returnTo unit test와 Frontend 전체 169 tests가 통과했다.
- Next steps:
  - P7 route는 P7 공개 계약 구현 뒤 추가한다.

## [2026-07-28] Session Summary (지원 홈 Route Meta 통일)

- What was done:
  - Dashboard route meta와 404·Profile section action의 복귀 문구를 `지원 홈`으로 통일했다.
- Key decisions:
  - `/dashboard` path, route name, guard, redirect와 returnTo 계약은 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - Router unit test와 Dashboard·Not Found UI fixture가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (내 지원 정보 Meta·Deep Link 보존)

- What was done:
  - profile basic meta를 `내 지원 정보`로 맞추고 workspace navigation이 기존 7개 route를 그대로 사용하도록 검증했다.
- Key decisions:
  - path·route name·guard·redirect·returnTo는 변경하지 않고 미구현 route를 추가하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - route table 전수 비교와 router test, mobile selector deep link가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (분석 기록 route meta 적용)

- What was done:
  - Agent Run 목록·상세의 사용자 노출 meta title을 분석 기록·분석 상세로 변경했다.
- Key decisions:
  - route path·name·guard와 lazy import는 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - Router tests와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (Route Meta 소비자 문구 정리)

- What was done:
  - route path·name·guard·redirect는 그대로 두고 header에 노출되는 meta title만 `오늘의 준비`, `경험 정보`, `이력서·자료`, `관심 공고`, `AI 작업`으로 정리했다.
- Key decisions:
  - route 범위는 24개 path record로 변경 전후 동일하며 P6 이후 path를 추가하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - before/after path 추출 비교가 24/24로 동일하고 router·returnTo test가 통과했다.
- Next steps:
  - 공개 URL 변경은 기능 구현과 별도 호환성 검토 없이는 수행하지 않는다.

## [2026-07-27] Session Summary (현재 Route 범위 UI 회귀 고정)

- What was done:
  - route table은 변경하지 않고 router test에서 제품 dashboard 빠른 작업과 인증 상태별 404 복구 동선을 검증했다.
- Key decisions:
  - 현재 18개 사용자 route와 전용 404만 유지하고 P6 이후 path, placeholder와 disabled route를 추가하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - router/returnTo 기존 계약과 전체 Vitest가 통과하고 `index.ts` diff가 없음을 확인했다.
- Next steps:
  - 향후 route는 대응 API·page가 구현될 때 명세와 함께 추가한다.

## [2026-07-27] Session Summary (P5 Job lazy route 추가)

- What was done:
  - `/jobs`, `/jobs/new`, `/jobs/:jobId` redirect와 `/jobs/:jobId/overview` lazy route를 추가했다.
- Key decisions:
  - 향후 child route를 수용하는 layout만 만들고 P6 analysis route는 등록하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - route guard·returnTo·redirect unit test와 P5 Browser E2E가 통과했다.
- Next steps:
  - P6 분석 구현 시 overview sibling child route를 추가한다.

## [2026-07-19] Session Summary (P4 Document lazy route 추가)

- What was done:
  - `/documents`와 `/documents/:documentId` lazy route 및 safe returnTo를 추가했다.
- Key decisions:
  - Document route는 main bundle과 분리한다.
- Issues encountered:
  - None.
- Validation:
  - route import·guard·404·returnTo 테스트와 bundle build가 통과했다.
- Next steps:
  - 미구현 P5 path를 allowlist에 추가하지 않는다.

## [2026-07-19] Session Summary (P3 Agent Run lazy route·returnTo 추가)

- What was done:
  - `/agent-runs`, `/agent-runs/:agentRunId`를 dynamic import로 추가했다.
  - 두 보호 path의 안전한 login returnTo를 허용했다.

- Key decisions:
  - Agent Run feature/page는 initial bundle에 static import하지 않는다.

- Issues encountered:
  - None.

- Validation:
  - lazy component function과 P1·P2 route·guard 회귀 테스트가 통과했다.

- Next steps:
  - P4 route는 해당 page 구현과 함께 추가한다.

## [2026-07-19] Session Summary (P2 profile route·returnTo 확장)

- What was done:
  - `/profile`→`/profile/basic`과 basic·education·certifications·languages·awards·careers·evidence route를 추가했다.
  - profile path를 안전한 returnTo allowlist와 guard·404 회귀 테스트에 연결했다.

- Key decisions:
  - 기존 AppLayout·auth-required guard를 재사용하고 onboarding 완료 여부로 route를 차단하지 않는다.

- Issues encountered:
  - None

- Validation:
  - redirect, 보호 route, 401, safe returnTo와 404 router 테스트가 통과했다.

- Next steps:
  - 후속 route는 실제 page·API가 함께 구현될 때만 등록한다.

## [2026-07-19] Session Summary (P1 인증 route·guard 구현)

- What was done:
  - root 인증 분기, signup/login public-only, onboarding/dashboard auth-required와 catch-all 404 route를 등록했다.
  - auth bootstrap을 기다리는 전역 guard와 로그인 성공·기인증 접근·Session 만료의 안전한 `returnTo` 처리를 구현했다.
  - route table, guard, 안전한 returnTo와 page 흐름 테스트를 추가했다.

- Key decisions:
  - 허용 목적지는 등록된 auth-required path만이며 scheme·host·`//`·backslash·CR/LF·public route를 거부한다.
  - `/`는 anonymous를 `/login`, authenticated를 `/dashboard`로 보내고 signup 성공은 `/onboarding`으로 이동한다.
  - 클라이언트 guard는 UX 경계이며 서버 인증·인가를 대체하지 않는다.

- Issues encountered:
  - logout/401 뒤 현재 보호 route가 그대로 렌더링되지 않도록 store subscription과 router navigation을 연결했다.
  - browser query는 Vue Router가 한 번 decode하므로 helper에 이중 인코딩 허용을 추가하지 않았다.

- Validation:
  - router·returnTo·auth flow 관련 테스트와 전체 `corepack pnpm check`가 통과했다.
  - `/dashboard` 보호, `/onboarding` shell, public-only redirect, 잘못된 returnTo 거부와 404를 검증했다.

- Next steps:
  - P2 실제 profile/dashboard page 구현 시 현재 shell component만 교체하고 guard 계약은 유지한다.
  - 실제 browser Cookie·history smoke test는 cross-stack 실행 환경에서 보강한다.

## [2026-07-17] Session Summary (Vue Router 기본 구성)

- What was done:
  - 당시 구현 상태:
    - `index.ts`가 `createWebHistory(import.meta.env.BASE_URL)`로 router를 생성한다.
    - `routes` 배열은 비어 있고 route metadata, navigation guard, page import는 없다.
    - `main.ts`의 plugin 등록과 `App.vue`의 `RouterView` 연결만 완료되어 있다.
  - 완료된 작업:
    - Vue Router instance와 browser history 기본 구성을 추가했다.
    - Router를 애플리케이션 bootstrap과 root outlet에 연결했다.
    - 작업 목적에 따라 `index.md`와 이 문서를 생성해 현재 빈 route 상태와 향후 guard 책임을 기록했다.
  - 당시 진행 중인 작업:
    - 현재 추가 중인 route 또는 navigation guard는 없다.
    - Router 초기 문서 계층 구성은 이번 작업에서 완료했다.

- Key decisions:
  - Vite base URL과 `createWebHistory` 기반 구성을 유지한다.
  - route guard 정책을 public-only, auth-required, profile-recommended로 분리하고 서버 인가를 클라이언트 guard로 대체하지 않는다.
  - 실제 page가 추가될 때 lazy import와 명시적인 route metadata를 함께 도입한다.

- Issues encountered:
  - 등록된 route가 없어 URL에 대응하는 화면이 렌더링되지 않는다.
  - 인증 상태 조회와 guard 복구 흐름이 아직 설계·구현되지 않았다.
  - route test가 없어 browser history와 navigation 정책을 검증할 수 없다.

- Validation:
  - 기본 검증 명령: `Set-Location frontend; corepack pnpm check`
  - 향후 navigation 검증 명령: `Set-Location frontend; corepack pnpm test:unit` 및 route E2E 대상 `corepack pnpm test:e2e`
  - 기본 검증은 성공했다. Route test와 E2E test는 파일이 없어 실행하지 않았으므로 navigation 동작은 미검증 상태다.

- Next steps:
  - 페이지 명세에 정의된 public, onboarding, app 내부 route 등록
  - public-only, auth-required, profile-recommended metadata와 guard 구현
  - not-found와 접근 실패·세션 만료 navigation UX 정의
  - route 및 guard unit/component test와 핵심 navigation E2E test 추가
