# Vue 프론트엔드 개발 규칙

## 적용 범위

`frontend/`의 Vue SFC, TypeScript, router, style, test와 build 설정에 적용한다. 모든 변경 전에 [`workflow.md`](workflow.md), 화면 계약 변경 시 [`../spec/page.md`](../spec/page.md)와 [`../spec/api.md`](../spec/api.md)를 읽는다.

## 구조와 책임

- Vue 3 Composition API와 `<script setup lang="ts">`를 기본으로 사용한다.
- route page, feature component, 공용 UI, API client, query, store의 책임을 분리한다. 실제 기능이 생기기 전 빈 계층을 대량 생성하지 않는다.
- 서버 상태는 TanStack Vue Query, 로그인 사용자·전역 UI·네트워크 전 임시 draft 같은 최소 상태만 Pinia로 관리한다.
- API DTO와 form schema를 TypeScript/Zod로 명시하고 `any`와 무분별한 type assertion을 피한다.
- route guard는 public-only, auth-required, profile-recommended 정책을 구분한다.

## API와 오류 처리

- `/api/v1` 계약과 Session Cookie/CSRF를 따른다.
- 성공 응답을 임의의 envelope로 가정하지 않는다.
- 오류의 `timestamp`, `status`, `code`, `message`, `fieldErrors`, `requestId`를 typed client error로 정규화한다.
- code를 기준으로 사용자 동작을 결정하고 서버의 내부 message를 parsing하지 않는다.
- 401은 로그인 흐름, 403은 권한/CSRF 복구, 404는 resource not found, 409는 충돌 UX로 구분한다.
- mutation 재시도와 optimistic update는 멱등성·version 계약을 확인한 뒤 사용한다.

## UI와 스타일

- 접근 가능한 PrimeVue component와 semantic HTML을 우선한다.
- loading, empty, error, disabled, success 상태를 명시한다.
- Tailwind utility와 기존 theme을 사용하고 전역 CSS는 token/reset 같은 공통 책임으로 제한한다.
- 사용자 문서·자기소개서·면접 원문을 console이나 analytics에 기록하지 않는다.
- 반응형 layout과 keyboard focus를 주요 사용자 흐름에서 확인한다.

## 테스트와 검증

- 순수 logic과 form: Vitest
- component interaction: Vue Test Utils
- 핵심 사용자 여정: Playwright
- server call은 unit/component test에서 실제 backend나 외부 유료 API에 의존하지 않는다.
- 기본 검증은 `Set-Location frontend; corepack pnpm check`다.
- `pnpm-lock.yaml`은 package manager가 갱신하도록 하고 손으로 편집하지 않는다.

## 변경 시 주의사항

- 현재 router route와 E2E test는 비어 있고 `App.vue`는 `RouterView`만 제공한다. 명세 존재를 UI 구현 완료로 기록하지 않는다.
- dependency 추가 전에 기존 Vue/PrimeVue/TanStack/Zod 기능으로 해결 가능한지 확인한다.
- API schema 변경은 backend 규칙, frontend type, 관련 page/API 명세와 함께 갱신한다.
- formatter가 `frontend/` 아래 Markdown도 검사할 수 있으므로 추적 문서도 Prettier 검증 대상이다.

## 관련 문서

- [페이지 구조 명세](../spec/page.md)
- [API 명세](../spec/api.md)
- [프론트엔드 구조 안내](../../frontend/index.md)
- [문서 추적 규칙](documentation-tracking.md)
