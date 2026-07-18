# 페이지 구조 명세서

- 문서 버전: 1.1 (P0 승인 기준선)
- Frontend: Vue 3 SPA
- 기본 화면: Desktop First, 모바일 반응형
- API Prefix: `/api/v1`
- 공고 상태: `IN_PROGRESS`, `SUBMITTED`, `CLOSED`

---

## 1. 전체 라우트

```text
/
├─ /signup
├─ /login
├─ /onboarding
├─ /dashboard
├─ /profile
│  ├─ /profile/basic
│  ├─ /profile/education
│  ├─ /profile/certifications
│  ├─ /profile/languages
│  ├─ /profile/awards
│  ├─ /profile/careers
│  └─ /profile/evidence
├─ /documents
│  └─ /documents/:documentId
├─ /jobs
│  ├─ /jobs/new
│  └─ /jobs/:jobId
│     ├─ /jobs/:jobId/overview
│     ├─ /jobs/:jobId/analysis
│     ├─ /jobs/:jobId/cover-letter
│     ├─ /jobs/:jobId/interview
│     └─ /jobs/:jobId/interview/mock/new
├─ /cover-letters
│  └─ /cover-letters/:coverLetterId/edit
├─ /interviews
│  ├─ /interview-question-sets/:questionSetId
│  └─ /mock-interviews/:sessionId
├─ /agent-runs
│  └─ /agent-runs/:agentRunId
└─ /settings
   ├─ /settings/account
   ├─ /settings/ai
   └─ /settings/privacy

unmatched /:pathMatch(.*)* → 전용 404
```

Canonical redirect:

| 입력                     | 결과                                                                |
| ------------------------ | ------------------------------------------------------------------- |
| `/`, anonymous           | `/login`                                                            |
| `/`, authenticated       | `/dashboard`                                                        |
| `/signup`, authenticated | 방금 가입한 session이면 `/onboarding`, 그 외 `/dashboard`로 replace |
| `/login`, authenticated  | 안전한 `returnTo` 또는 `/dashboard`로 replace                       |
| `/profile`               | `/profile/basic`                                                    |
| `/jobs/:jobId`           | `/jobs/:jobId/overview`                                             |
| `/settings`              | `/settings/account`                                                 |

job 상세 tab child는 `overview|analysis|cover-letter|interview`, 별도 생성 child는 `interview/mock/new`만 허용한다. 타 사용자 UUID도 같은 404 화면을 사용한다.

---

## 2. 공통 Layout

## 2.1 PublicLayout

대상:

- `/signup`
- `/login`

구성:

- 서비스 로고
- 인증 Form
- 개인정보·AI 처리 안내
- 오류 메시지 영역

## 2.2 AppLayout

대상: 로그인 보호 페이지.

### 좌측 Navigation

- 대시보드
- 내 프로필
- 문서·근거
- 채용 공고
- 자기소개서
- 면접 준비
- 작업 기록
- 설정

### 상단 Header

- 현재 페이지 제목
- 진행 중 Agent Run 알림
- 사용자 메뉴
- 로그아웃

### 공통 상태 UI

- Skeleton
- Empty State
- Inline Validation
- Toast
- Confirm Dialog
- Agent Progress Drawer
- Version Conflict 비교·재적용 Dialog
- 인증 shell별 전용 404

Frontend의 TypeScript type과 runtime validation은 [`api.md`](api.md) 2장의 canonical enum 값을 이름과 의미까지 그대로 사용한다. 화면 전용 alias나 추가 상태를 만들지 않고 알 수 없는 값은 안전한 일반 상태와 갱신 안내로 처리한다. 특히 공고 업무·추출, 문서 parse·evidence 추출, 자기소개서, 조사 coverage, 모의 면접·feedback, Agent Run 상태 축을 서로 합치지 않는다.

---

# 3. 인증과 온보딩

## 3.1 `/signup`

### 구성

- 이메일
- 비밀번호
- 비밀번호 확인
- 표시 이름
- 이용약관 동의
- AI 처리 동의
- 가입 버튼

