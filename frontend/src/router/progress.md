# Progress

## Overview

- `index.ts`가 `createWebHistory(import.meta.env.BASE_URL)`로 router를 생성한다.
- `/`, 인증, onboarding, dashboard, profile과 lazy Agent Run list/detail 및 전용 404 route가 구현되어 있다.
- `returnTo.ts`가 same-origin 등록 보호 path와 UUID Agent Run detail만 허용한다.

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
