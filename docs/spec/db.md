# DB 명세서

- 문서 버전: 1.1 (P0 승인 기준선)
- 기준일: 2026-07-18
- DBMS: PostgreSQL 18 + pgvector
- 식별자: UUID
- 시간: `timestamptz` UTC
- 상태: `varchar` + 명시적 `CHECK`
- JSON 산출물: `jsonb`

이 문서는 향후 Flyway가 구현해야 할 목표 데이터 계약이다. 현재 적용된 migration은 `V1__enable_extensions.sql`뿐이며 이 문서 변경은 table이나 migration 구현 완료를 뜻하지 않는다.

## 1. 공통 무결성·소유권

- table은 snake_case 복수형, PK는 `id uuid`다.
- 축약 schema에서 `NULL`로 표시한 column만 nullable이고 나머지 나열된 domain column은 `NOT NULL`이다. `timestamps`는 `created_at,updated_at timestamptz NOT NULL`, 별도 `created_at`도 NOT NULL을 뜻한다. lifecycle timestamp와 terminal output은 각 table에 nullability를 명시한다.
- `companies`, immutable AI policy/price catalog, Spring Session framework table과 독립 `account_deletion_tasks`만 전역·framework 예외다.
- 그 밖의 모든 사용자 콘텐츠 row는 `user_id uuid NOT NULL`과 `UNIQUE(user_id,id)`를 가진다.
- aggregate child도 user ID를 중복 저장하며 FK는 `(user_id,parent_id) → parent(user_id,id)`다.
- 중요한 교차 참조(profile↔document, cover↔job, answer↔evidence, research↔job/cover, question set↔job/cover/research, mock↔job/cover/question set, usage↔run/step/turn)도 복합 FK로 owner 일치를 강제한다.
- `profile_evidence(source_type,source_entity_id)`의 polymorphic source만 FK 예외다. 저장 transaction이 type별 `(user_id,id,deleted_at)`을 조회한다.
- JSON의 evidence/source ID는 snapshot 표시용이고 authoritative provenance는 typed link table이다.
- 일반 API delete는 soft delete 또는 명시적 lifecycle command다. owner FK cascade는 회원 최종 purge에서만 사용한다.
- 낙관적 잠금 aggregate는 `version bigint NOT NULL DEFAULT 0`, append-only row는 immutable이다.
- 모든 금액은 `numeric(12,6) CHECK >=0` USD, fit score는 `numeric(5,2)`다.
- 원문, 비밀번호, Session/token, 전체 prompt/response는 idempotency·usage·run metadata에 복사하지 않는다.
- PostgreSQL RLS는 MVP에서 사용하지 않으며 복합 FK와 owner-scoped query를 함께 사용한다.

## 2. Canonical CHECK 값과 상태 전이

