# Progress

## Overview

- `main.ts`가 Vue 앱과 Pinia, Router, Vue Query, PrimeVue를 초기화한다.
- `App.vue`는 `RouterView`만 제공하고 route 목록은 비어 있어 표시할 제품 화면이 없다.
- `env.d.ts`에 Vite API base/proxy 환경 변수 타입이 선언되어 있다.
- 도메인 page, component, API client, query, store와 프론트엔드 테스트는 아직 없다.

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
