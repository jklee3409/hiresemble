# E2E 테스트 영역 안내

## 디렉터리 목적

이 디렉터리는 Hiresemble의 핵심 사용자 여정을 실제 브라우저에서 검증하는 Playwright 테스트를 관리한다. 현재 P2 profile cross-stack과 P3 test-local Agent Run REST/SSE 시나리오가 구현되어 있다.

## 주요 파일 및 하위 디렉터리

| 경로                                                 | 역할                                                                   |
| ---------------------------------------------------- | ---------------------------------------------------------------------- |
| [`.gitkeep`](.gitkeep)                               | 초기 디렉터리 추적용 placeholder로 보존                                |
| [`profile.spec.ts`](profile.spec.ts)                 | 가입·온보딩·프로필 지속성·두 사용자 404·cache cleanup                  |
| [`agent-runs.spec.ts`](agent-runs.spec.ts)           | snapshot·disconnect·reconnect·polling·retry·cancel·logout fixture      |
| [`index.md`](index.md)                               | E2E 영역의 책임과 의존 관계 설명                                       |
| [`progress.md`](progress.md)                         | E2E 구현 상태와 검증 이력 추적                                         |
| [`../playwright.config.ts`](../playwright.config.ts) | test directory, Chromium project, Vite web server와 artifact 정책 설정 |

현재 하위 디렉터리는 없고 P2 profile과 P3 Agent Run spec을 관리한다.

## 구성 요소 역할

- 현재 인증·프로필을 검증하고 향후 공고, 자기소개서, 면접 준비 등 완결된 결과 흐름을 phase별로 추가한다.
- unit/component test로 확인하기 어려운 route 이동, form 상호작용, 화면 상태 연결을 다룬다.
- Playwright 설정은 테스트 전에 Vite 개발 서버를 시작하고 Chromium desktop 환경을 사용한다.

## 다른 디렉터리와의 의존 관계

- 테스트 대상 UI와 route는 [`../src/`](../src/)에 구현된다.
- 사용자 여정과 화면 완료 조건은 [`../../docs/spec/page.md`](../../docs/spec/page.md)와 [`../../docs/spec/functional.md`](../../docs/spec/functional.md)를 따른다.
- 서버 연동 시 [`../../backend/`](../../backend/)의 API 계약에 의존하지만, 테스트 데이터와 외부 서비스는 격리 가능한 환경을 사용해야 한다.

## 변경 시 주의사항

- 테스트마다 격리된 사용자 식별자를 사용하고 운영·기존 개발 데이터에 의존하지 않는다.
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
