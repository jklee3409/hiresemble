# P1~P3 Page 안내

## 디렉터리 목적

P1 인증·보호 shell, P2 onboarding·profile과 P3 Agent Run list/detail page 및 전용 404를 관리한다.

## 주요 파일 및 하위 디렉터리

- [`SignupPage.vue`](SignupPage.vue): 가입 Form과 onboarding 이동
- [`LoginPage.vue`](LoginPage.vue): 로그인 Form과 안전한 returnTo
- [`OnboardingPage.vue`](OnboardingPage.vue): 기본 프로필·대표 학력·희망 조건·완료/추후 입력 P2 흐름
- [`DashboardPage.vue`](DashboardPage.vue): 보호 layout 검증 shell
- [`ProfileBasicPage.vue`](ProfileBasicPage.vue): 기본 프로필·완료 항목·희망 배열 form
- [`StructuredProfilePage.vue`](StructuredProfilePage.vue): 프로필 5종 목록·form·삭제·409 재적용
- [`ProfileEvidencePage.vue`](ProfileEvidencePage.vue): direct evidence filter·편집·검토와 read-only 상태
- [`AgentRunListPage.vue`](AgentRunListPage.vue): filter·pagination·sort 목록
- [`AgentRunDetailPage.vue`](AgentRunDetailPage.vue): REST snapshot, SSE 복구와 retry·cancel 조정
- [`RootRedirectPage.vue`](RootRedirectPage.vue): 인증 bootstrap 대기 shell
- [`NotFoundPage.vue`](NotFoundPage.vue): 전용 404
- [`authFlow.test.ts`](authFlow.test.ts): 가입·로그인·field 오류 component 흐름
- [`onboardingFlow.test.ts`](onboardingFlow.test.ts), [`profilePages.test.ts`](profilePages.test.ts): P2 page 흐름
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Page는 route 단위 사용자 상호작용을 조정하고 API·상태 세부는 shared/store에 위임한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`src/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- Dashboard 집계·API, AI 설정, 문서 업로드·연결과 P4 이후 기능을 선행 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
