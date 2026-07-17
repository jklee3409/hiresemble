# DB 명세서

- 문서 버전: 1.0
- DBMS: PostgreSQL 18
- Extension: pgvector
- 식별자: UUID
- 시간: `timestamptz`
- 삭제 정책: 사용자 콘텐츠는 기본 `deleted_at` 소프트 삭제, 원본 파일은 명시적 삭제 시 Object Storage에서도 제거
- 상태값 저장: `varchar` + CHECK 제약조건
- JSON 산출물: `jsonb`

---

## 1. 명명·공통 규칙

- 테이블: snake_case 복수형
- PK: `id uuid`
- 사용자 소유 테이블: `user_id uuid not null`
- 시간: `created_at`, `updated_at`
- 낙관적 잠금이 필요한 편집 엔터티: `version bigint`
- 사용자 소유 리소스 조회는 항상 `user_id` 포함
- FK 삭제:
  - 사용자 삭제: 관련 도메인 데이터 cascade
  - 공고 삭제: 자기소개서·면접 데이터는 soft delete 또는 명시적 cascade
  - 원본 문서 삭제: 파생 근거 상태를 source deleted 처리

---

## 2. 논리 상태값

### 공고 상태

```text
IN_PROGRESS
SUBMITTED
CLOSED
```

### 문서 유형

```text
RESUME
PORTFOLIO
CAREER_DESCRIPTION
CERTIFICATE
TRANSCRIPT
OTHER
```

### 문서 파싱 상태

```text
UPLOADED
PARSING
PARSED
NEEDS_MANUAL_TEXT
FAILED
```

### 사용자 근거 검토 상태

```text
PENDING
VERIFIED
REJECTED
SOURCE_DELETED
```

### 자기소개서 상태

```text
DRAFT
FINALIZED
ARCHIVED
```

### 자기소개서 버전 출처

```text
AI_GENERATED
USER_EDITED
AI_REVISED
RESTORED
```

### 검증 상태

```text
PENDING
PASSED
WARNING
FAILED
```

### Agent Run 상태

```text
QUEUED
RUNNING
WAITING_USER
SUCCEEDED
FAILED
CANCELLED
INTERRUPTED
```

### Agent Step 상태

```text
PENDING
RUNNING
SUCCEEDED
FAILED
SKIPPED
```

### 모의 면접 상태

```text
READY
IN_PROGRESS
COMPLETED
CANCELLED
```

---

# 3. 사용자·프로필

## 3.1 users

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | uuid | PK | 사용자 |
| email | varchar(320) | NOT NULL, UNIQUE | 소문자 정규화 |
| password_hash | varchar(255) | NOT NULL | BCrypt |
| display_name | varchar(100) | NOT NULL | 표시 이름 |
| role | varchar(30) | NOT NULL, CHECK USER | MVP 사용자 역할 |
| status | varchar(30) | NOT NULL | ACTIVE, LOCKED, WITHDRAWN |
| terms_agreed_at | timestamptz | NOT NULL | 약관 동의 |
| ai_consent_at | timestamptz | NOT NULL | AI 처리 동의 |
| last_login_at | timestamptz | NULL | 최근 로그인 |
| created_at | timestamptz | NOT NULL | 생성 |
| updated_at | timestamptz | NOT NULL | 수정 |
| deleted_at | timestamptz | NULL | 탈퇴 |

인덱스: `lower(email)` unique.

## 3.2 user_profiles

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| user_id | uuid | PK, FK users | 사용자 |
| legal_name | varchar(100) | NULL | 선택 입력 |
| introduction | text | NULL | 간단 소개 |
| desired_roles | jsonb | NOT NULL DEFAULT [] | 희망 직무 배열 |
| desired_industries | jsonb | NOT NULL DEFAULT [] | 희망 산업 |
| desired_locations | jsonb | NOT NULL DEFAULT [] | 희망 지역 |
| expected_graduation_date | date | NULL | 졸업 예정일 |
| profile_completed | boolean | NOT NULL DEFAULT false | 필수 프로필 완료 |
| created_at | timestamptz | NOT NULL | 생성 |
| updated_at | timestamptz | NOT NULL | 수정 |
| version | bigint | NOT NULL DEFAULT 0 | 낙관적 잠금 |