### API

- `GET /auth/csrf`
- `POST /auth/signup`

### 완료

- `/onboarding` 이동

## 3.2 `/login`

### API

- `POST /auth/login`
- `GET /auth/me`

성공 시 사용자의 마지막 보호 페이지 또는 `/dashboard`.

## 3.3 `/onboarding`

단계:

1. 기본 프로필
2. 대표 학력
3. 희망 직무·산업·지역
4. 이력서 또는 포트폴리오 업로드
5. 추후 입력 선택

API:

- `PUT /profile`
- `POST /profile/educations`
- `POST /documents`

문서 분석은 완료를 기다리지 않고 대시보드로 이동 가능.

가입 직후만 `/onboarding`으로 이동한다. 이후에는 프로필 완료 여부로 route를 강제 redirect하지 않는다. `legalName`, 희망 직무·산업·지역 각 1개, 대표 학력 1개 중 부족 항목과 충족 항목당 20%인 완료율을 경고·프로필 이동 링크와 함께 표시하되 공고·분석·자기소개서·면접 진입을 일괄 차단하지 않는다.

---

# 4. 대시보드 `/dashboard`

## 주요 카드

1. 프로필 완성도
2. 처리 중 문서
3. 지원 중 공고 수
4. 서류 제출 공고 수
5. 마감 임박 공고
6. 검증 경고가 있는 자기소개서
7. 최근 모의 면접
8. 진행 중 Agent Run

## 빠른 작업

- 공고 URL 등록
- 이력서 업로드
- 지원서 작성
- 모의 면접 시작

## API

- `GET /dashboard`

Dashboard count와 최근 항목은 paginated 목록 첫 page에서 추정하지 않는다. 프로필 카드에는 완료율, 부족 항목과 `/profile/basic` 링크를 표시하며 미완료를 기능 차단으로 표현하지 않는다.

---

# 5. 프로필

## 5.1 `/profile/basic`

### Form

- 이름
- 간단 소개
- 졸업 예정일
- 희망 직무
- 희망 산업
- 희망 지역

API:

- `GET /profile`
- `PUT /profile`

## 5.2 `/profile/education`

- 학력 카드 목록
- 추가·수정 Modal
- 대표 학력 배지
- 삭제 확인

API:

- `GET /profile/educations`
- `POST /profile/educations`
- `PUT /profile/educations/:id`
- `DELETE /profile/educations/:id`

## 5.3 `/profile/certifications`

자격증 목록·Form. 증빙 문서 연결.

## 5.4 `/profile/languages`

어학 시험, 점수, 응시·만료일.

## 5.5 `/profile/awards`

수상 목록과 설명, 증빙 연결.

## 5.6 `/profile/careers`

경력 Timeline, 역할·성과 편집.

## 5.7 `/profile/evidence`

### Filter

- 상태: PENDING, VERIFIED, REJECTED, SOURCE_DELETED
- 카테고리
- 출처 문서

### Evidence Card

- 제목
- 근거 내용
- 출처 유형·연결 문서·원천 삭제 여부
- 신뢰도
- 수정
- 승인
- 거절

`SOURCE_DELETED` card는 원천 삭제 marker와 과거 사용처만 읽기 전용으로 표시하고 수정·승인·거절 action을 숨긴다.

API:

- `GET /profile/evidence`
- `PUT /profile/evidence/:id`
- `PATCH /profile/evidence/:id/verification`

---

# 6. 문서

## 6.1 `/documents`

### 구성

- Upload Dropzone
- 문서 유형 선택
- 지원 확장자·용량 안내
- 문서 Table/Grid
- 파싱 상태
- 근거 추출 상태
- 업로드 일시
- 재처리·다운로드·삭제

API:

- `POST /documents`
- `GET /documents`
- `POST /documents/:id/reparse`
- `POST /documents/:id/download-url`
- `DELETE /documents/:id`

## 6.2 `/documents/:documentId`

### 영역

- 메타데이터
- 파싱 상태와 오류
- 추출 텍스트 Preview
- 수동 텍스트 편집
- 추출된 사용자 근거
- Agent Run 링크

