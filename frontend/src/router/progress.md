# Progress

## Overview

- `index.ts`가 `createWebHistory(import.meta.env.BASE_URL)`로 router를 생성한다.
- `routes` 배열은 비어 있고 route metadata, navigation guard, page import는 없다.
- `main.ts`의 plugin 등록과 `App.vue`의 `RouterView` 연결만 완료되어 있다.

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
