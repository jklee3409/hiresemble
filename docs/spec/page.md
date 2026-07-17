# 페이지 구조 명세서

- 문서 버전: 1.0
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
│     ├─ overview
│     ├─ analysis
│     ├─ cover-letter
│     └─ interview
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
```

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

- `GET /auth/me`
- `GET /profile`
- `GET /documents?parseStatus=PARSING`
- `GET /jobs?status=IN_PROGRESS`
- `GET /jobs?status=SUBMITTED`
- `GET /agent-runs?status=RUNNING`

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
- 출처 문서·페이지
- 신뢰도
- 수정
- 승인
- 거절

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

---

# 7. 채용 공고

## 7.1 `/jobs`

### 상단 Tab

- 지원 중 (`IN_PROGRESS`)
- 서류 제출 (`SUBMITTED`)
- 마감 (`CLOSED`)

### Filter

- 회사·직무 검색
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

## 7.2 `/jobs/new`

### 입력

- URL 필수
- 회사명 선택
- 직무명 선택
- 본문 직접 입력 선택
- 마감일 선택

### 등록 결과

- 즉시 공고 상세로 이동
- URL 분석 Progress 표시
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

## 8.4 검증 표시

| 상태 | UI |
|---|---|
| PENDING | 회색 |
| PASSED | 초록 |
| WARNING | 노랑 |
| FAILED | 빨강 |

문장 클릭 시 연결 근거와 수정 제안 표시.

---

# 9. 면접 질문 세트 `/interview-question-sets/:questionSetId`

## 구성

### 조사 요약

- 조사 쿼리
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

## 10.3 완료 COMPLETED

- 종합 점수
- 강점
- 약점
- 질문별 피드백
- 다시 연습할 질문
- 추천 학습 주제
- 새 세션 시작

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

API:

- `GET /cover-letters`

## 11.2 면접 준비 목록 `/interviews`

- 예상 질문 세트 목록
- 모의 면접 세션 목록
- 회사·직무·상태 Filter
- 질문 세트 또는 세션 상세 이동

API:

- `GET /interview-question-sets`
- `GET /mock-interview-sessions`

## 11.3 작업 기록 `/agent-runs`

- Workflow, 상태, 시작·종료 시각
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

모델의 실제 공급자 이름은 운영자 설정에 따라 표시 여부를 결정한다.

## 13.3 `/settings/privacy`

- 업로드 문서 수와 저장량
- 문서 관리 페이지 이동
- 회원 탈퇴와 데이터 삭제
- AI 처리 동의 정보
- 외부 검색 출처 저장 정책
- 로그에 본문을 남기지 않는다는 안내

API:

- `GET /settings/privacy`
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
['profile']
['documents', filters]
['job', jobId]
['jobs', filters]
['coverLetter', coverLetterId]
['questionSet', questionSetId]
['agentRun', runId]
```

---

# 15. Route Guard

- Public Only: `/signup`, `/login`
- Auth Required: 그 외
- Profile Recommended: 공고 분석·자기소개서·면접 기능
- 필수 데이터 부족 시 차단 대신 경고와 프로필 이동 링크 제공
- 소유하지 않은 UUID 접근 시 404 페이지

---

# 16. 핵심 E2E 시나리오

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
