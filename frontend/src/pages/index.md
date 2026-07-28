# P1~P5 Page 안내

## 디렉터리 목적

P1 인증·보호 shell, P2 onboarding·profile, P3 Agent Run, P4 Document와 P5 Job page 및 전용 404를 관리한다.

## 주요 파일 및 하위 디렉터리

- [`SignupPage.vue`](SignupPage.vue): 가입 Form과 onboarding 이동
- [`LoginPage.vue`](LoginPage.vue): 로그인 Form과 안전한 returnTo
- [`OnboardingPage.vue`](OnboardingPage.vue): 기본 프로필·대표 학력·희망 조건·문서 이동/추후 입력 P2 흐름
- [`DashboardPage.vue`](DashboardPage.vue): 실제 구현 route로 연결하는 집계 없는 작업 공간
- [`ProfileBasicPage.vue`](ProfileBasicPage.vue): Career Profile Workspace 안의 기본 정보·희망 조건 form과 저장 후 다음 section 이동
- [`StructuredProfilePage.vue`](StructuredProfilePage.vue): Workspace 안의 학력·경력·자격증·어학·수상 목록·form·삭제·409 재적용
- [`ProfileEvidencePage.vue`](ProfileEvidencePage.vue): Workspace 안의 자료 기반 경험 정보 filter·편집·검토와 read-only 상태
- [`AgentRunListPage.vue`](AgentRunListPage.vue): filter·pagination·sort 목록
- [`AgentRunDetailPage.vue`](AgentRunDetailPage.vue): REST snapshot, SSE 복구와 retry·cancel 조정
- [`DocumentListPage.vue`](DocumentListPage.vue): upload·filter·pagination·sort와 두 상태 축 목록
- [`DocumentDetailPage.vue`](DocumentDetailPage.vue): text·manual resume·reparse·download·delete·evidence 검토
- [`JobListPage.vue`](JobListPage.vue): 상태 tab·검색·추출/마감 filter·정렬·pagination·상태 mutation
- [`JobNewPage.vue`](JobNewPage.vue): URL 우선 등록·접을 수 있는 직접 입력·마감일과 201/202 생성
- [`JobOverviewPage.vue`](JobOverviewPage.vue): 공고 본문·상태·추출·편집·retry·delete·version conflict
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

- Dashboard 집계·API, AI 설정과 P6 이후 기능을 선행 추가하지 않고 현재 API가 제공하는 정보만 표시한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
