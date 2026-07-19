# Router 영역 안내

## 디렉터리 목적

이 디렉터리는 Hiresemble SPA의 P1 인증, P2 profile과 P3 Agent Run route table, browser history, metadata, guard와 안전한 `returnTo` 검증을 관리한다.

## 주요 파일 및 하위 디렉터리

| 경로                                   | 역할                                         |
| -------------------------------------- | -------------------------------------------- |
| [`index.ts`](index.ts)                 | 인증·profile·Agent Run·shell route와 guard   |
| [`returnTo.ts`](returnTo.ts)           | 등록된 보호 path만 허용하는 redirect 검증    |
| [`router.test.ts`](router.test.ts)     | public-only·auth-required·401·shell·404 test |
| [`returnTo.test.ts`](returnTo.test.ts) | scheme·host·control·미등록 path 거부 test    |
| [`index.md`](index.md)                 | Router 영역의 구조와 변경 원칙 설명          |
| [`progress.md`](progress.md)           | Route와 guard 구현 상태 추적                 |

현재 하위 디렉터리는 없다.

## 구성 요소 역할

- `index.ts`는 `/`, 인증, onboarding, dashboard, `/profile`, lazy `/agent-runs` route와 전용 404를 등록한다.
- public-only와 auth-required 정책을 metadata와 auth store bootstrap으로 구분한다.
- `returnTo`는 dashboard·onboarding·등록된 profile·Agent Run의 same-origin path만 허용한다.
- route name, path, page import와 layout 경계를 한눈에 추적할 수 있는 진입점 역할을 한다.

## 다른 디렉터리와의 의존 관계

- [`../main.ts`](../main.ts)가 router를 Vue plugin으로 등록하고 [`../App.vue`](../App.vue)의 `RouterView`가 일치한 component를 렌더링한다.
- route path와 화면 구조는 [`../../../docs/spec/page.md`](../../../docs/spec/page.md)를 기준으로 한다.
- 인증·CSRF 상태는 현재 auth store와 API client에 연결되지만 서버 인가 규칙을 대체하지 않는다.

## 변경 시 주의사항

- dashboard는 아직 shell이며 실제 집계 기능 완료로 기록하지 않는다.
- profile 미완료 상태를 보호 route hard gate로 사용하지 않는다.
- guard에서 도메인 데이터 조회나 서버 권한 판단을 중복 구현하지 않는다.
- 사용자가 북마크하거나 외부에서 접근할 수 있는 path 변경은 호환성과 redirect 필요성을 검토한다.
- route component가 늘어나면 초기 bundle 영향을 고려해 lazy import를 사용한다.

## 관련 규칙 및 문서

- [프론트엔드 소스 안내](../index.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [페이지 구조 명세](../../../docs/spec/page.md)
- [기능 명세](../../../docs/spec/functional.md)
- [Router 진행 상황](progress.md)