API:

- `GET /documents/:id`
- `GET /documents/:id/text`
- `PUT /documents/:id/manual-text`
- `GET /profile/evidence?documentId=:id`

`PARSED + evidenceExtractionStatus=FAILED`는 추출 text를 유지하고 문서 업로드 실패로 표시하지 않는다. safe error, Agent Run과 재처리 CTA를 제공한다. 문서 삭제 성공 즉시 상세·download·cache에서 제거하고 이후 404를 정상 삭제 결과로 처리한다.

---

# 7. 채용 공고

## 7.1 `/jobs`

### 상단 Tab

- 지원 중 (`IN_PROGRESS`)
- 서류 제출 (`SUBMITTED`)
- 마감 (`CLOSED`)

### Filter

- 회사·직무 검색
- URL 추출 상태
- 마감 기간
- 마감 임박
- 정렬

### Job Card

- 회사
- 공고·직무
- 상태
- 마감일
- 추출 상태
- 적합도
- 자기소개서·면접 준비 진행도
- 상태 변경 Menu

API:

- `GET /jobs`
- `PATCH /jobs/:id/status`

### 상태 표시 규칙

`CLOSED`로 변경돼도 `submittedAt`이 있으면 `서류 제출 이력 있음` 보조 배지를 표시한다.

업무 상태 `IN_PROGRESS|SUBMITTED|CLOSED`와 추출 상태를 별도 badge로 표시한다.

- `NEEDS_MANUAL_INPUT`: 업무 상태를 유지하고 본문·마감 직접 입력을 강조하며 분석을 비활성화
- `FAILED`: safe error, 추출 재시도와 수동 입력을 모두 제공
- `MANUAL_INPUT_PROVIDED`: 수동 본문으로 분석 가능

## 7.2 `/jobs/new`

### 입력

- URL 필수
- 회사명 선택
- 직무명 선택
- 본문 직접 입력 선택
- 마감일 선택

### 등록 결과

- 즉시 공고 상세로 이동
- 직접 입력 본문이 없으면 `QUEUED` URL 분석 Progress 표시
- 직접 입력 본문이 있으면 `MANUAL_INPUT_PROVIDED`로 표시하고 URL 분석 Progress를 만들지 않음
- 분석 실패 시 본문 직접 입력 Prompt

API:

- `POST /jobs`
- `GET /agent-runs/:runId`
- `GET /agent-runs/:runId/events`

## 7.3 `/jobs/:jobId`

상세 페이지 내부 Tab.

### Overview Tab

- 회사·직무·URL
- 공고 본문
- 마감일과 출처
- 상태 변경
- 편집
- URL 재추출
- 삭제

API:

- `GET /jobs/:id`
- `PUT /jobs/:id`
- `PATCH /jobs/:id/status`
- `POST /jobs/:id/retry-extraction`
- `DELETE /jobs/:id`

### Analysis Tab

- 분석 실행·재실행
- 지원 가능 여부
- 적합도 점수 안내
- 주요 업무
- 필수·우대
- 강점
- 부족한 점
- 매칭 근거
- 분석 버전

`analysisOutdated=true`이면 기존 분석을 유지하고 노란 `OUTDATED` badge, reason과 재분석 CTA를 표시한다. downstream 기능을 일괄 차단하지 않는다. `Eligibility`와 `fitScore`는 서로 다른 영역으로 표시하고 `INELIGIBLE` 점수도 그대로 표시한다.

다음 문구를 점수 가까이에 항상 표시한다.

> 적합도 점수는 합격 가능성이 아니라 등록된 정보와 공고 요구사항의 일치도를 나타냅니다.

API:

- `POST /jobs/:id/analysis`
- `GET /jobs/:id/analyses`
- `GET /jobs/:id/analyses/latest`

### Cover Letter Tab

- 자기소개서 존재 여부
- 문항별 현재 답변 Preview
- 검증 상태
- 편집 이동

API:

