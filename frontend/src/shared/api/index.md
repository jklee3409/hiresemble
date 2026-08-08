# Frontend API Client 안내

## 디렉터리 목적

Backend P1~P8, Gate 2 GitHub Source와 Gate 3 Career Artifact OpenAPI 및 11개 Agent Run WorkflowType에 일치하는 TypeScript DTO, Axios·CSRF와 typed 오류 처리를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`contracts.ts`](contracts.ts): 인증·프로필·canonical 경험 request·response, 문서·GitHub evidence source와 error type
- [`http.ts`](http.ts): baseURL·cookie·CSRF interceptor
- [`authApi.ts`](authApi.ts): 다섯 인증 endpoint와 계정 닉네임 변경 consumer
- [`profileApi.ts`](profileApi.ts): 프로필·대외활동 CRUD, direct/document evidence batch 검토와 canonical 경험 목록·상세·수정·검증·병합/분리 consumer
- [`experienceContracts.ts`](experienceContracts.ts): canonical 경험·GitHub provenance 응답의 strict Zod 계약
- [`githubSourceContracts.ts`](githubSourceContracts.ts): GitHub Source·repository page·refresh와 Run parity strict Zod 계약
- [`githubSourceApi.ts`](githubSourceApi.ts): GitHub Source 7개 operation, typed sort·version·Idempotency-Key consumer
- [`careerArtifactContracts.ts`](careerArtifactContracts.ts): Career Artifact enum·projection·MIME·resource parity와 request strict Zod 계약
- [`careerArtifactApi.ts`](careerArtifactApi.ts): Career Artifact 11개 operation, CSRF·query·version과 create/regenerate 전용 Idempotency-Key consumer
- [`agentRunContracts.ts`](agentRunContracts.ts): 11개 workflow enum·DTO·SSE event Zod 계약
- [`agentRunApi.ts`](agentRunApi.ts): Agent Run 목록·상세·retry·cancel·개별/선택 history delete consumer
- [`documentContracts.ts`](documentContracts.ts): 원본 파일명을 포함한 문서·parse·evidence enum과 공개 DTO Zod 계약
- [`documentApi.ts`](documentApi.ts): 문서 공개 API 8개 multipart·version·idempotency consumer
- [`jobContracts.ts`](jobContracts.ts): 업무·추출·등록 상하반기·자동 `BALANCED` 분석 projection·P6 분석 enum과 공개 DTO의 strict Zod 계약
- [`jobApi.ts`](jobApi.ts): Job 공개 API 10개, 등록 기간 filter·version·idempotency consumer
- [`coverLetterContracts.ts`](coverLetterContracts.ts): P7 상태·질문·version·verification·선택 가능 AI model catalog strict Zod 계약
- [`coverLetterApi.ts`](coverLetterApi.ts): Cover Letter 공개 API 18개, exact model·CAS·Idempotency consumer
- [`interviewContracts.ts`](interviewContracts.ts): P8 조사·질문·답변·feedback strict Zod 계약
- [`interviewApi.ts`](interviewApi.ts): P8 공개 API 11개, CAS·Idempotency consumer
- [`dashboardContracts.ts`](dashboardContracts.ts): Dashboard·Career Guide strict Zod 응답 계약과 날짜별 count 일치 검증
- [`dashboardApi.ts`](dashboardApi.ts): 월별 Dashboard projection과 게시 Career Guide read consumer
- [`errors.ts`](errors.ts): typed error·field mapping과 bounded `Retry-After` 초 파싱
- [`http.test.ts`](http.test.ts): cookie·CSRF·401·409 transport test
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- HTTP transport를 화면에서 분리하고 Backend 오류 code를 parsing 없이 사용자 동작으로 변환한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`shared/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 성공 envelope를 가정하거나 활성 phase 밖 endpoint function을 추가하지 않는다. Dashboard 날짜별 count는 반환 items와 일치해야 한다.
- Agent Run 생성은 별도 공개 client로 만들지 않고 domain 202 응답의 Run ID를 사용한다. Career Artifact create/regenerate도 기존 `runAcceptedSchema`와 resource ID parity를 추가 검증한다. storage key·hash·provider metadata와 P9 이후 DTO를 type에 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../AGENTS.md)
- [공통 작업 절차](../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
