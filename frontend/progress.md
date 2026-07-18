# Progress

## Overview

- Vue 3, TypeScript, Vite, pnpm 기반 개발 환경과 주요 plugin이 구성되어 있다.
- `src/main.ts`가 Pinia, Vue Router, Vue Query, PrimeVue Aura theme을 등록한다.
- `App.vue`는 `RouterView`만 제공하고 router의 `routes`는 비어 있어 제품 화면은 아직 없다.
- unit/component test와 E2E test 파일은 아직 없으며 실제 비즈니스 API client도 구현되지 않았다.

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