- `POST /jobs/:id/cover-letter`
- `GET /cover-letters?jobId=:id`
- `GET /cover-letters/:coverLetterId`로 문항·current answer·검증 preview 조회

### Interview Tab

- 질문 세트
- 조사 상태
- 최근 모의 면접
- 면접 준비 생성

API:

- `POST /jobs/:id/interview-preparations`
- `GET /interview-question-sets?jobId=:id`
- `GET /mock-interview-sessions?jobId=:id`

---

# 8. 자기소개서 편집 `/cover-letters/:coverLetterId/edit`

## 8.1 화면 구조

```text
[좌측] 문항 Navigator
[중앙] TipTap 답변 Editor
[우측] 공고 요구사항 / 사용자 근거 / 검증 Panel
[하단] 버전 이력 Drawer
```

## 8.2 기능

- 문항 추가·수정·삭제·정렬
- 최대 글자 수와 현재 글자 수
- 관련 경험 선택
- AI 초안 생성
- 사용자 수정
- 브라우저 로컬 임시 저장
- 명시적 서버 버전 저장
- 과거 버전 비교·복원
- 현재 버전 검증
- 검증 제안 선택 적용
- 최종화
- archive·unarchive

## 8.3 API

- `GET /cover-letters/:id`
- `POST /cover-letters/:id/questions`
- `PUT /cover-letters/:id/questions/:questionId`
- `DELETE /cover-letters/:id/questions/:questionId`
- `PATCH /cover-letters/:id/questions/order`
- `POST /cover-letters/:id/generate`
- `GET /cover-letter-questions/:questionId/versions`
- `POST /cover-letter-questions/:questionId/versions`
- `POST /cover-letter-questions/:questionId/versions/:versionId/restore`
- `POST /cover-letter-answer-versions/:versionId/verify`
- `GET /cover-letter-answer-versions/:versionId/verifications`
- `POST /cover-letters/:id/finalize`
- `POST /cover-letters/:id/archive`
- `POST /cover-letters/:id/unarchive`

## 8.4 검증 표시

| 상태    | UI   |
| ------- | ---- |
| PENDING | 회색 |
| PASSED  | 초록 |
| WARNING | 노랑 |
| FAILED  | 빨강 |

문장 클릭 시 연결 근거와 수정 제안 표시.

`PENDING|FAILED`는 최종화 버튼을 비활성화한다. `WARNING`은 해당 verification별 확인 checkbox를 제공하고 확인한 ID만 finalize request에 보낸다. current answer가 바뀌면 과거 검증을 fresh로 취급하지 않는다.

`ARCHIVED` 상세은 읽기 전용이다. 과거 version·verification 조회는 허용하지만 title/question/answer save·restore·generate·새 verify·finalize는 비활성화한다. active 자기소개서가 없고 server `canUnarchive=true`일 때만 unarchive를 제공한다.

---

# 9. 면접 질문 세트 `/interview-question-sets/:questionSetId`

## 구성

### 조사 요약

- 조사 주제와 source coverage
- 검색 품질
- 회사·채용 프로세스 요약
- 출처 목록
- 공식·커뮤니티 구분
- 발행일·조회일

### 예상 질문

Filter:

- 이력서
- 자기소개서
- 기술
- 행동
- 회사
- 꼬리 질문

질문 Card:

- 질문
- 의도
- 평가 포인트
- 답변 가이드
- 관련 근거
- 꼬리 질문
- 답변 작성
- 피드백 요청

### API

- `GET /interview-question-sets/:id`
- `GET /research-runs/:id`
- `GET /research-runs/:id/sources`
- `GET /interview-questions/:id`
- `POST /interview-questions/:id/answer-versions`
- `POST /interview-answer-versions/:id/feedback`
- `GET /interview-answer-versions/:id/feedbacks`

`SourceCoverage`를 `SUFFICIENT|LIMITED|NONE`으로 표시한다. `LIMITED|NONE`은 실패가 아니라 출처 부족 경고이며 source-based가 아닌 질문을 구분한다. 조사 실패는 safe error와 새 research/question set을 만드는 retry action을 제공한다.