## 3.3 educations

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users, NOT NULL |
| school_name | varchar(200) | NOT NULL |
| major | varchar(200) | NULL |
| degree | varchar(100) | NULL |
| education_status | varchar(30) | NOT NULL |
| admission_date | date | NULL |
| graduation_date | date | NULL |
| gpa | numeric(5,2) | NULL |
| gpa_scale | numeric(5,2) | NULL |
| is_primary | boolean | NOT NULL DEFAULT false |
| description | text | NULL |
| created_at / updated_at | timestamptz | NOT NULL |

제약: `graduation_date >= admission_date`; 사용자당 `is_primary=true` 최대 1개 partial unique.

## 3.4 certifications

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| name | varchar(200) | NOT NULL |
| issuer | varchar(200) | NULL |
| credential_number | varchar(200) | NULL |
| acquired_date | date | NULL |
| expires_at | date | NULL |
| description | text | NULL |
| evidence_document_id | uuid | FK documents, NULL |
| created_at / updated_at | timestamptz | NOT NULL |

## 3.5 language_scores

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| test_name | varchar(100) | NOT NULL |
| score | varchar(100) | NOT NULL |
| grade | varchar(100) | NULL |
| tested_at | date | NULL |
| expires_at | date | NULL |
| evidence_document_id | uuid | FK documents, NULL |
| created_at / updated_at | timestamptz | NOT NULL |

## 3.6 awards

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| name | varchar(200) | NOT NULL |
| organizer | varchar(200) | NULL |
| awarded_at | date | NULL |
| description | text | NULL |
| evidence_document_id | uuid | FK documents, NULL |
| created_at / updated_at | timestamptz | NOT NULL |

## 3.7 careers

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| organization | varchar(200) | NOT NULL |
| position | varchar(200) | NULL |
| employment_type | varchar(50) | NULL |
| started_at | date | NULL |
| ended_at | date | NULL |
| is_current | boolean | NOT NULL DEFAULT false |
| responsibilities | text | NULL |
| achievements | text | NULL |
| created_at / updated_at | timestamptz | NOT NULL |

제약: 재직 중이 아니면 `ended_at >= started_at`.

---

# 4. 문서·근거

## 4.1 documents

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | uuid | PK | 문서 |
| user_id | uuid | FK users | 소유자 |
| document_type | varchar(40) | CHECK | 문서 유형 |
| original_filename | varchar(255) | NOT NULL | 표시명 |
| storage_key | varchar(500) | NOT NULL, UNIQUE | Object key |
| mime_type | varchar(150) | NOT NULL | 탐지 MIME |
| file_size | bigint | NOT NULL | byte |
| checksum_sha256 | char(64) | NOT NULL | 중복 확인 |
| parse_status | varchar(40) | NOT NULL | 파싱 상태 |
| parse_error_code | varchar(100) | NULL | 오류 코드 |
| manual_text_provided | boolean | NOT NULL DEFAULT false | 수동 보완 |
| uploaded_at | timestamptz | NOT NULL | 업로드 |
| updated_at | timestamptz | NOT NULL | 수정 |
| deleted_at | timestamptz | NULL | 삭제 |

인덱스: `(user_id, document_type)`, `(user_id, checksum_sha256)`.

## 4.2 document_texts

| 컬럼 | 타입 | 제약 |
|---|---|---|
| document_id | uuid | PK, FK documents ON DELETE CASCADE |
| extracted_text | text | NULL |
| masked_text | text | NULL |
| page_count | integer | NULL |
| character_count | integer | NOT NULL DEFAULT 0 |
| parser_name | varchar(100) | NULL |
| parser_version | varchar(50) | NULL |
| parsed_at | timestamptz | NULL |
| updated_at | timestamptz | NOT NULL |

