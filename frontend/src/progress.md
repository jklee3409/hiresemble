# Progress

## Overview

- `main.ts`가 Vue 앱과 Pinia, Router, Vue Query, PrimeVue를 초기화한다.
- `env.d.ts`에 Vite API base/proxy 환경 변수 타입이 선언되어 있다.
- P1 auth·Session, P2 profile, P3 `features/agent-runs`와 P4 `features/documents`, API·page·lazy route 구현·테스트가 있다.
- 실제 profile·Agent Run·Document 화면이 있으며 Dashboard 집계·공고·AI 설정 기능은 아직 없다.

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
