# P1 Page 안내

## 디렉터리 목적

P1 route에 대응하는 인증 Form, 보호 shell page와 전용 404를 관리한다.

## 주요 파일 및 하위 디렉터리

- [`SignupPage.vue`](SignupPage.vue): 가입 Form과 onboarding 이동
- [`LoginPage.vue`](LoginPage.vue): 로그인 Form과 안전한 returnTo
- [`OnboardingPage.vue`](OnboardingPage.vue): P2 전 shell
- [`DashboardPage.vue`](DashboardPage.vue): 보호 layout 검증 shell
- [`RootRedirectPage.vue`](RootRedirectPage.vue): 인증 bootstrap 대기 shell
- [`NotFoundPage.vue`](NotFoundPage.vue): 전용 404
- [`authFlow.test.ts`](authFlow.test.ts): 가입·로그인·field 오류 component 흐름
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Page는 route 단위 사용자 상호작용을 조정하고 API·상태 세부는 shared/store에 위임한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`src/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 프로필 입력, Dashboard 집계·API, 문서 업로드와 AI UI를 P1에 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