## 4.3 document_chunks

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| document_id | uuid | FK documents ON DELETE CASCADE |
| chunk_index | integer | NOT NULL |
| page_from | integer | NULL |
| page_to | integer | NULL |
| content | text | NOT NULL |
| masked_content | text | NOT NULL |
| token_count | integer | NULL |
| embedding | vector | NULL |
| embedding_model | varchar(150) | NULL |
| embedding_dimension | integer | NULL |
| metadata | jsonb | NOT NULL DEFAULT {} |
| created_at | timestamptz | NOT NULL |

Unique: `(document_id, chunk_index)`.
인덱스: `(user_id, document_id)`, 필요 시 embedding HNSW.

## 4.4 profile_evidence

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | uuid | PK | 근거 |
| user_id | uuid | FK users | 소유자 |
| source_type | varchar(50) | NOT NULL | EDUCATION, CERTIFICATION, LANGUAGE_SCORE, AWARD, CAREER, DOCUMENT_CHUNK, MANUAL |
| source_entity_id | uuid | NULL | 원본 엔터티 ID |
| document_id | uuid | FK documents, NULL | 문서 기반일 때 |
| evidence_category | varchar(80) | NOT NULL | SKILL, ROLE, ACTION, RESULT 등 |
| title | varchar(250) | NOT NULL | 제목 |
| content | text | NOT NULL | 근거 내용 |
| metadata | jsonb | NOT NULL DEFAULT {} | 수치·기간·기술 |
| confidence | numeric(4,3) | NULL | AI 추출 신뢰도 |
| verification_status | varchar(30) | NOT NULL | PENDING, VERIFIED, REJECTED, SOURCE_DELETED |
| verified_at | timestamptz | NULL | 승인 시각 |
| created_at / updated_at | timestamptz | NOT NULL | 시간 |
| version | bigint | NOT NULL DEFAULT 0 | 낙관적 잠금 |

인덱스: `(user_id, verification_status, evidence_category)`.

---

# 5. 회사·공고

## 5.1 companies

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| normalized_name | varchar(200) | NOT NULL |
| display_name | varchar(200) | NOT NULL |
| official_website | varchar(500) | NULL |
| created_at / updated_at | timestamptz | NOT NULL |

Unique: `lower(normalized_name)`.

## 5.2 job_postings

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | uuid | PK | 공고 |
| user_id | uuid | FK users | 사용자별 저장 공고 |
| company_id | uuid | FK companies, NULL | 회사 |
| source_url | varchar(2000) | NOT NULL | 입력 URL |
| canonical_url | varchar(2000) | NULL | 정규화 URL |
| title | varchar(300) | NULL | 공고명 |
| position_name | varchar(300) | NULL | 직무 |
| role_category | varchar(100) | NULL | 백엔드 등 |
| employment_type | varchar(100) | NULL | 신입/인턴 등 |
| location | varchar(300) | NULL | 근무지 |
| description_text | text | NULL | 정제 본문 |
| deadline_at | timestamptz | NULL | 마감 |
| deadline_source | varchar(30) | NOT NULL | USER_ENTERED, AUTO_EXTRACTED, UNKNOWN |
| deadline_confidence | numeric(4,3) | NULL | 자동 추출 신뢰도 |
| extraction_status | varchar(40) | NOT NULL | QUEUED, EXTRACTING, EXTRACTED, NEEDS_MANUAL_INPUT, FAILED |
| status | varchar(30) | NOT NULL | IN_PROGRESS, SUBMITTED, CLOSED |
| submitted_at | timestamptz | NULL | 제출 이력 |
| closed_at | timestamptz | NULL | 종료 시각 |
| closed_reason | varchar(50) | NULL | DEADLINE_PASSED, USER_CLOSED, URL_INACTIVE |
| content_hash | char(64) | NULL | 분석 캐시 |
| created_at / updated_at | timestamptz | NOT NULL | 시간 |
| deleted_at | timestamptz | NULL | 삭제 |
| version | bigint | NOT NULL DEFAULT 0 | 낙관적 잠금 |