답변 feedback 접수 뒤 Agent Run을 연결하고 성공 이력만 feedback 목록에 표시한다. 실행 실패·취소는 빈 PENDING feedback row가 아니라 Agent Run safe error와 retry 가능 여부로 표시한다.

---

# 10. 모의 면접 `/mock-interviews/:sessionId`

## 10.1 시작 전 READY

설정 요약:

- 공고·직무
- 사용할 자기소개서
- 질문 세트
- 면접 유형
- 난이도
- 목표 질문 수
- 피드백 방식
- 압박 모드

버튼:

- 시작
- 취소

생성 화면은 `/jobs/:jobId/interview/mock/new`다. `/interviews` 빠른 작업은 공고 선택 뒤 이 route로 이동하며 type, 난이도, 목표 질문 수, feedback timing, pressure mode와 VERIFIED evidence 최대 5개를 입력한다.

## 10.2 진행 IN_PROGRESS

### 중앙 대화

- 면접관 질문
- 사용자 답변 입력
- 답변 전송
- 진행 질문 수
- 종료 버튼

### 우측 Context

- 현재 질문 유형
- 관련 프로젝트
- 남은 질문 수
- 즉시 피드백 모드일 때 피드백

### API

- `POST /mock-interview-sessions/:id/start`
- `POST /mock-interview-sessions/:id/messages`
- `POST /mock-interview-sessions/:id/complete`
- `POST /mock-interview-sessions/:id/cancel`

중복 전송 방지를 위해 전송 중 입력을 잠그고 client request ID를 사용한다.

start/message마다 새 UUID `clientRequestId`와 현재 session version을 보낸다. timeout이나 연결 단절 뒤 같은 ID를 유지해 성공 또는 실패 terminal 응답을 복구한다. `409 MOCK_TURN_IN_PROGRESS`는 처리 중 상태로 polling하고 새 ID를 자동 발급하지 않는다. 저장된 실패가 replay되면 안전 오류를 그대로 표시하며, 사용자가 명시적으로 새 유료 시도를 선택할 때만 새 ID를 만든다. 요청당 최대 chat 1회·20초·USD 0.03과 session USD 0.30 상한을 안내하고 search/embedding은 사용하지 않는다.

## 10.3 완료 COMPLETED

- 종합 점수
- 강점
- 약점
- 질문별 피드백
- 다시 연습할 질문
- 추천 학습 주제
- 새 세션 시작

session `COMPLETED`와 feedback 상태를 분리한다.

- `QUEUED|RUNNING`: transcript는 열고 feedback skeleton·Agent Run 링크를 표시
- `FAILED`: transcript를 유지하고 safe error와 retryable Agent Run action 표시
- `SUCCEEDED`: 종합 feedback 표시
- `CANCELLED`: transcript를 유지하고 취소 상태 표시

API:

- `GET /mock-interview-sessions/:id`
- `GET /mock-interview-sessions/:id/messages`
- `GET /mock-interview-sessions/:id/feedbacks`

---

# 11. 목록 페이지

## 11.1 자기소개서 목록 `/cover-letters`

- 공고·회사·상태·최근 수정일
- `DRAFT`, `FINALIZED`, `ARCHIVED` Filter
- 편집 페이지 이동
- active row archive, archived row 조건부 unarchive
- archived read-only badge와 server action boolean 사용

API:

- `GET /cover-letters`

## 11.2 면접 준비 목록 `/interviews`

- 예상 질문 세트 목록
- 모의 면접 세션 목록
- 회사·직무·상태 Filter
- 질문 세트 또는 세션 상세 이동
- 모의 면접 새 세션 생성

API:

- `GET /interview-question-sets`
- `GET /mock-interview-sessions`

두 목록은 URL query와 pagination namespace를 분리한다.

- question set: `qsJobId`, `qsCoverLetterId`, `qsQuery`, `qsSourceCoverage`, `qsResearchStatus`, `qsSort`, `qsPage`, `qsSize`
- mock session: `mockJobId`, `mockQuery`, `mockStatus`, `mockFeedbackStatus`, `mockSort`, `mockPage`, `mockSize`

