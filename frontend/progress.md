# Progress

## Overview

- Vue 3, TypeScript, Vite, pnpm 기반 개발 환경과 주요 plugin이 구성되어 있다.
- P1 auth, P2 profile과 P3 Agent Run typed client·Vue Query·SSE 복구가 구현되어 있다.
- `/agent-runs` 목록·상세는 lazy route이며 AppLayout에는 lazy Progress Drawer가 연결되어 있다.
- Vitest 20 files/78 tests, profile E2E 1개와 Agent Run fixture E2E 2개가 있다. Dashboard·문서·AI 설정은 아직 없다.

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