Unique: `(user_id, canonical_url)` where deleted_at is null.
인덱스: `(user_id, status, deadline_at)`, `(status, deadline_at)`.

## 5.3 job_status_history

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| job_posting_id | uuid | FK job_postings |
| user_id | uuid | FK users |
| from_status | varchar(30) | NULL |
| to_status | varchar(30) | NOT NULL |
| reason | varchar(100) | NOT NULL |
| changed_by | varchar(30) | NOT NULL | USER, SCHEDULER, SYSTEM |
| changed_at | timestamptz | NOT NULL |

## 5.4 job_analyses

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| job_posting_id | uuid | FK job_postings |
| analysis_version | integer | NOT NULL |
| job_content_hash | char(64) | NOT NULL |
| profile_snapshot_hash | char(64) | NOT NULL |
| eligibility | varchar(30) | NOT NULL | ELIGIBLE, CONDITIONAL, INELIGIBLE, UNKNOWN |
| fit_score | numeric(5,2) | NULL |
| required_qualifications | jsonb | NOT NULL DEFAULT [] |
| preferred_qualifications | jsonb | NOT NULL DEFAULT [] |
| responsibilities | jsonb | NOT NULL DEFAULT [] |
| matched_evidence | jsonb | NOT NULL DEFAULT [] |
| strengths | jsonb | NOT NULL DEFAULT [] |
| gaps | jsonb | NOT NULL DEFAULT [] |
| analysis_summary | text | NULL |
| agent_run_id | uuid | FK agent_runs, NULL |
| created_at | timestamptz | NOT NULL |

Unique: `(job_posting_id, analysis_version)`.

---

# 6. 자기소개서

## 6.1 cover_letters

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| job_posting_id | uuid | FK job_postings |
| title | varchar(300) | NOT NULL |
| status | varchar(30) | NOT NULL | DRAFT, FINALIZED, ARCHIVED |
| finalized_at | timestamptz | NULL |
| created_at / updated_at | timestamptz | NOT NULL |
| deleted_at | timestamptz | NULL |
| version | bigint | NOT NULL DEFAULT 0 |

Unique 권장: 한 공고당 active cover letter 1개, 필요 시 archived 후 새 생성.

## 6.2 cover_letter_questions

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| cover_letter_id | uuid | FK cover_letters ON DELETE CASCADE |
| question_order | integer | NOT NULL |
| question_text | text | NOT NULL |
| max_length | integer | NULL |
| memo | text | NULL |
| created_at / updated_at | timestamptz | NOT NULL |

Unique: `(cover_letter_id, question_order)`.

## 6.3 cover_letter_answer_versions

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| question_id | uuid | FK cover_letter_questions ON DELETE CASCADE |
| parent_version_id | uuid | FK self, NULL |
| version_no | integer | NOT NULL |
| content | text | NOT NULL |
| character_count | integer | NOT NULL |
| source_type | varchar(30) | NOT NULL |
| prompt_version | varchar(50) | NULL |
| model_name | varchar(150) | NULL |
| is_current | boolean | NOT NULL DEFAULT false |
| created_by | varchar(30) | NOT NULL | USER, AI |
| created_at | timestamptz | NOT NULL |

Unique: `(question_id, version_no)`.
Partial unique: 질문당 `is_current=true` 한 개.

## 6.4 cover_letter_evidence_links

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| answer_version_id | uuid | FK cover_letter_answer_versions ON DELETE CASCADE |
| profile_evidence_id | uuid | FK profile_evidence |
| claim_text | text | NULL |
| usage_type | varchar(30) | NOT NULL | PRIMARY, SUPPORTING, VERIFIED_CLAIM |
| created_at | timestamptz | NOT NULL |

Unique: `(answer_version_id, profile_evidence_id, usage_type)`.