API 요청 시 각각 canonical API parameter 이름으로 변환한다.

## 11.3 작업 기록 `/agent-runs`

- Workflow, 상태, 접수·최근 갱신 시각
- 실패·중단 Filter
- 비용과 재시도 가능 여부
- 상세 실행 이동

API:

- `GET /agent-runs`

---

# 12. Agent Run `/agent-runs/:agentRunId`

## 구성

- Workflow 명
- 상태
- 진행률
- 현재 단계
- 단계 Timeline
- 모델 등급
- 소요 시간
- 비용
- 안전한 오류 메시지
- 재시도·취소

API:

- `GET /agent-runs/:id`
- `GET /agent-runs/:id/events`
- `POST /agent-runs/:id/retry`
- `POST /agent-runs/:id/cancel`

실패 단계의 내부 프롬프트·민감 데이터는 표시하지 않는다.

연결 직후 `snapshot`을 원천으로 사용하고 `progress|step|waiting_user|heartbeat|terminal` event를 적용한다. `stateVersion`이 낮거나 같은 event는 무시한다. 1/2/5/10/30초 backoff로 재연결하고 3회 실패하면 5초 REST polling으로 전환한다. SSE 단절만으로 run 실패를 표시하지 않으며 terminal snapshot 뒤 stream을 닫고 resource query를 invalidate한다.

`WAITING_USER`는 `requiredUserAction` deep link를 표시하고 일반 retry를 비활성화한다. `FAILED|INTERRUPTED`는 `retryable=true`, active run은 `cancellable=true`일 때만 action을 제공한다. provider/model, prompt, hash, reuse detail은 표시하지 않고 `highestModelTierUsed`만 안전한 모델 등급으로 보여 준다.

`actualCostUsd`는 provider 청구서 확정액이 아니라 접수 시 고정된 price catalog로 계산한 billable estimate임을 비용 영역에 표시한다.

---

# 13. 설정

## 13.1 `/settings/account`

- 표시 이름
- 비밀번호 변경
- 로그아웃
- 회원 탈퇴

API:

- `PATCH /account/display-name`
- `PATCH /account/password`
- `POST /auth/logout`
- `DELETE /account`

## 13.2 `/settings/ai`

- 기본 품질 모드: 절약 / 균형
- 고품질 최종 검토 사용 여부
- 사용자 일일 비용 한도
- 시스템 최대 비용 한도
- AI 데이터 처리 안내

API:

- `GET /settings/ai`
- `PUT /settings/ai`

일반 사용자 화면에는 provider/model 실명을 표시하지 않는다. `HIGH_QUALITY`는 자기소개서 생성·검증과 면접 답변 feedback에서만 선택할 수 있으며 설정 활성화·요청별 선택·예산 예약이 모두 필요하다는 안내를 제공한다. reset zone은 `Asia/Seoul`, 초기 user daily 1.00/system max 2.00 USD를 표시한다.

## 13.3 `/settings/privacy`

- 업로드 문서 수와 저장량
- 문서 관리 페이지 이동
- 회원 탈퇴와 데이터 삭제
- AI 처리 동의 정보
- 외부 검색 출처 저장 정책
- 로그에 본문을 남기지 않는다는 안내

API:

- `GET /settings/privacy`

외부 검색 출처 저장 정책은 versioned 제품 문구이며 `PrivacySettingsDto`가 반환하는 계정별 동적 field가 아니다.

- `GET /documents`
- `DELETE /account`

---

# 14. Frontend 상태 관리

## Pinia

- `authStore`: 현재 사용자, 로그인 여부
- `uiStore`: Navigation, Drawer, Toast
- `draftStore`: 네트워크 전송 전 임시 편집 복구

## Vue Query

- profile
- documents
- evidence
- jobs
- job analyses
- cover letters
- interview sets
- mock sessions
- agent runs

Query Key 예시:

