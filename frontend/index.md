# 프론트엔드 모듈 안내

## 디렉터리 목적

이 디렉터리는 Hiresemble의 Vue 기반 단일 페이지 애플리케이션과 프론트엔드 개발·검증 설정을 관리한다. 현재 P1 인증, P2 프로필, P3 Agent Run과 P4 Document 목록·상세·SSE 복구 흐름이 구현되어 있다.

## 주요 파일 및 하위 디렉터리

| 경로                                           | 역할                                                        |
| ---------------------------------------------- | ----------------------------------------------------------- |
| [`src/`](src/)                                 | Vue 애플리케이션 진입점, 루트 컴포넌트, router와 전역 style |
| [`e2e/`](e2e/)                                 | Playwright profile·Agent Run fixture와 실제 Document E2E    |
| [`package.json`](package.json)                 | 의존성, Node/pnpm 요구 버전, 개발·검증 script               |
| [`pnpm-lock.yaml`](pnpm-lock.yaml)             | 재현 가능한 의존성 버전 잠금                                |
| [`vite.config.ts`](vite.config.ts)             | Vue/Tailwind plugin, alias, 개발 서버와 `/api` proxy 설정   |
| [`vitest.config.ts`](vitest.config.ts)         | jsdom unit/component 설정과 Playwright E2E 수집 제외        |
| [`playwright.config.ts`](playwright.config.ts) | Chromium E2E project와 Vite web server 설정                 |
| [`eslint.config.js`](eslint.config.js)         | TypeScript와 Vue lint 규칙                                  |
| [`tsconfig.json`](tsconfig.json)               | 애플리케이션·도구 TypeScript 설정 연결                      |
| [`.env.example`](.env.example)                 | API base URL과 개발 proxy target 예시                       |
| [`progress.md`](progress.md)                   | 프론트엔드 모듈의 현재 상태와 검증 이력                     |

## 구성 요소 역할

- `src/main.ts`가 공유 Pinia·QueryClient, Router, 401 reset과 PrimeVue를 조립한다.
- `src/App.vue`는 layout과 page가 표시되는 최소 route outlet을 제공한다.
- `src/shared/api`와 `src/stores`가 Session Cookie·CSRF, 인증 상태와 typed profile·Agent Run·Document transport를 관리한다.
- `src/layouts`, `src/features`, `src/pages`는 인증 shell, profile, Agent Run·Progress Drawer와 Document 검토 흐름을 제공한다.
- Vite는 로컬 `/api` 요청을 Spring 서버로 전달하고, Vitest와 Playwright는 각각 단위·컴포넌트 테스트와 브라우저 사용자 여정을 담당한다.
- `package.json`의 `check` script가 lint, Markdown을 포함한 format 검사, type 검사, unit test, production build를 묶는다.

## 다른 디렉터리와의 의존 관계

- 프론트엔드는 런타임에 [`../backend/`](../backend/)의 `/api/v1` HTTP 계약과 Session Cookie/CSRF 정책에 의존한다.
- 화면과 route는 [`../docs/spec/page.md`](../docs/spec/page.md), API client와 오류 처리는 [`../docs/spec/api.md`](../docs/spec/api.md)를 기준으로 구현한다.
- 저장소 공통 실행 환경과 인프라는 [`../compose.yaml`](../compose.yaml)과 루트 환경 변수 예시에서 관리한다.
- GitHub Actions는 [`../.github/workflows/ci.yml`](../.github/workflows/ci.yml)에서 이 모듈의 `pnpm check`를 실행한다.

## 변경 시 주의사항

- Dashboard 집계, AI 설정, 공고와 P5 이후 route는 아직 구현되지 않았으므로 shell을 제품 기능 완료로 기록하지 않는다.
- 서버 상태는 Vue Query, 꼭 필요한 클라이언트 전역 상태만 Pinia로 관리한다.
- `pnpm-lock.yaml`을 직접 편집하지 않고 pnpm을 통해 갱신한다.
- `frontend/` 아래 Markdown도 Prettier 검사 대상이 될 수 있으므로 문서 변경 후 `corepack pnpm check`를 확인한다.
- 비밀값이나 실제 사용자 데이터는 환경 파일, console, test fixture에 남기지 않는다.

## 관련 규칙 및 문서

- [최상위 Codex 지침](../AGENTS.md)
- [프론트엔드 개발 규칙](../docs/agent-rules/frontend-development.md)
- [공통 작업 절차](../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../docs/agent-rules/documentation-tracking.md)
- [페이지 구조 명세](../docs/spec/page.md)
- [프론트엔드 진행 상황](progress.md)