## 6.5 cover_letter_verifications

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| answer_version_id | uuid | FK cover_letter_answer_versions |
| status | varchar(30) | NOT NULL | PENDING, PASSED, WARNING, FAILED |
| issues | jsonb | NOT NULL DEFAULT [] |
| suggestions | jsonb | NOT NULL DEFAULT [] |
| verified_claims | jsonb | NOT NULL DEFAULT [] |
| prompt_version | varchar(50) | NULL |
| model_name | varchar(150) | NULL |
| agent_run_id | uuid | FK agent_runs, NULL |
| created_at | timestamptz | NOT NULL |

인덱스: `(answer_version_id, created_at desc)`.

---

# 7. 외부 조사

## 7.1 research_runs

회사 조사와 면접 정보 조사를 공통 관리한다.

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| job_posting_id | uuid | FK job_postings |
| cover_letter_id | uuid | FK cover_letters, NULL |
| research_type | varchar(30) | NOT NULL | COMPANY, INTERVIEW |
| query | text | NOT NULL |
| quality_mode | varchar(30) | NOT NULL | BASIC, ADVANCED |
| status | varchar(30) | NOT NULL | QUEUED, RUNNING, SUCCEEDED, FAILED |
| summary | text | NULL |
| report_json | jsonb | NOT NULL DEFAULT {} |
| agent_run_id | uuid | FK agent_runs, NULL |
| created_at / completed_at | timestamptz | NULL |

## 7.2 research_sources

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| research_run_id | uuid | FK research_runs ON DELETE CASCADE |
| source_url | varchar(2000) | NOT NULL |
| title | varchar(500) | NULL |
| source_type | varchar(30) | NOT NULL |
| published_at | timestamptz | NULL |
| retrieved_at | timestamptz | NOT NULL |
| snippet | text | NULL |
| reliability_score | numeric(4,3) | NULL |
| provider_rank | integer | NULL |
| content_hash | char(64) | NULL |

Unique: `(research_run_id, source_url)`.

---

# 8. 면접 준비

## 8.1 interview_question_sets

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| job_posting_id | uuid | FK job_postings |
| cover_letter_id | uuid | FK cover_letters |
| research_run_id | uuid | FK research_runs, NULL |
| title | varchar(300) | NOT NULL |
| generation_config | jsonb | NOT NULL DEFAULT {} |
| created_at / updated_at | timestamptz | NOT NULL |

## 8.2 interview_questions

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| question_set_id | uuid | FK interview_question_sets ON DELETE CASCADE |
| question_order | integer | NOT NULL |
| question_type | varchar(40) | NOT NULL |
| question_text | text | NOT NULL |
| intent | text | NULL |
| evaluation_points | jsonb | NOT NULL DEFAULT [] |
| answer_guide | text | NULL |
| follow_up_questions | jsonb | NOT NULL DEFAULT [] |
| related_evidence_ids | jsonb | NOT NULL DEFAULT [] |
| source_based | boolean | NOT NULL DEFAULT false |
| created_at | timestamptz | NOT NULL |

Unique: `(question_set_id, question_order)`.

## 8.3 interview_answer_versions

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| interview_question_id | uuid | FK interview_questions ON DELETE CASCADE |
| parent_version_id | uuid | FK self, NULL |
| version_no | integer | NOT NULL |
| content | text | NOT NULL |
| source_type | varchar(30) | NOT NULL | USER_EDITED, AI_GENERATED, AI_REVISED, RESTORED |
| is_current | boolean | NOT NULL DEFAULT false |
| created_at | timestamptz | NOT NULL |

Unique: `(interview_question_id, version_no)`.
Partial unique: 질문당 current 한 개.

## 8.4 interview_answer_feedbacks

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| answer_version_id | uuid | FK interview_answer_versions |
| scores | jsonb | NOT NULL |
| strengths | jsonb | NOT NULL DEFAULT [] |
| weaknesses | jsonb | NOT NULL DEFAULT [] |
| suggestions | jsonb | NOT NULL DEFAULT [] |
| revised_example | text | NULL |
| model_name | varchar(150) | NULL |
| agent_run_id | uuid | FK agent_runs, NULL |
| created_at | timestamptz | NOT NULL |

