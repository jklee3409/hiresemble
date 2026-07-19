# 프론트엔드 소스 안내

## 디렉터리 목적

이 디렉터리는 Vue 애플리케이션의 실행 코드와 타입 선언을 관리한다. P1 인증, P2 typed profile, P3 Agent Run과 P4 Document API·route·SSE 상호작용을 책임별 디렉터리로 구성한다.

## 주요 파일 및 하위 디렉터리

| 경로                         | 역할                                                     |
| ---------------------------- | -------------------------------------------------------- |
| [`main.ts`](main.ts)         | 전역 style import, Vue app 생성, plugin 등록과 DOM mount |
| [`App.vue`](App.vue)         | 현재 `RouterView`만 렌더링하는 루트 컴포넌트             |
| [`env.d.ts`](env.d.ts)       | Vite 환경 변수 TypeScript 타입 선언                      |
| [`router/`](router/)         | browser history와 route/guard 구성 영역                  |
| [`styles/`](styles/)         | Tailwind 진입점과 제한된 전역 style 영역                 |
| [`app/`](app/)               | Pinia·QueryClient bootstrap                              |
| [`features/`](features/)     | auth·profile·Agent Run·Document query·SSE·표현 규칙      |
| [`layouts/`](layouts/)       | PublicLayout·AppLayout와 lazy Progress Drawer            |
| [`pages/`](pages/)           | 인증·profile·Agent Run·Document page, 보호 shell와 404   |
| [`shared/`](shared/)         | typed 인증·profile·Agent Run·Document API와 cleanup      |
| [`stores/`](stores/)         | auth 상태와 사용자 경계 reset                            |
| [`progress.md`](progress.md) | 소스 영역의 구현 상태와 검증 이력                        |

## 구성 요소 역할

- `main.ts`는 Pinia, Vue Router, Vue Query와 PrimeVue Aura theme을 한 곳에서 조립한다.
- `App.vue`는 route component가 표시될 최상위 outlet만 소유한다.
- `env.d.ts`는 `VITE_API_BASE_URL`과 선택적 proxy target의 접근 타입을 고정한다.
- 세부 책임은 각 하위 디렉터리의 `index.md`에서 관리하며 P5 이후 빈 기능 계층은 만들지 않는다.

## 다른 디렉터리와의 의존 관계

- 앱 시작 문서는 [`../index.html`](../index.html)에서 `main.ts`를 불러오고, build·alias는 [`../vite.config.ts`](../vite.config.ts)와 TypeScript 설정에 의존한다.
- 향후 page와 component는 [`../../docs/spec/page.md`](../../docs/spec/page.md), API DTO와 오류 처리는 [`../../docs/spec/api.md`](../../docs/spec/api.md)를 따른다.
- browser 사용자 여정 검증은 [`../e2e/`](../e2e/)에서 수행한다.

## 변경 시 주의사항

- 기능 구현 전 의미 없는 디렉터리와 추상화를 미리 만들지 않는다.
- Vue SFC는 Composition API와 `<script setup lang="ts">`를 기본으로 하고 `any` 사용을 피한다.
- 서버 상태와 클라이언트 상태의 소유권을 Vue Query와 Pinia 사이에서 명확히 구분한다.
- App root에 도메인 규칙이나 page별 상태를 집중시키지 않는다.
- 사용자 문서나 개인정보를 console 또는 analytics에 기록하지 않는다.

## 관련 규칙 및 문서

- [프론트엔드 모듈 안내](../index.md)
- [프론트엔드 개발 규칙](../../docs/agent-rules/frontend-development.md)
- [페이지 구조 명세](../../docs/spec/page.md)
- [API 명세](../../docs/spec/api.md)
- [소스 진행 상황](progress.md)