| 축                                         | CHECK 값                                                                                                                                                                                                |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `users.status`                             | `ACTIVE`, `LOCKED`, `WITHDRAWN`                                                                                                                                                                         |
| `job_postings.status`                      | `IN_PROGRESS`, `SUBMITTED`, `CLOSED`                                                                                                                                                                    |
| `job_postings.extraction_status`           | `QUEUED`, `EXTRACTING`, `EXTRACTED`, `MANUAL_INPUT_PROVIDED`, `NEEDS_MANUAL_INPUT`, `FAILED`                                                                                                            |
| `documents.parse_status`                   | `UPLOADED`, `PARSING`, `PARSED`, `NEEDS_MANUAL_TEXT`, `FAILED`                                                                                                                                          |
| `documents.evidence_extraction_status`     | `NOT_STARTED`, `QUEUED`, `EXTRACTING`, `SUCCEEDED`, `FAILED`                                                                                                                                            |
| `profile_evidence.verification_status`     | `PENDING`, `VERIFIED`, `REJECTED`, `SOURCE_DELETED`                                                                                                                                                     |
| `cover_letters.status`                     | `DRAFT`, `FINALIZED`, `ARCHIVED`                                                                                                                                                                        |
| `cover_letter_answer_versions.source_type` | `AI_GENERATED`, `USER_EDITED`, `AI_REVISED`, `RESTORED`                                                                                                                                                 |
| `interview_answer_versions.source_type`    | `USER_EDITED`                                                                                                                                                                                           |
| `cover_letter_verifications.status`        | `PENDING`, `PASSED`, `WARNING`, `FAILED`                                                                                                                                                                |
| `research_runs.status`                     | `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`                                                                                                                                                 |
| `research_runs.source_coverage`            | 실행 중 `NULL`, terminal `SUFFICIENT`, `LIMITED`, `NONE`                                                                                                                                                |
| `mock_interview_sessions.status`           | `READY`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`                                                                                                                                                        |
| `mock_interview_sessions.feedback_status`  | `NOT_REQUESTED`, `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`                                                                                                                                |
| `agent_runs.status`                        | `QUEUED`, `RUNNING`, `WAITING_USER`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `INTERRUPTED`                                                                                                                  |
| `agent_steps.status`                       | `PENDING`, `RUNNING`, `WAITING_USER`, `SUCCEEDED`, `FAILED`, `SKIPPED`, `REUSED`, `CANCELLED`, `INTERRUPTED`                                                                                            |
| `research_runs.research_quality`           | `BASIC`, `ADVANCED`                                                                                                                                                                                     |
| `interview_questions.question_type`        | `COVER_LETTER`, `RESUME`, `PORTFOLIO`, `TECHNICAL`, `PROJECT_DEEP_DIVE`, `BEHAVIORAL`, `COMPANY_MOTIVATION`, `FOLLOW_UP`                                                                                |
| 공개 품질                                  | `ECONOMY`, `BALANCED`, `HIGH_QUALITY`                                                                                                                                                                   |
| 내부 tier                                  | `LOW_COST`, `BALANCED`, `HIGH_QUALITY`                                                                                                                                                                  |
| `agent_runs.workflow_type`                 | `DOCUMENT_INGESTION`, `JOB_POSTING_EXTRACTION`, `JOB_ANALYSIS`, `COVER_LETTER_GENERATION`, `COVER_LETTER_VERIFICATION`, `INTERVIEW_PREPARATION`, `INTERVIEW_ANSWER_FEEDBACK`, `MOCK_INTERVIEW_FEEDBACK` |
| `documents.document_type`                  | `RESUME`, `PORTFOLIO`, `CAREER_DESCRIPTION`, `CERTIFICATE`, `TRANSCRIPT`, `OTHER`                                                                                                                       |
| `profile_evidence.source_type`             | `EDUCATION`, `CERTIFICATION`, `LANGUAGE_SCORE`, `AWARD`, `CAREER`, `DOCUMENT_CHUNK`, `MANUAL`                                                                                                           |
| `job_postings.deadline_source`             | `USER_ENTERED`, `AUTO_EXTRACTED`, `UNKNOWN`                                                                                                                                                             |
| `job_postings.closed_reason`               | `DEADLINE_PASSED`, `USER_CLOSED`, `URL_INACTIVE`                                                                                                                                                        |
| `job_postings.description_source`          | `AUTO_EXTRACTED`, `USER_ENTERED`                                                                                                                                                                        |
| `job_analyses.eligibility`                 | `ELIGIBLE`, `CONDITIONAL`, `INELIGIBLE`, `UNKNOWN`                                                                                                                                                      |
| `job_analysis_score_criteria.category`     | `REQUIRED_QUALIFICATION`, `CORE_RESPONSIBILITY_OR_SKILL`, `PREFERRED_QUALIFICATION`, `RELATED_EXPERIENCE_OR_DOMAIN`, `EDUCATION_CERTIFICATION_LANGUAGE`                                                 |
| `job_analysis_score_criteria.match_level`  | `MATCHED`, `PARTIAL`, `MISSING`, `UNKNOWN`                                                                                                                                                              |
| `research_topics.topic`                    | `COMPANY`, `INTERVIEW_PROCESS`, `ROLE_TECHNICAL`                                                                                                                                                        |
| `research_sources.source_type`             | `OFFICIAL`, `TECH_BLOG`, `NEWS`, `INTERVIEW_REVIEW`, `COMMUNITY`, `OTHER`                                                                                                                               |
| `mock_interview_sessions.interview_type`   | `TECHNICAL`, `BEHAVIORAL`, `TECHNICAL_AND_BEHAVIORAL`                                                                                                                                                   |
| `mock_interview_sessions.difficulty`       | `EASY`, `NORMAL`, `HARD`                                                                                                                                                                                |
| `mock_interview_sessions.feedback_timing`  | `AFTER_EACH`, `END_ONLY`                                                                                                                                                                                |
| `mock_interview_messages.role`             | `USER`, `INTERVIEWER`                                                                                                                                                                                   |
| `mock_interview_turns.status`              | `PENDING`, `COMPLETED`, `FAILED`                                                                                                                                                                        |
| `idempotency_records.state`                | `IN_PROGRESS`, `COMPLETED`                                                                                                                                                                              |
| `object_deletion_outbox.status`            | `PENDING`, `PROCESSING`, `SUCCEEDED`, `DEAD`                                                                                                                                                            |
| `ai_budget_reservations.status`            | `RESERVED`, `SETTLED`, `RELEASED`, `EXPIRED`                                                                                                                                                            |
| `ai_usage_records.usage_type`              | `CHAT`, `EMBEDDING`, `SEARCH`                                                                                                                                                                           |
| `account_deletion_tasks.status`            | `QUEUED`, `RUNNING`, `RETRY_WAIT`, `SUCCEEDED`, `DEAD`                                                                                                                                                  |
| `educations.education_status`              | `ENROLLED`, `LEAVE_OF_ABSENCE`, `EXPECTED_GRADUATION`, `GRADUATED`, `WITHDRAWN`                                                                                                                         |
| `cover_letter_answer_versions.created_by`  | `USER`, `AI`                                                                                                                                                                                            |

표의 scalar enum column은 명시적 `CHECK`를 갖는다. JSON 안의 `VerificationIssueCode`, `IssueSeverity`, `MockFeedbackCategory`는 versioned JSON schema와 domain validation으로 같은 값을 강제한다. `OutdatedReason`, `RequiredUserActionType`, `ProfileCompletionItem`은 저장 enum이 아닌 계산 projection이다.

주요 전이:

- 공고 업무: `IN_PROGRESS→SUBMITTED|CLOSED`, `SUBMITTED→CLOSED`, `CLOSED→IN_PROGRESS|SUBMITTED`.
- 공고 추출: `QUEUED→EXTRACTING→EXTRACTED|NEEDS_MANUAL_INPUT|FAILED`; 수동 본문은 `MANUAL_INPUT_PROVIDED`; 명시 retry는 `FAILED→QUEUED`.
- 문서 parse: `UPLOADED→PARSING→PARSED|NEEDS_MANUAL_TEXT|FAILED`; explicit manual/reparse만 새 revision을 `PARSING`으로 전환한다.
- evidence 추출: `NOT_STARTED→QUEUED→EXTRACTING→SUCCEEDED|FAILED`.
- 자기소개서: `DRAFT→FINALIZED|ARCHIVED`, `FINALIZED→DRAFT|ARCHIVED`, active가 없을 때 `ARCHIVED→DRAFT`.
- evidence 검토: `PENDING↔VERIFIED|REJECTED`, `VERIFIED↔REJECTED`, 모든 활성 상태에서 원천 삭제 시 `SOURCE_DELETED`.
- 자기소개서 검증: 한 row는 `PENDING→PASSED|WARNING|FAILED`; 재검증은 새 row다.
- 조사: `QUEUED→RUNNING→SUCCEEDED|FAILED|CANCELLED`; 출처 부족은 `SUCCEEDED+LIMITED|NONE`이다.
- 모의 면접: `READY→IN_PROGRESS|CANCELLED`, `IN_PROGRESS→COMPLETED|CANCELLED`. feedback은 `NOT_REQUESTED→QUEUED→RUNNING→SUCCEEDED|FAILED|CANCELLED`이고 FAILED retry는 새 run projection이다.
- Agent Run: `QUEUED→RUNNING|CANCELLED`, `RUNNING→WAITING_USER|SUCCEEDED|FAILED|CANCELLED|INTERRUPTED`, `WAITING_USER→QUEUED|CANCELLED`. terminal row는 다시 열지 않는다.
- Agent Step: `PENDING→RUNNING|SKIPPED|REUSED|CANCELLED`, `RUNNING→WAITING_USER|SUCCEEDED|FAILED|CANCELLED|INTERRUPTED`, `WAITING_USER→PENDING|CANCELLED`; retry는 새 attempt다.

## 3. 사용자·프로필

### 3.1 `users`

| 컬럼                               | 타입·제약                         | 설명                                                      |
| ---------------------------------- | --------------------------------- | --------------------------------------------------------- |
| `id`                               | uuid PK                           | 사용자                                                    |
| `email`                            | varchar(320) NOT NULL             | 소문자 정규화; 물리 purge 전 존재하는 모든 row에서 unique |
| `password_hash`                    | varchar(255) NOT NULL             | BCrypt                                                    |
| `display_name`                     | varchar(100) NOT NULL             | 1..100                                                    |
| `role`                             | varchar(30) NOT NULL CHECK `USER` | MVP 역할                                                  |
| `status`                           | varchar(30) NOT NULL CHECK        | lifecycle                                                 |
| `terms_agreed_at`, `ai_consent_at` | timestamptz NOT NULL              | 동의                                                      |
| `last_login_at`, `withdrawn_at`    | timestamptz NULL                  | 이력                                                      |
| `created_at`, `updated_at`         | timestamptz NOT NULL              | 시간                                                      |

탈퇴 final purge가 user row를 제거한 뒤 같은 정규화 email을 다시 사용할 수 있다.

Spring Session framework table은 user principal을 조회 가능한 인덱스를 가지며 login·signup rotation, password 변경과 탈퇴의 session 폐기를 지원한다. 탈퇴 접수 transaction은 해당 사용자의 현재 session을 포함한 모든 framework session row를 제거한다.

### 3.2 `user_profiles`

`id uuid PK`, `user_id uuid NOT NULL UNIQUE FK users`, `legal_name varchar(100) NULL`, `introduction varchar(2000) NULL`, `desired_roles/desired_industries/desired_locations jsonb NOT NULL DEFAULT []`, `expected_graduation_date date NULL`, `version`, timestamps. 세 배열은 각각 최대 10개, 중복 없는 1..100자 문자열이다.

`profile_completed`는 입력 column이 아니라 다음 항목의 계산 projection이다: legal name, 각 희망 배열 1개 이상, active primary education 1개. 다섯 항목을 모두 충족하면 true이고 `completion_percent`는 충족 항목 수×20이다. 필요하면 transactionally maintained projection을 쓰되 같은 계산 규칙을 사용한다.

### 3.3 구조화 프로필 table

모두 `id,user_id,version,created_at,updated_at,deleted_at NULL`, `UNIQUE(user_id,id)`와 user FK를 가진다. 일반 목록·대표 학력·직접 evidence 동기화는 `deleted_at IS NULL` row만 사용한다.

| table             | domain column·상한                                                                                                                                                                                                                                       | 핵심 제약                                                                                                                                                      |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `educations`      | `school_name varchar(200)`, `major varchar(200) NULL`, `degree varchar(100) NULL`, `education_status varchar(30)`, `admission_date/graduation_date date NULL`, `gpa/gpa_scale numeric(5,2) NULL`, `is_primary boolean`, `description varchar(5000) NULL` | 날짜 순서, 둘 중 하나만 있는 GPA 금지, `gpa 0..10`, `gpa_scale 0.01..10`, `gpa<=gpa_scale`, `(user_id) WHERE is_primary AND deleted_at IS NULL` partial unique |
| `certifications`  | `name varchar(200)`, `issuer/credential_number varchar(200) NULL`, `acquired_date/expires_at date NULL`, `description varchar(5000) NULL`, `evidence_document_id uuid NULL`                                                                              | document 복합 FK, 둘 다 있으면 만료>=취득                                                                                                                      |
| `language_scores` | `test_name/score varchar(100)`, `grade varchar(100) NULL`, `tested_at/expires_at date NULL`, `evidence_document_id uuid NULL`                                                                                                                            | document 복합 FK, 둘 다 있으면 만료>=응시                                                                                                                      |
| `awards`          | `name varchar(200)`, `organizer varchar(200) NULL`, `awarded_at date NULL`, `description varchar(5000) NULL`, `evidence_document_id uuid NULL`                                                                                                           | document 복합 FK                                                                                                                                               |
| `careers`         | `organization varchar(200)`, `position varchar(200) NULL`, `employment_type varchar(50) NULL`, `started_at/ended_at date NULL`, `is_current boolean`, `responsibilities/achievements varchar(20000) NULL`                                                | current면 end null, 날짜 둘 다 있으면 역전 금지                                                                                                                |

구조화 row 저장·수정·삭제와 직접 입력 `profile_evidence` 동기화는 한 transaction이다.

구조화 source row를 soft delete하면 과거 job/cover/interview/mock provenance가 참조한 동기화 evidence는 원문 없는 `SOURCE_DELETED` tombstone으로 보존하고 미참조 evidence는 삭제한다. tombstone은 terminal·read-only이며 구조화 source의 `deleted_at`과 같은 transaction에서 반영한다.

## 4. 문서·근거·Object 삭제

### 4.1 `documents`

`id,user_id`, `document_type varchar(40) CHECK`, `original_filename varchar(255)`, `display_name varchar(255)`, `storage_key varchar(500) UNIQUE`, `mime_type varchar(100)`, `file_size_bytes bigint CHECK 1..20MiB`, `checksum_sha256 char(64)`, 두 상태 column, `parse_error_code/evidence_error_code varchar(100) NULL`, `manual_text_provided boolean`, `source_revision bigint`, `latest_agent_run_id uuid NULL`, `version`, `uploaded_at,updated_at`, `deleted_at NULL`.

- key 형식은 `users/{userId}/documents/{documentId}/content`이며 사용자 filename을 포함하지 않는다.
- `(user_id,checksum_sha256)` index는 dedupe 후보 검색용이며 unique business 규칙이 아니다.
- `deleted_at` 설정 즉시 owner API에서 404다.

### 4.2 `document_texts`

`id,user_id,document_id,source_revision`, `extracted_text text NULL CHECK <=500000`, `masked_text text NULL CHECK <=500000`, `page_count integer NULL CHECK >=1`, `character_count integer CHECK 0..500000`, 내부 `parser_name/parser_version varchar(150) NULL`, `parsed_at NULL`, `version`, timestamps. Unique `(user_id,document_id,source_revision)`, document 복합 FK.

### 4.3 `document_chunks`

`id,user_id,document_id,source_revision,chunk_index`, `page_from/page_to integer NULL`, `content text`, `masked_content text`, `token_count integer`, `embedding vector(1536) NULL`, `embedding_policy_version bigint NULL`, `embedding_provider varchar(50) NULL`, `embedding_model varchar(150) NULL`, `embedding_dimension integer NULL`, `embedding_generation integer NULL`, `metadata jsonb DEFAULT '{}'`, `created_at`.

- unique `(user_id,document_id,source_revision,chunk_index)`.
- active policy는 provider `OpenAI`, model `text-embedding-3-small`, dimension `1536`, cosine, generation을 한 묶음으로 선택한다.
- embedding이 있으면 policy version/provider/model/dimension/generation은 모두 NOT NULL이고 dimension=1536이어야 하며, embedding이 없으면 다섯 metadata field도 모두 null이다.
- 다른 model·dimension을 같은 typed vector column/index에 섞지 않는다.
- 초기 HNSW index는 만들지 않는다. exact cosine search를 사용한다.

### 4.4 `profile_evidence`

`id,user_id`, `source_type varchar(50)`, `source_entity_id uuid NULL`, `document_id uuid NULL`, `evidence_category varchar(80)`, `title varchar(250)`, `content varchar(20000)`, `metadata jsonb <=16KiB`, `confidence numeric(4,3) NULL CHECK 0..1`, `verification_status`, `verified_at NULL`, `source_deleted_at NULL`, `version`, timestamps.

- document source는 document 복합 FK를 갖되 삭제 tombstone 보존을 위해 physical FK 동작은 evidence를 cascade하지 않는다.
- 문서 삭제 시 참조된 evidence는 `id,user_id,source_type,evidence_category,verification_status=SOURCE_DELETED,source_deleted_at`과 provenance link만 남기는 최소 tombstone으로 바꾼다. title/content는 비식별 고정 marker, metadata는 빈 object, confidence·verified_at은 null로 치환하고 원문·page/chunk 위치는 제거한다. 미참조 document evidence는 삭제한다.
- 참조 여부는 job analysis, cover answer, interview question과 mock session의 typed evidence link 존재로 판정한다.
- 직접 입력 evidence는 document 삭제의 영향을 받지 않는다.
- `SOURCE_DELETED` evidence는 terminal·read-only여서 content 수정이나 `VERIFIED|REJECTED` 전이를 허용하지 않는다.

### 4.5 `object_deletion_outbox`

`id,user_id,document_id,storage_key varchar(500),reason,status(PENDING|PROCESSING|SUCCEEDED|DEAD),attempt_count,next_attempt_at`, `claim_token/lease_expires_at NULL`, `last_error_code varchar(100) NULL,created_at,completed_at NULL`. Active unique `(document_id,storage_key,reason)`.

상태는 `PENDING→PROCESSING→SUCCEEDED|DEAD`이고 lease가 만료된 PROCESSING은 PENDING으로 회수한다. retry는 1분, 5분, 30분, 2시간, 12시간, 이후 24시간이며 최대 10회 뒤 `DEAD`+운영 경보다. storage key와 provider 오류는 client에 노출하지 않는다.

## 5. 회사·공고·분석

### 5.1 `companies`

전역 table: `id`, `normalized_name/display_name varchar(200) NOT NULL`, `official_website varchar(2000) NULL`, timestamps. `lower(normalized_name)` unique.

### 5.2 `job_postings`

`id,user_id`, `company_id uuid NULL`, `source_url/canonical_url varchar(2000)`, `title/position_name varchar(300) NULL`, `role_category/employment_type varchar(100) NULL`, `location varchar(200) NULL`, `description_text text NULL CHECK <=200000`, `description_source varchar(30) NULL`, `deadline_at NULL`, `deadline_source`, `deadline_confidence numeric(4,3) NULL`, 두 상태 축, `submitted_at/closed_at NULL`, `closed_reason varchar(30) NULL`, 내부 `content_hash char(64) NULL`, `latest_agent_run_id uuid NULL`, `version`, timestamps, `deleted_at NULL`.

- active partial unique `(user_id,canonical_url) WHERE deleted_at IS NULL`.
- create request에 usable `description_text`가 있으면 `description_source=USER_ENTERED`, `extraction_status=MANUAL_INPUT_PROVIDED`, `latest_agent_run_id=NULL`이며 extraction run을 만들지 않는다. 본문이 없으면 `extraction_status=QUEUED`와 `JOB_POSTING_EXTRACTION` run을 같은 transaction에서 만든다.
- `submitted_at`은 최초 SUBMITTED에서 설정하고 영구 보존한다. reopen은 현재 `closed_at/closed_reason`만 null로 만들며 history는 보존한다.
- status 변경과 history는 한 transaction, Scheduler도 업무 status만 변경한다.

### 5.3 `job_status_history`

`id,user_id,job_posting_id`, `from_status varchar(30) NULL`, `to_status,reason varchar(100),changed_by(USER|SCHEDULER|SYSTEM),changed_at`; 복합 parent FK.

### 5.4 `job_analyses`와 rubric

`job_analyses`: `id,user_id,job_posting_id,analysis_version`, 내부 job/profile/evidence hash, `eligibility`, `fit_score numeric(5,2) NULL CHECK 0..100`, `analysis_summary varchar(10000) NULL`, `rubric_version`, `agent_run_id`, `created_at`; unique `(user_id,job_posting_id,analysis_version)`.

`job_analysis_score_criteria`: `id,user_id,job_analysis_id,category,criterion varchar(2000)`, `weight numeric(5,2) CHECK 0..100`, `match_level`, `score numeric(5,2) CHECK 0..weight`, `explanation varchar(2000)`, `source_location varchar(500) NULL`.

`job_analysis_evidence_links`: `id,user_id,job_analysis_id,score_criterion_id NULL,profile_evidence_id,usage_type,created_at`; 모든 복합 FK. requirements·strength/gap snapshot JSON은 display용이고 provenance 원천은 link다.

가중치는 40/30/15/10/5이며 `Eligibility`와 점수는 별도다. 성공한 analysis는 criterion을 최소 1개 가지며 criterion을 추출하지 못하면 analysis row 없이 run을 `INSUFFICIENT_JOB_DATA`로 실패시킨다. stale 여부·reason은 저장 enum이 아니라 current hash 비교 projection이다.

## 6. 자기소개서

### 6.1 `cover_letters`

`id,user_id,job_posting_id,title varchar(300),status`, `finalized_at/archived_at/deleted_at NULL`, `version,created_at,updated_at`.

- partial unique `(user_id,job_posting_id) WHERE deleted_at IS NULL AND status IN ('DRAFT','FINALIZED')`.
- archived history는 여러 개다.
- unarchive는 active row가 없을 때만 가능하고 `archived_at=null`, `finalized_at`은 이력으로 유지한다.

### 6.2 `cover_letter_questions`

`id,user_id,cover_letter_id,question_order 1..20,question_text varchar(2000),max_length integer 1..10000 NULL,memo varchar(2000) NULL,version,timestamps`, `deleted_at NULL`. Active order unique. 삭제는 soft delete하며 answer·verification을 cascade하지 않는다.

### 6.3 `cover_letter_answer_versions`

`id,user_id,question_id,parent_version_id NULL,restored_from_version_id NULL,version_no,content_json jsonb,content_text varchar(20000),character_count integer 0..20000,source_type,is_current,created_by,created_at`; unique version number, partial unique `(user_id,question_id) WHERE is_current=true`.

- immutable row이며 source는 server가 지정한다.
- allowlist TipTap JSON만 저장하고 raw HTML은 저장하지 않는다.
- current 교체, cover `FINALIZED→DRAFT`, 검증 freshness 무효화는 한 transaction이다.

### 6.4 provenance·검증

`cover_letter_evidence_links`: `id,user_id,answer_version_id,profile_evidence_id,claim_text varchar(2000),usage_type,created_at`; unique link와 복합 FK. Evidence tombstone 뒤에도 link를 보존한다.

`cover_letter_verifications`: `id,user_id,answer_version_id,status,issues jsonb,suggestions jsonb,verified_claims jsonb,agent_run_id NULL,created_at`; PENDING은 run terminal transaction에서 `PASSED|WARNING|FAILED`가 되어 고착되지 않는다. P0 async verify로 새로 생성한 row는 agent run을 반드시 연결하며 nullable은 승인된 공개 DTO와 보존 이력 경계를 맞춘다.

`cover_letter_verification_acknowledgements`: `id,user_id,cover_letter_id,verification_id,acknowledged_at`; cover letter와 verification에 owner 복합 FK, unique `(user_id,cover_letter_id,verification_id)`를 가지는 immutable audit link다. `WARNING`이고 finalize 시점 current answer의 최신 verification인 ID만 finalize transaction에서 삽입한다.

finalize는 모든 active question current answer와 그 version의 최신 verification을 검사하고 필요한 acknowledgement link를 같은 transaction에서 저장한다.

## 7. 조사·예상 질문·답변

### 7.1 `research_runs`, topic·source

`research_runs`: `id,user_id,job_posting_id,cover_letter_id,retry_of_research_run_id NULL,research_quality,status,source_coverage NULL,missing_coverage_topics jsonb,summary varchar(10000) NULL,agent_run_id,retryable,safe_error_code varchar(100) NULL`, `created_at,started_at NULL,completed_at NULL,updated_at`.

`research_topics`: `id,user_id,research_run_id,topic,query_text varchar(500),topic_order,created_at`; unique `(user_id,research_run_id,topic,query_text)`과 run 복합 FK를 가진다.

`research_sources`: `id,user_id,research_run_id,source_url varchar(2000),title varchar(500) NULL,source_type,published_at NULL,retrieved_at,snippet varchar(2000) NULL,reliability_notice varchar(500),provider_rank integer,content_hash char(64)`; unique `(user_id,research_run_id,source_url)`.

`research_topic_source_links`: `id,user_id,research_topic_id,research_source_id,is_primary,created_at`; 양쪽 owner composite FK와 unique `(user_id,research_topic_id,research_source_id)`, source당 primary link 하나를 가진다. 공개 `ResearchSourceDto.topic`은 primary link의 topic이다.

preparation 하나는 combined research run 하나와 question set 하나를 만든다. retry는 기존 row를 덮지 않고 새 run·question set·Agent Run을 만들며 lineage를 저장한다.

### 7.2 질문 세트와 provenance

`interview_question_sets`: `id,user_id,job_posting_id,cover_letter_id,research_run_id,title varchar(300),generation_config jsonb,agent_run_id,created_at,updated_at`.

`interview_questions`: `id,user_id,question_set_id,question_order 1..20,question_type,question_text varchar(2000)`, `intent varchar(2000) NULL`, `evaluation_points jsonb DEFAULT '[]'`, `answer_guide varchar(10000) NULL`, `follow_up_questions jsonb DEFAULT '[]',source_based boolean,created_at`; active order unique.

`interview_question_evidence_links`: question↔evidence typed N:M. `interview_question_source_links`: question↔research source typed N:M. `source_based`는 source link 존재 여부로 계산한다.

### 7.3 답변·feedback

`interview_answer_versions`: `id,user_id,interview_question_id,parent_version_id NULL,version_no,content varchar(20000),source_type CHECK USER_EDITED,is_current,created_at`; partial current unique.

`interview_answer_feedbacks`: `id,user_id,answer_version_id,scores jsonb,strengths/weaknesses/suggestions jsonb`, `revised_example varchar(10000) NULL`, `agent_run_id,created_at`.

feedback row는 성공 domain apply transaction에서만 생성한다. 실패·취소 PENDING row를 만들지 않는다.

## 8. 모의 면접

### 8.1 `mock_interview_sessions`

`id,user_id,job_posting_id,cover_letter_id,question_set_id NULL,status,feedback_status,interview_type,difficulty,target_question_count 1..20,current_question_count 0..target_question_count,feedback_timing,pressure_mode,budget_policy_version,version`, `actual_cost_usd numeric(12,6) CHECK >=0`, `feedback_agent_run_id/started_at/completed_at NULL`, `created_at,updated_at`.

관련 job/cover/question set와 preferred evidence는 같은 사용자여야 한다. `COMPLETED`와 feedback 상태는 독립이다.

### 8.2 `mock_session_evidence_links`

`id,user_id,session_id,evidence_id,display_order,created_at`을 저장한다. unique `(user_id,session_id,evidence_id)`와 `(user_id,session_id,display_order)`, session당 최대 5개이며 session과 evidence 양쪽에 owner composite FK를 둔다. 참조할 수 있는 evidence는 `VERIFIED`뿐이다.

### 8.3 `mock_interview_turns`

`id,user_id,session_id,client_request_id,request_hash,status(PENDING|COMPLETED|FAILED)`, `user_message_id/interviewer_message_id NULL`, `response_http_status integer NULL`, `response_json jsonb NULL`, `safe_error_code varchar(100) NULL,started_at,completed_at NULL`.

- unique `(user_id,session_id,client_request_id)`.
- session당 PENDING 최대 1개 partial unique.
- `PENDING`에는 terminal response field가 모두 null이다. `COMPLETED|FAILED`는 original `response_http_status`와 client-safe `response_json`이 non-null이고 FAILED만 `safe_error_code`가 non-null이다.
- 동일 ID/hash terminal은 성공·실패 모두 원래 HTTP status와 저장 응답을 replay하고 모델을 재호출하지 않는다. 처리 중은 409, 다른 hash는 key reuse 409다.

### 8.4 message·feedback

`mock_interview_messages`: `id,user_id,session_id,sequence_no,role(USER|INTERVIEWER),content varchar(5000),related_question_id NULL,created_at`; unique `(user_id,session_id,sequence_no)`.

`mock_interview_feedbacks`: `id,user_id,session_id,message_id NULL,feedback_scope(MESSAGE|SESSION),items jsonb`, `session_summary varchar(10000) NULL`, `agent_run_id NULL,created_at`. 종합 feedback은 async `BALANCED`; 동기 immediate feedback은 turn response snapshot에도 저장할 수 있으나 별도 유료 호출을 하지 않는다.

## 9. Idempotency·Agent Run

### 9.1 `idempotency_records`

```text
id, user_id, http_method, route_scope, resource_scope_id
idempotency_key, request_hash, hash_key_version, state(IN_PROGRESS|COMPLETED)
response_status NULL, response_json NULL
resource_type NULL, resource_id NULL, agent_run_id NULL
created_at, completed_at NULL, expires_at
```

Unique `(user_id,http_method,route_scope,resource_scope_id,idempotency_key)`. Root scope는 nil UUID다. request hash는 canonical request와 upload가 있으면 파일 SHA-256을 입력으로 한 versioned server key의 HMAC-SHA-256이며 비밀번호·본문·파일 원문은 저장하지 않는다. 완료 24시간 보존, linked run terminal 전 IN_PROGRESS cleanup 금지. 탈퇴 record는 만들지 않는다.

비동기 endpoint의 202 접수도 resource·Agent Run을 같은 transaction에서 만든 뒤 `COMPLETED` response로 저장하므로 replay는 원래 status·DTO와 같은 run ID를 반환한다.

### 9.2 `agent_runs`

`id,user_id,workflow_type,status,current_step NULL`, `progress_percent integer CHECK 0..100`, `workflow_version,input_hash,budget_policy_version,requested_quality_mode NULL,highest_model_tier_used NULL`, `estimated_cost_usd/reserved_cost_usd/actual_cost_usd numeric(12,6) CHECK >=0`, `retry_of_run_id NULL,root_run_id,run_attempt_no`, `error_code varchar(100) NULL,error_message_safe varchar(500) NULL,partial_result_json NULL,claim_token NULL,claimed_by NULL,lease_expires_at NULL,heartbeat_at NULL,cancel_requested_at NULL,waiting_reason NULL,state_version,queued_at,started_at NULL,completed_at NULL,updated_at`.

- retry predecessor 복합 FK와 root lineage를 보존하고 `UNIQUE(user_id,retry_of_run_id) WHERE retry_of_run_id IS NOT NULL`로 모든 resource/generic retry 진입점이 predecessor당 successor를 하나만 만들게 한다. 호환되는 후속 retry는 같은 successor를 반환하고 option 충돌은 거부한다.
- claim은 조건부 update 또는 `FOR UPDATE SKIP LOCKED`, heartbeat 15초, lease 60초, reconciliation 30초다.
- lease가 만료된 RUNNING row는 immutable `INTERRUPTED`다.
- user retry는 새 run, WAITING_USER resume은 같은 run이다.
- cancel terminal 처리와 processing resource의 마지막 안정 상태 복원은 한 transaction이다. Job extraction은 usable source가 있으면 `EXTRACTED|MANUAL_INPUT_PROVIDED`, 없으면 `NEEDS_MANUAL_INPUT`; document parse는 같은 revision의 committed text/chunk가 있으면 `PARSED`, 없으면 `UPLOADED`; evidence extraction은 같은 revision의 prior 성공 snapshot이 있으면 `SUCCEEDED`, 없으면 `NOT_STARTED`로 복원한다.
- interview preparation cancel은 research run을 `CANCELLED`로 끝내고 preallocated question set은 질문·source link 없는 read-only cancelled 결과로 남긴다. interview answer feedback은 row를 만들지 않고, mock feedback은 session `feedback_status=CANCELLED`, cover verification은 연결 PENDING verification을 `FAILED`로 종결한다. reconciliation도 terminal run과 processing resource 불일치를 같은 mapping으로 복구한다.

### 9.3 `agent_run_resource_links`

`id,user_id,agent_run_id,resource_kind,document_id NULL,job_posting_id NULL,job_analysis_id NULL,cover_letter_id NULL,cover_letter_answer_version_id NULL,research_run_id NULL,question_set_id NULL,interview_answer_version_id NULL,mock_session_id NULL,created_at`을 저장한다. resource column은 정확히 하나만 non-null이고 각 column은 대상의 `(user_id,id)`에 복합 FK를 둔다. run당 primary resource link는 최대 하나이며 공개 `resourceType/resourceId`는 이 link에서 계산한다. idempotency record의 같은 이름 field는 replay metadata일 뿐 authoritative owner relation이 아니다.

### 9.4 `agent_steps`

`id,user_id,agent_run_id,step_key varchar(100),scope_key varchar(100) NULL,step_order,agent_name varchar(150),status,attempt,max_attempts 1..3,input_hash,output_hash NULL,input_refs jsonb,output_json jsonb NULL,output_schema_version,model_policy_version,prompt_version,reused_step_id NULL,error_code varchar(100) NULL,error_message_safe varchar(500) NULL`, `created_at,started_at NULL,completed_at NULL,updated_at`.

Unique `(user_id,agent_run_id,step_key,scope_key,attempt)`. `output_json`에는 result ref·hash·validation summary만 저장하며 원문·prompt/provider response를 저장하지 않는다.

## 10. AI policy·가격·예산·usage

### 10.1 versioned policy

`ai_model_policies`, `embedding_policy_versions`, `ai_budget_policy_versions`는 immutable version을 가진 전역 policy다. embedding active 값은 provider `OpenAI`, model `text-embedding-3-small`, dimension `1536`, cosine, active generation이다. budget policy는 user default/system maximum daily budget, async run·mock turn·mock session 상한과 reset zone을 한 version으로 묶는다. 공개 quality와 내부 tier를 별도 column으로 저장한다.

`user_ai_preferences`: `id,user_id,default_quality_mode(ECONOMY|BALANCED),high_quality_enabled,daily_budget_usd,version,timestamps`; user당 active 1개. 초기 user budget 1.00, system max 2.00, reset zone `Asia/Seoul`은 versioned 운영 policy에서 관리한다.

### 10.2 immutable 가격·ledger·reservation

- `ai_price_versions`: immutable catalog header와 effective range.
- `ai_price_items`: provider, product, unit, unit_price와 price version. 외부 provider 단가를 이 명세에 금액으로 고정하지 않는다.
- `ai_budget_ledgers`: `id,user_id,budget_date,budget_zone,spent_usd,reserved_usd,policy_version`; unique user/date/zone.
- `ai_budget_reservations`: `id,user_id,operation_type,agent_run_id NULL,mock_turn_id NULL,reserved_usd,settled_usd,status,expires_at,budget_policy_version,price_version,timestamps`.
- `ai_usage_records`: `id,user_id,agent_run_id NULL,agent_step_id NULL,mock_session_id NULL,mock_turn_id NULL,operation_type,usage_type(CHAT|EMBEDDING|SEARCH),provider,product,model_tier,unit counts,price_version,cost_usd,duration_ms,created_at`.

chat input/cached input/output, embedding unit, BASIC/ADVANCED search를 모두 기록한다. 무료/cache hit도 0 cost usage row를 남긴다. 동기 mock turn usage는 run/step FK가 null이고 session/turn 복합 FK가 필수다.

초기 상한은 user default daily 1.00, system daily max 2.00, async run max 0.30, mock turn 0.03, mock session sync total 0.30 USD다. 값은 code constant가 아니라 versioned policy다.

## 11. 회원 탈퇴 task와 보존

`account_deletion_tasks`는 user FK가 없는 독립 table이다.

```text
id(deletionRequestId), subject_user_id NULL
status(QUEUED|RUNNING|RETRY_WAIT|SUCCEEDED|DEAD), policy_version
attempt_count, next_attempt_at, claim_token NULL, lease_expires_at NULL
purge_by, last_error_code varchar(100) NULL, requested_at, completed_at NULL
```

- email·이름·원문을 복사하지 않는다.
- 접수 transaction: password 확인 뒤 user `WITHDRAWN`, 모든 Session 삭제, task `QUEUED`.
- worker는 active run cancel·안정 상태 반영, Object outbox 완료, domain child purge를 처리한다.
- final transaction은 task를 SUCCEEDED·`subject_user_id=null`로 바꾸고 user를 purge한다.
- 물리 삭제 목표는 접수 후 24시간 이내다.
- 개인정보 없는 성공 task metadata는 30일 보존한다. `DEAD`는 운영 경보·수동 복구 대상이다.

## 12. Transaction·삭제·embedding 운영 규칙

1. 공고 status와 history, timestamp는 한 transaction이다.
2. current answer false/true와 cover DRAFT 전이는 한 transaction이다.
3. Agent step checkpoint와 domain apply는 input hash·owner·version으로 멱등 처리한다.
4. 비용은 resource/run/turn 생성 전 원자 reserve, catalog version으로 settle, terminal·WAITING_USER에 release한다.
5. Object upload 성공 뒤 DB 실패는 보상 삭제, DB logical delete 뒤 Object 삭제는 Outbox다.
6. 질문은 soft delete하고 answer version·verification·provenance를 보존한다.
7. document delete는 API 즉시 404, Object/text/chunk/embedding purge, 참조 evidence tombstone, 미참조 evidence 삭제다.
8. boot 시 configured embedding model dimension과 `vector(1536)` typmod가 다르면 fail fast한다.
9. live chunk 50,000개 이상 또는 대표 query p95가 200ms를 초과할 때만 별도 migration으로 HNSW를 검토한다.
10. model·dimension 변경은 새 generation과 typed vector column/index 생성→backfill→검증→active switch→cleanup 순서다.

## 13. 향후 migration 책임

기존 `V1__enable_extensions.sql`은 수정하지 않는다. 실제 버전과 SQL은 구현 단계에서 작성한다.

| 순서 책임                    | 목표 영역                                                                        |
| ---------------------------- | -------------------------------------------------------------------------------- |
| identity/session/idempotency | users, profile base, Spring Session ownership, idempotency, 독립 deletion task   |
| structured profile           | profile 5종, direct evidence, version·대표 학력 unique                           |
| Agent runtime/budget         | run/step claim·retry·cancel, policy, price, ledger, reservation, usage           |
| documents/evidence           | 두 상태 축, owner FK, text/chunk, vector(1536), Object outbox                    |
| jobs/analysis                | canonical active unique, 두 상태 축, history, rubric·provenance                  |
| cover letter                 | active partial unique, soft question, immutable answer/content/link/verification |
| research/interview           | combined research, source links, answer/feedback, mock turn/message/feedback     |
| vector index 조건부          | 측정 기준을 넘을 때만 HNSW                                                       |

각 migration은 owner composite FK·unique·CHECK를 같은 단계에서 만들고 빈 DB와 직전 production-like schema upgrade를 검증한다.