## 8.5 mock_interview_sessions

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| job_posting_id | uuid | FK job_postings |
| cover_letter_id | uuid | FK cover_letters |
| question_set_id | uuid | FK interview_question_sets, NULL |
| status | varchar(30) | NOT NULL | READY, IN_PROGRESS, COMPLETED, CANCELLED |
| interview_type | varchar(40) | NOT NULL |
| difficulty | varchar(30) | NOT NULL |
| target_question_count | integer | NOT NULL |
| feedback_timing | varchar(30) | NOT NULL | AFTER_EACH, END_ONLY |
| pressure_mode | boolean | NOT NULL DEFAULT false |
| started_at | timestamptz | NULL |
| completed_at | timestamptz | NULL |
| created_at | timestamptz | NOT NULL |

## 8.6 mock_interview_messages

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| session_id | uuid | FK mock_interview_sessions ON DELETE CASCADE |
| sequence_no | integer | NOT NULL |
| role | varchar(20) | NOT NULL | INTERVIEWER, USER, SYSTEM |
| content | text | NOT NULL |
| related_question_id | uuid | FK interview_questions, NULL |
| model_name | varchar(150) | NULL |
| created_at | timestamptz | NOT NULL |

Unique: `(session_id, sequence_no)`.

## 8.7 mock_interview_feedbacks

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| session_id | uuid | FK mock_interview_sessions ON DELETE CASCADE |
| message_id | uuid | FK mock_interview_messages, NULL |
| feedback_scope | varchar(20) | NOT NULL | MESSAGE, SESSION |
| scores | jsonb | NOT NULL DEFAULT {} |
| strengths | jsonb | NOT NULL DEFAULT [] |
| weaknesses | jsonb | NOT NULL DEFAULT [] |
| recommendations | jsonb | NOT NULL DEFAULT [] |
| summary | text | NULL |
| model_name | varchar(150) | NULL |
| created_at | timestamptz | NOT NULL |

세션당 `feedback_scope=SESSION` 최신 1개를 UI 기본 표시.

---

# 9. AI 실행·비용

## 9.1 agent_runs

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| workflow_type | varchar(60) | NOT NULL |
| resource_type | varchar(50) | NULL |
| resource_id | uuid | NULL |
| status | varchar(30) | NOT NULL |
| current_step | varchar(100) | NULL |
| progress_percent | integer | NOT NULL DEFAULT 0 |
| workflow_version | varchar(50) | NOT NULL |
| input_hash | char(64) | NULL |
| estimated_cost_usd | numeric(12,6) | NULL |
| actual_cost_usd | numeric(12,6) | NOT NULL DEFAULT 0 |
| error_code | varchar(100) | NULL |
| error_message_safe | text | NULL |
| queued_at | timestamptz | NOT NULL |
| started_at | timestamptz | NULL |
| completed_at | timestamptz | NULL |
| updated_at | timestamptz | NOT NULL |

인덱스: `(user_id, status, queued_at desc)`.

## 9.2 agent_steps

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| agent_run_id | uuid | FK agent_runs ON DELETE CASCADE |
| step_key | varchar(100) | NOT NULL |
| step_order | integer | NOT NULL |
| agent_name | varchar(150) | NULL |
| status | varchar(30) | NOT NULL |
| attempt | integer | NOT NULL DEFAULT 1 |
| input_hash | char(64) | NULL |
| input_refs | jsonb | NOT NULL DEFAULT {} |
| output_json | jsonb | NOT NULL DEFAULT {} |
| model_policy | varchar(30) | NULL |
| model_name | varchar(150) | NULL |
| prompt_version | varchar(50) | NULL |
| error_code | varchar(100) | NULL |
| error_message_safe | text | NULL |
| started_at | timestamptz | NULL |
| completed_at | timestamptz | NULL |

Unique: `(agent_run_id, step_key, attempt)`.

