#  API 명세서

- 문서 버전: 1.0
- Base URL: `/api/v1`
- 인증: Spring Session Cookie
- Content-Type: `application/json`
- 파일 업로드: `multipart/form-data`
- 시간: ISO-8601 UTC
- ID: UUID
- 장기 작업: `202 Accepted` + `agentRunId`
- 페이지네이션: `page`, `size`, `sort`

---

## 1. 공통 규칙

### 1.1 인증

- 세션 쿠키는 HttpOnly
- 상태 변경 요청은 CSRF 토큰 필요
- `GET /auth/csrf`에서 토큰 조회
- 미인증: `401`
- 다른 사용자의 리소스: 존재 여부를 숨기기 위해 `404`

### 1.2 공통 성공 응답

단일 리소스는 직접 반환한다.

```json
{
  "id": "uuid",
  "createdAt": "2026-07-17T08:00:00Z"
}
```

목록:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### 1.3 오류 응답

```json
{
  "timestamp": "2026-07-17T08:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": [
    {"field": "deadlineAt", "reason": "INVALID_DATE"}
  ],
  "requestId": "uuid"
}
```

### 1.4 낙관적 잠금

편집 API는 필요한 경우 `version` 필드를 전달한다. 충돌 시 `409 RESOURCE_VERSION_CONFLICT`.

### 1.5 장기 작업

```json
{
  "agentRunId": "uuid",
  "status": "QUEUED",
  "resourceId": "uuid"
}
```

진행 조회:

- `GET /agent-runs/{agentRunId}`
- `GET /agent-runs/{agentRunId}/events` (`text/event-stream`)

---

# 2. 인증

## POST /auth/signup

회원 가입.

```json
{
  "email": "user@example.com",
  "password": "strong-password",
  "displayName": "지원자",
  "termsAgreed": true,
  "aiConsent": true
}
```

응답 `201`.

## POST /auth/login

```json
{
  "email": "user@example.com",
  "password": "strong-password"
}
```

응답 `200`, 세션 쿠키 발급.

## POST /auth/logout

현재 세션 무효화. 응답 `204`.

