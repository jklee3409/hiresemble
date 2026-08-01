# P1~P8 Page 안내

## 디렉터리 목적

P1 인증·보호 shell부터 P8 면접 준비·예상 질문 set·답변 feedback page 및 전용 404까지 관리한다.

## 주요 파일 및 하위 디렉터리

- [`SignupPage.vue`](SignupPage.vue): 가입 Form과 onboarding 이동
- [`LoginPage.vue`](LoginPage.vue): 로그인 Form과 안전한 returnTo
- [`OnboardingPage.vue`](OnboardingPage.vue): 기본 프로필·학력 단계와 서버 계산 최종 학력·희망 조건·문서 이동/추후 입력 P2 흐름
- [`GuidePage.vue`](GuidePage.vue): 실제 공통 UI mini preview로 내 정보→자료→자동 공고 분석→자기소개서→면접 순서를 안내하는 재방문 가능 가이드
- [`DashboardPage.vue`](DashboardPage.vue): 현재 profile·Document·Job·Agent Run API의 정확한 total과 최근 항목을 조합하는 지원 현황, 상태 기반 다음 할 일과 신규 사용자 시작 화면
- [`ProfileBasicPage.vue`](ProfileBasicPage.vue): Career Profile Workspace 안의 기본 정보·자기소개·희망 조건 편집 영역, Form 하단 저장 상태·409 재적용
- [`DashboardPage.test.ts`](DashboardPage.test.ts): 신규·기존 사용자, 사용자 이름 fallback과 부분 조회 오류 대시보드 회귀
- [`StructuredProfilePage.vue`](StructuredProfilePage.vue): Workspace 안의 학력 단계·서버 계산 최종 학력, 경력·자격증·어학·수상 목록·form·삭제·409 재적용과 학력 상태 한국어 표시
- [`ProfileActivitiesPage.vue`](ProfileActivitiesPage.vue): 문서 AI 추출 경험과 분리된 사용자 직접 대외활동 CRUD·소재 후보 사용 선택
- [`AgentRunListPage.vue`](AgentRunListPage.vue): filter·pagination·sort와 terminal 작업 개별·현재 페이지 선택 삭제
- [`AgentRunDetailPage.vue`](AgentRunDetailPage.vue): REST snapshot, SSE 복구와 retry·cancel 조정
- [`DocumentListPage.vue`](DocumentListPage.vue): upload·filter·pagination·sort와 두 상태 축 목록
- [`DocumentDetailPage.vue`](DocumentDetailPage.vue): 전문 자료 정보·preview·manual resume·다시 분석·원본 확인·delete와 batch evidence 검토
- [`JobListPage.vue`](JobListPage.vue): 상태 tab·검색·추출/마감 filter·정렬·pagination·상태 mutation
- [`JobNewPage.vue`](JobNewPage.vue): URL 우선 등록·접을 수 있는 직접 입력·마감일과 201/202 생성
- [`JobOverviewPage.vue`](JobOverviewPage.vue): 자동 준비 journey, 읽기 전용 document view와 분리된 편집·추출·retry·delete·version conflict
- [`JobAnalysisPage.vue`](JobAnalysisPage.vue): 최초 자동 분석 진행, 결과 summary·다음 행동, 보조 재분석·Eligibility/fit score·criterion/evidence·OUTDATED·이력
- [`JobCoverLetterPage.vue`](JobCoverLetterPage.vue): 공고 맥락 자기소개서 상태·진행률·생성/편집 진입
- [`CoverLetterListPage.vue`](CoverLetterListPage.vue): 간격이 분리된 전체 목록 URL filter·상태·archive/unarchive
- [`CoverLetterEditPage.vue`](CoverLetterEditPage.vue): 문항·TipTap·evidence·AI·version·verification·finalize canonical editor
- [`JobInterviewPage.vue`](JobInterviewPage.vue): 공고별 자기소개서·조사 품질·질문 조건 면접 준비 접수
- [`InterviewListPage.vue`](InterviewListPage.vue): `qs*` URL filter·pagination·sort 기반 예상 질문 set 목록
- [`InterviewQuestionSetPage.vue`](InterviewQuestionSetPage.vue): 조사 source·coverage·질문·답변 version·409·feedback 상세
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

- Dashboard의 명세상 전용 집계 endpoint는 아직 구현되지 않았다. 현재 API의 `totalElements`와 반환된 최근 항목만 표시하고 paginated `items.length`를 전체 수치로 사용하지 않는다.
- 공고 분석 결과는 해당 Job child page에서만 표시하며 Agent Run 목록·상세에 결과 전체를 복제하지 않는다.
- 자기소개서 전체 editor는 canonical edit route에만 두고 공고 tab과 Agent Run에는 상태·resource link만 표시한다.
- P8 `/interviews`는 예상 질문 set만 표시하며 P9 mock session placeholder를 만들지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