## 9.3 ai_model_policies

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| task_type | varchar(60) | NOT NULL |
| quality_mode | varchar(30) | NOT NULL |
| provider | varchar(50) | NOT NULL |
| model_name | varchar(150) | NOT NULL |
| max_input_tokens | integer | NULL |
| max_output_tokens | integer | NULL |
| max_cost_usd | numeric(12,6) | NULL |
| temperature | numeric(4,3) | NULL |
| enabled | boolean | NOT NULL DEFAULT true |
| created_at / updated_at | timestamptz | NOT NULL |

Unique: `(task_type, quality_mode)`.

## 9.4 user_ai_preferences

사용자가 시스템 상한 이하에서 기본 품질과 개인 비용 한도를 선택한다.

| 컬럼 | 타입 | 제약 |
|---|---|---|
| user_id | uuid | PK, FK users |
| default_quality_mode | varchar(30) | NOT NULL | ECONOMY, BALANCED |
| high_quality_enabled | boolean | NOT NULL DEFAULT false |
| daily_budget_usd | numeric(12,6) | NULL | 시스템 최대값 이하 |
| created_at / updated_at | timestamptz | NOT NULL |

`HIGH_QUALITY` 실행은 `high_quality_enabled=true`이고 개별 요청에서 다시 선택한 경우에만 허용한다.

## 9.5 ai_usage_logs

| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users |
| agent_run_id | uuid | FK agent_runs |
| agent_step_id | uuid | FK agent_steps |
| provider | varchar(50) | NOT NULL |
| model_name | varchar(150) | NOT NULL |
| input_tokens | integer | NOT NULL DEFAULT 0 |
| cached_input_tokens | integer | NOT NULL DEFAULT 0 |
| output_tokens | integer | NOT NULL DEFAULT 0 |
| estimated_cost_usd | numeric(12,6) | NOT NULL DEFAULT 0 |
| duration_ms | bigint | NULL |
| created_at | timestamptz | NOT NULL |

인덱스: `(user_id, created_at)`, `(agent_run_id)`.

---

# 10. Spring Session 관리 테이블

Spring Session JDBC가 관리한다.

- `spring_session`
- `spring_session_attributes`

도메인 API는 이 테이블을 직접 조회하지 않는다.

---

# 11. 주요 관계

```text
users
├─ user_profiles
├─ educations / certifications / language_scores / awards / careers
├─ documents ─ document_texts / document_chunks ─ profile_evidence
├─ job_postings ─ job_analyses
│   ├─ cover_letters ─ questions ─ answer_versions ─ verifications
│   ├─ research_runs ─ research_sources
│   ├─ interview_question_sets ─ questions ─ answers ─ feedbacks
│   └─ mock_interview_sessions ─ messages ─ feedbacks
├─ user_ai_preferences
└─ agent_runs ─ agent_steps ─ ai_usage_logs
```

---

# 12. 트랜잭션 규칙

1. 공고 상태 변경과 `job_status_history` 생성은 한 트랜잭션.
2. 자기소개서 새 버전 생성과 기존 `is_current=false` 처리는 한 트랜잭션.
3. 면접 답변 새 버전도 동일 규칙.
4. Agent Step 성공 기록 후 도메인 결과 반영은 멱등 키로 중복 방지.
5. 문서 Object 업로드 성공 후 DB 저장 실패 시 보상 삭제.
6. DB 문서 삭제 성공 후 Object 삭제 실패 시 재시도 Outbox 대상 기록을 권장한다.
7. 사용자 소유 FK와 API 인증 사용자 ID가 일치하지 않으면 404로 처리한다.

---

# 13. 데이터 보존

- 탈퇴 시 로그인 차단 후 비동기 물리 삭제 가능
- AI 사용량 로그는 본문 없이 비용·메타데이터만 보존
- 검색 출처는 URL·제목·스니펫만 저장
- 원본 문서는 사용자가 삭제 가능
- 자기소개서와 면접 답변은 사용자가 직접 삭제할 때까지 보존