```text
['user', userId, 'profile']
['user', userId, 'documents', filters]
['user', userId, 'job', jobId]
['user', userId, 'jobs', filters]
['user', userId, 'coverLetter', coverLetterId]
['user', userId, 'questionSet', questionSetId]
['user', userId, 'agentRun', runId]
```

목록 filter는 URL query가 공유 가능한 원천이다. Zod에서 유효하지 않은 값을 제거해 canonical URL로 replace하고 filter 변경 시 해당 page를 0으로 reset한다.

- documents: `documentType`, `parseStatus`, `evidenceExtractionStatus`, `sort`, `page`, `size`
- evidence: `verificationStatus`, `evidenceCategory`, `documentId`, `sort`, `page`, `size`
- jobs: `status`, `extractionStatus`, `query`, `deadlineFrom`, `deadlineTo`, `deadlineWithinDays`, `sort`, `page`, `size`
- cover letters: `jobId`, `status`, `query`, `sort`, `page`, `size`
- Agent Runs: repeatable `workflowType`, repeatable `status`, `resourceType`, `resourceId`, `retryable`, `sort`, `page`, `size`
- `/interviews`의 두 목록은 11.2의 `qs*`·`mock*` URL namespace를 API parameter로 변환한다.

Browser draft는 `sessionStorage`만 사용한다.

- key: `schemaVersion/userId/resourceType/resourceId/questionId/baseVersionId`
- value: content JSON, baseVersionId, savedAt만 저장
- savedAt 기준 최대 24시간이며 browser session 종료 시 함께 사라짐
- server save, question 삭제, archive, logout, 탈퇴, 인증 사용자 ID 변경 시 해당 draft 삭제
- server base version과 다르면 자동 덮어쓰지 않고 server snapshot과 draft 비교·재적용 UI 제공
- Session 만료 중에는 이전 draft를 render하지 않고 같은 user 재인증 뒤에만 복구 후보로 표시

logout·탈퇴·401 auth reset·user ID 변경 시 EventSource 종료→in-flight query 취소→`queryClient.clear()`→Pinia reset→해당 user draft purge 순으로 처리한다.

---

# 15. Route Guard

- Public Only: `/signup`, `/login`
- Auth Required: 그 외
- Profile Recommended: 공고 분석·자기소개서·면접 기능
- Profile Recommended는 완료율 표시와 경고뿐이며 `profileCompleted=false`로 강제 redirect하지 않음
- 필수 데이터 부족 시 개별 workflow prerequisite에 따라 경고·409를 처리하고 프로필 이동 링크 제공
- 소유하지 않은 UUID 접근 시 404 페이지

`returnTo`는 한 번 decode한 same-origin registered auth-required path만 허용한다. `//`, scheme, host, backslash, CR/LF, login/signup/404는 거부하고 localStorage나 referrer에 저장하지 않는다.

---

# 16. 409 충돌 UX

- version 충돌은 mutation 자동 재시도와 optimistic overwrite를 금지한다.
- 최신 server snapshot과 사용자의 미저장 form/draft를 나란히 보여 주고 field별 재적용 또는 취소를 선택하게 한다.
- idempotency 처리 중과 mock turn 처리 중은 기존 request ID로 상태를 복구한다.
- active cover letter, archive, prerequisite 같은 상태 충돌은 server action boolean과 안정적인 error code로 CTA를 결정한다.

---

# 17. 핵심 E2E 시나리오

## 시나리오 A

```text
가입
→ 온보딩
→ 이력서 PDF 업로드
→ 추출 근거 승인
→ 공고 URL 등록
→ 분석 완료
```

## 시나리오 B

```text
공고 상세
→ 자기소개서 생성
→ 문항 추가
→ AI 초안
→ 사용자 수정 버전 저장
→ 근거 검증
→ 최종화
→ 공고 상태 SUBMITTED
```

## 시나리오 C

```text
공고 Interview Tab
→ 면접 준비 생성
→ 검색 출처 확인
→ 예상 질문 답변 작성
→ 피드백
→ 모의 면접 생성·진행
→ 종합 피드백 조회
```