## GET /auth/me

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "지원자",
  "profileCompleted": false
}
```

## GET /auth/csrf

```json
{
  "headerName": "X-CSRF-TOKEN",
  "parameterName": "_csrf",
  "token": "..."
}
```

## PATCH /account/display-name

```json
{
  "displayName": "지원자"
}
```

## PATCH /account/password

현재 비밀번호 확인 후 변경한다.

```json
{
  "currentPassword": "current-password",
  "newPassword": "new-strong-password"
}
```

## DELETE /account

회원 탈퇴 요청. 현재 비밀번호 재확인 후 세션을 종료하고 비동기 데이터 삭제를 시작한다.

---

# 3. 사용자 프로필

## GET /profile

기본 프로필 조회.

## PUT /profile

```json
{
  "legalName": "홍길동",
  "introduction": "백엔드 개발자 지망생",
  "desiredRoles": ["BACKEND", "AI_SERVICE"],
  "desiredIndustries": ["FINANCE"],
  "desiredLocations": ["SEOUL"],
  "expectedGraduationDate": "2026-08-20",
  "version": 1
}
```

## 학력

- `GET /profile/educations`
- `POST /profile/educations`
- `PUT /profile/educations/{educationId}`
- `DELETE /profile/educations/{educationId}`

POST 예시:

```json
{
  "schoolName": "대학교",
  "major": "컴퓨터공학",
  "degree": "BACHELOR",
  "educationStatus": "EXPECTED_GRADUATION",
  "admissionDate": "2020-03-01",
  "graduationDate": "2026-08-20",
  "gpa": 3.94,
  "gpaScale": 4.5,
  "isPrimary": true
}
```

## 자격증

- `GET /profile/certifications`
- `POST /profile/certifications`
- `PUT /profile/certifications/{certificationId}`
- `DELETE /profile/certifications/{certificationId}`

## 어학 성적

- `GET /profile/language-scores`
- `POST /profile/language-scores`
- `PUT /profile/language-scores/{languageScoreId}`
- `DELETE /profile/language-scores/{languageScoreId}`

## 수상

- `GET /profile/awards`
- `POST /profile/awards`
- `PUT /profile/awards/{awardId}`
- `DELETE /profile/awards/{awardId}`

## 경력

- `GET /profile/careers`
- `POST /profile/careers`
- `PUT /profile/careers/{careerId}`
- `DELETE /profile/careers/{careerId}`

---

# 4. 문서·근거

## POST /documents

`multipart/form-data`

| 필드 | 필수 | 설명 |
|---|---:|---|
| file | Y | PDF/DOCX/TXT |
| documentType | Y | RESUME, PORTFOLIO, CAREER_DESCRIPTION, CERTIFICATE, TRANSCRIPT, OTHER |
| displayName | N | 표시명 |

응답 `202`:

```json
{
  "documentId": "uuid",
  "parseStatus": "UPLOADED",
  "agentRunId": "uuid"
}
```

## GET /documents

Query:

- `documentType`
- `parseStatus`
- `page`
- `size`

## GET /documents/{documentId}

메타데이터와 파싱 상태 조회. 원문 전체는 기본 반환하지 않는다.

## GET /documents/{documentId}/text

추출 텍스트 조회. 사용자 편집 화면에서만 사용.

## PUT /documents/{documentId}/manual-text

파싱 실패·텍스트 부족 시 수동 텍스트 저장 후 재처리.

```json
{
  "text": "..."
}
```

응답 `202` + `agentRunId`.

## POST /documents/{documentId}/reparse

응답 `202`.

## POST /documents/{documentId}/download-url

단기 다운로드 URL 발급.

## DELETE /documents/{documentId}

원본과 파생 데이터 삭제. 응답 `204`.

## GET /profile/evidence

Query:

- `verificationStatus`
- `evidenceCategory`
- `documentId`
- `page`, `size`

## PUT /profile/evidence/{evidenceId}

내용 수정.

```json
{
  "title": "입찰 성능 개선",
  "content": "TPS를 14.35에서 91.58로 개선",
  "metadata": {"before": 14.35, "after": 91.58},
  "version": 1
}
```

## PATCH /profile/evidence/{evidenceId}/verification

```json
{
  "status": "VERIFIED"
}
```

허용: `VERIFIED`, `REJECTED`.

---

# 5. 채용 공고

## POST /jobs

공고 URL 등록. 응답 `202`.

```json
{
  "sourceUrl": "https://company.example/jobs/123",
  "companyName": null,
  "positionName": null,
  "descriptionText": null,
  "deadlineAt": "2026-07-31T14:59:59Z"
}
```

응답:

```json
{
  "jobId": "uuid",
  "status": "IN_PROGRESS",
  "extractionStatus": "QUEUED",
  "agentRunId": "uuid"
}
```

`descriptionText`가 제공되면 URL 추출 실패 시 해당 본문을 사용한다.

## GET /jobs

Query:

- `status=IN_PROGRESS|SUBMITTED|CLOSED`
- `query`
- `deadlineFrom`
- `deadlineTo`
- `sort=createdAt,desc|deadlineAt,asc|updatedAt,desc`
- `page`, `size`

## GET /jobs/{jobId}

공고 상세, 최신 분석 요약, 자기소개서·면접 준비 링크를 반환.

## PUT /jobs/{jobId}

회사·직무·본문·마감일 수정.

```json
{
  "companyName": "회사",
  "title": "신입 개발자 채용",
  "positionName": "백엔드 개발",
  "descriptionText": "...",
  "deadlineAt": "2026-08-01T14:59:59Z",
  "version": 2
}
```

사용자 입력 마감일은 `deadlineSource=USER_ENTERED`.

## PATCH /jobs/{jobId}/status

```json
{
  "status": "SUBMITTED"
}
```

허용 전이:

- `IN_PROGRESS → SUBMITTED`
- `IN_PROGRESS → CLOSED`
- `SUBMITTED → CLOSED`
- `CLOSED → IN_PROGRESS`
- `CLOSED → SUBMITTED`

`SUBMITTED` 최초 진입 시 `submittedAt` 저장. `CLOSED` 전환 시에도 보존.

## POST /jobs/{jobId}/retry-extraction

URL 추출 재실행. 응답 `202`.

## POST /jobs/{jobId}/analysis

공고 분석과 사용자 적합도 분석. 응답 `202`.

```json
{
  "qualityMode": "BALANCED",
  "forceReanalyze": false
}
```

## GET /jobs/{jobId}/analyses

분석 버전 목록.

## GET /jobs/{jobId}/analyses/latest

최신 분석 상세.

## DELETE /jobs/{jobId}

공고 soft delete. 응답 `204`.

---

# 6. 자기소개서

## POST /jobs/{jobId}/cover-letter

해당 공고 자기소개서 생성.

```json
{
  "title": "2026 신입 백엔드 지원서"
}
```

응답 `201`.

## GET /cover-letters

Query: `jobId`, `status`, `page`, `size`.

## GET /cover-letters/{coverLetterId}

문항, 현재 답변 버전, 최신 검증 상태를 반환.

## PUT /cover-letters/{coverLetterId}

제목·상태 수정. `FINALIZED`는 필수 문항 존재와 검증 조건 확인.

## POST /cover-letters/{coverLetterId}/questions

```json
{
  "questionOrder": 1,
  "questionText": "지원 동기와 커리어 계획을 작성해 주세요.",
  "maxLength": 1000,
  "memo": null
}
```

## PUT /cover-letters/{coverLetterId}/questions/{questionId}

문항 수정.

## DELETE /cover-letters/{coverLetterId}/questions/{questionId}

문항 삭제 후 순서 재정렬 가능.

## PATCH /cover-letters/{coverLetterId}/questions/order

```json
{
  "questionIds": ["uuid-1", "uuid-2"]
}
```

## POST /cover-letters/{coverLetterId}/generate

선택 문항 AI 생성. 응답 `202`.

```json
{
  "questionIds": ["uuid"],
  "preferredEvidenceIds": ["uuid"],
  "qualityMode": "BALANCED",
  "avoidExperienceDuplication": true
}
```

## GET /cover-letter-questions/{questionId}/versions

문항 답변 버전 목록.

## POST /cover-letter-questions/{questionId}/versions

사용자 수정본 저장.

```json
{
  "content": "수정된 자기소개서...",
  "sourceType": "USER_EDITED",
  "parentVersionId": "uuid"
}
```

응답 `201`; 새 버전이 current가 된다. 자기소개서가 `FINALIZED`였다면 `DRAFT`로 되돌린다.

## POST /cover-letter-questions/{questionId}/versions/{versionId}/restore

선택 버전을 복원한 새 `RESTORED` 버전 생성.

## POST /cover-letter-answer-versions/{versionId}/verify

응답 `202`.

```json
{
  "qualityMode": "BALANCED"
}
```

## GET /cover-letter-answer-versions/{versionId}/verifications

검증 이력.

## POST /cover-letters/{coverLetterId}/finalize

상태를 `FINALIZED`로 변경. 응답 `200`.

## POST /cover-letters/{coverLetterId}/archive

상태를 `ARCHIVED`로 변경.

---

# 7. 면접 준비와 조사

## POST /jobs/{jobId}/interview-preparations

회사·유사 직무 면접 조사와 질문 세트 생성을 시작. 응답 `202`.

```json
{
  "coverLetterId": "uuid",
  "researchQuality": "BASIC",
  "questionTypes": [
    "RESUME",
    "TECHNICAL",
    "BEHAVIORAL",
    "COMPANY"
  ],
  "questionCount": 20
}
```

응답:

```json
{
  "questionSetId": "uuid",
  "researchRunId": "uuid",
  "agentRunId": "uuid"
}
```

## GET /interview-question-sets

Query: `jobId`, `coverLetterId`, `page`, `size`.

## GET /interview-question-sets/{questionSetId}

질문 세트와 조사 요약.

## GET /research-runs/{researchRunId}

조사 상태와 요약.

## GET /research-runs/{researchRunId}/sources

출처 목록. URL, 제목, 유형, 발행일, 조회일, 스니펫 반환.

## POST /research-runs/{researchRunId}/retry

조사 재시도. 응답 `202`.

## GET /interview-questions/{questionId}

질문 의도, 평가 포인트, 답변 가이드, 꼬리 질문.

## GET /interview-questions/{questionId}/answer-versions

답변 버전 목록.

## POST /interview-questions/{questionId}/answer-versions

```json
{
  "content": "제가 해당 문제를 해결할 때...",
  "sourceType": "USER_EDITED",
  "parentVersionId": null
}
```

## POST /interview-answer-versions/{versionId}/feedback

응답 `202`.

```json
{
  "qualityMode": "BALANCED"
}
```

## GET /interview-answer-versions/{versionId}/feedbacks

피드백 이력.

---

# 8. 대화형 모의 면접

## POST /mock-interview-sessions

```json
{
  "jobId": "uuid",
  "coverLetterId": "uuid",
  "questionSetId": "uuid",
  "interviewType": "TECHNICAL_AND_BEHAVIORAL",
  "difficulty": "NORMAL",
  "targetQuestionCount": 10,
  "feedbackTiming": "END_ONLY",
  "pressureMode": false
}
```

응답 `201`, 상태 `READY`.

## POST /mock-interview-sessions/{sessionId}/start

첫 면접관 질문 생성. 응답 `200`.

```json
{
  "sessionStatus": "IN_PROGRESS",
  "interviewerMessage": {
    "id": "uuid",
    "sequenceNo": 1,
    "role": "INTERVIEWER",
    "content": "자기소개를 해주세요."
  }
}
```

## POST /mock-interview-sessions/{sessionId}/messages

사용자 답변 저장 후 다음 질문 또는 꼬리 질문 반환.

```json
{
  "content": "저는 ..."
}
```

응답:

```json
{
  "userMessageId": "uuid",
  "interviewerMessage": {
    "id": "uuid",
    "sequenceNo": 3,
    "role": "INTERVIEWER",
    "content": "그 성능 개선에서 본인의 역할을 더 설명해 주세요."
  },
  "immediateFeedback": null,
  "sessionStatus": "IN_PROGRESS"
}
```

`feedbackTiming=AFTER_EACH`이면 `immediateFeedback` 포함.

## POST /mock-interview-sessions/{sessionId}/complete

세션을 `COMPLETED`로 전환하고 종합 피드백 생성 작업을 시작한다. 항상 `202 + agentRunId`를 반환한다.

## POST /mock-interview-sessions/{sessionId}/cancel

상태 `CANCELLED`.

## GET /mock-interview-sessions

Query: `jobId`, `status`, `page`, `size`.

## GET /mock-interview-sessions/{sessionId}

세션 설정과 요약.

## GET /mock-interview-sessions/{sessionId}/messages

전체 대화 조회.

## GET /mock-interview-sessions/{sessionId}/feedbacks

메시지별·세션 종합 피드백 조회.

---

# 9. Agent Run

## GET /agent-runs

Query: `workflowType`, `status`, `page`, `size`.

## GET /agent-runs/{agentRunId}

```json
{
  "id": "uuid",
  "workflowType": "COVER_LETTER_GENERATION",
  "status": "RUNNING",
  "currentStep": "FACT_CHECK",
  "progressPercent": 80,
  "actualCostUsd": 0.04,
  "steps": [
    {
      "stepKey": "QUESTION_ANALYSIS",
      "status": "SUCCEEDED",
      "attempt": 1
    }
  ]
}
```

## GET /agent-runs/{agentRunId}/events

SSE Event:

```text
event: progress
data: {"status":"RUNNING","currentStep":"FACT_CHECK","progressPercent":80}
```

## POST /agent-runs/{agentRunId}/retry

`FAILED` 또는 `INTERRUPTED` 실행을 실패 단계부터 재실행. 응답 `202`.

## POST /agent-runs/{agentRunId}/cancel

아직 종료되지 않은 실행 취소 요청.

---

# 10. 주요 상태 코드

| 상황 | HTTP |
|---|---:|
| 생성 완료 | 201 |
| 비동기 작업 접수 | 202 |
| 성공, 본문 없음 | 204 |
| 입력 오류 | 400 |
| 인증 필요 | 401 |
| CSRF/권한 오류 | 403 |
| 소유하지 않은 리소스 | 404 |
| 중복 URL·이메일 | 409 |
| 낙관적 잠금 충돌 | 409 |
| 지원하지 않는 파일 | 415 |
| 파일 크기 초과 | 413 |
| 비용 한도 초과 | 429 |
| 외부 검색·LLM 일시 장애 | 503 |

---

# 11. 멱등성과 중복 방지

다음 POST는 `Idempotency-Key` 헤더를 지원한다.

- `/documents`
- `/jobs`
- `/jobs/{jobId}/analysis`
- `/cover-letters/{id}/generate`
- `/jobs/{jobId}/interview-preparations`
- `/agent-runs/{id}/retry`

같은 사용자·키·요청 본문이면 기존 결과를 반환한다.


---

# 12. 사용자 설정

## GET /settings/ai

```json
{
  "defaultQualityMode": "BALANCED",
  "highQualityEnabled": false,
  "dailyBudgetUsd": 1.00,
  "systemMaxDailyBudgetUsd": 2.00
}
```

## PUT /settings/ai

```json
{
  "defaultQualityMode": "ECONOMY",
  "highQualityEnabled": false,
  "dailyBudgetUsd": 0.50
}
```

사용자 설정은 시스템 상한을 초과할 수 없다.

## GET /settings/privacy

개인정보와 AI 처리 상태를 반환한다.

```json
{
  "termsAgreedAt": "2026-07-17T08:00:00Z",
  "aiConsentAt": "2026-07-17T08:00:00Z",
  "storedDocumentCount": 3,
  "storedDocumentBytes": 10485760,
  "promptBodyLoggingEnabled": false
}
```
