# E2E 테스트 영역 안내

## 디렉터리 목적

이 디렉터리는 Hiresemble의 핵심 사용자 여정을 실제 브라우저에서 검증하는 Playwright 테스트를 관리한다. 현재는 디렉터리 유지용 파일만 있고 테스트 시나리오는 아직 구현되지 않았다.

## 주요 파일 및 하위 디렉터리

| 경로                                                 | 역할                                                                   |
| ---------------------------------------------------- | ---------------------------------------------------------------------- |
| [`.gitkeep`](.gitkeep)                               | 테스트가 없는 현재 빈 디렉터리를 Git에서 유지                          |
| [`index.md`](index.md)                               | E2E 영역의 책임과 의존 관계 설명                                       |
| [`progress.md`](progress.md)                         | E2E 구현 상태와 검증 이력 추적                                         |
| [`../playwright.config.ts`](../playwright.config.ts) | test directory, Chromium project, Vite web server와 artifact 정책 설정 |

현재 하위 디렉터리와 `*.spec.ts` 테스트 파일은 없다.

## 구성 요소 역할

- 향후 인증, 프로필, 공고, 자기소개서, 면접 준비 등 사용자가 완결된 결과를 얻는 핵심 흐름을 브라우저 관점에서 검증한다.
- unit/component test로 확인하기 어려운 route 이동, form 상호작용, 화면 상태 연결을 다룬다.
- Playwright 설정은 테스트 전에 Vite 개발 서버를 시작하고 Chromium desktop 환경을 사용한다.

## 다른 디렉터리와의 의존 관계

- 테스트 대상 UI와 route는 [`../src/`](../src/)에 구현된다.
- 사용자 여정과 화면 완료 조건은 [`../../docs/spec/page.md`](../../docs/spec/page.md)와 [`../../docs/spec/functional.md`](../../docs/spec/functional.md)를 따른다.
- 서버 연동 시 [`../../backend/`](../../backend/)의 API 계약에 의존하지만, 테스트 데이터와 외부 서비스는 격리 가능한 환경을 사용해야 한다.

## 변경 시 주의사항

- route와 화면이 아직 비어 있으므로 현재 E2E 커버리지가 있다고 기록하지 않는다.
- 실제 유료 AI·검색 API나 운영 데이터에 의존하는 시나리오를 작성하지 않는다.
- 안정적인 role/label 기반 locator를 우선하고 구현 세부 CSS selector 의존을 줄인다.
- `playwright-report`, `test-results`, trace 등 자동 생성 artifact에는 추적 문서를 만들거나 커밋하지 않는다.
- 테스트 추가 후 `corepack pnpm test:e2e`를 통합 `pnpm check`와 별도로 실행한다.

## 관련 규칙 및 문서

- [프론트엔드 모듈 안내](../index.md)
- [프론트엔드 개발 규칙](../../docs/agent-rules/frontend-development.md)
- [공통 작업 절차](../../docs/agent-rules/workflow.md)
- [페이지 구조 명세](../../docs/spec/page.md)
- [E2E 진행 상황](progress.md)
