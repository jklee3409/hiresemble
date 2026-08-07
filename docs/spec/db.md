# DB 명세서

- 문서 버전: 1.4 (GitHub Source·Career Artifact 목표 계약)
- 기준일: 2026-08-07
- DBMS: PostgreSQL 18 + pgvector
- 식별자: UUID
- 시간: `timestamptz` UTC
- 상태: `varchar` + 명시적 `CHECK`
- JSON 산출물: `jsonb`

이 문서는 목표 데이터 계약과 현재 구현된 Flyway 경계를 함께 기록한다. 현재 최신 migration은 GitHub Source ingestion을 추가한 V27이며, 미래 계약은 별도로 `PLANNED`를 표시한다. 적용된 V26은 변경하지 않았다.

## 1. 공통 무결성·소유권

- table은 snake_case 복수형, PK는 `id uuid`다.
- 축약 schema에서 `NULL`로 표시한 column만 nullable이고 나머지 나열된 domain column은 `NOT NULL`이다. `timestamps`는 `created_at,updated_at timestamptz NOT NULL`, 별도 `created_at`도 NOT NULL을 뜻한다. lifecycle timestamp와 terminal output은 각 table에 nullability를 명시한다.
- `companies`, `career_guide_posts`, immutable AI policy/price catalog, Spring Session framework table과 독립 `account_deletion_tasks`만 전역·framework 예외다.
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

| 축                                                   | CHECK 값                                                                                                                                                                                                |
| ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `users.status`                                       | `ACTIVE`, `LOCKED`, `WITHDRAWN`                                                                                                                                                                         |
| `job_postings.status`                                | `IN_PROGRESS`, `SUBMITTED`, `CLOSED`                                                                                                                                                                    |
| `job_postings.posting_half`                          | `FIRST_HALF`, `SECOND_HALF`                                                                                                                                                                             |
| `job_postings.extraction_status`                     | `QUEUED`, `EXTRACTING`, `EXTRACTED`, `MANUAL_INPUT_PROVIDED`, `NEEDS_MANUAL_INPUT`, `FAILED`                                                                                                            |
| `documents.parse_status`                             | `UPLOADED`, `PARSING`, `PARSED`, `NEEDS_MANUAL_TEXT`, `FAILED`                                                                                                                                          |
| `documents.evidence_extraction_status`               | `NOT_STARTED`, `QUEUED`, `EXTRACTING`, `SUCCEEDED`, `FAILED`                                                                                                                                            |
| `activities.activity_type`                           | `CLUB`, `VOLUNTEERING`, `CONTEST`, `SUPPORTERS`, `PRESS_CORPS`, `STUDENT_COUNCIL`, `EDUCATION_PROGRAM`, `INTERNATIONAL`, `OTHER`                                                                        |
| `profile_evidence.verification_status`               | `PENDING`, `VERIFIED`, `REJECTED`, `SOURCE_DELETED`                                                                                                                                                     |
| `experience_items.verification_status`               | `PENDING`, `VERIFIED`, `REJECTED`                                                                                                                                                                       |
| `experience_items.match_kind`                        | `NEW`, `RELATED_DIFFERENT`, `CONFLICT`                                                                                                                                                                  |
| `experience_evidence_links.relation_kind`            | `PRIMARY_SOURCE`, `CORROBORATING`                                                                                                                                                                       |
| `cover_letters.status`                               | `DRAFT`, `FINALIZED`, `ARCHIVED`                                                                                                                                                                        |
| `cover_letter_answer_versions.source_type`           | `AI_GENERATED`, `USER_EDITED`, `AI_REVISED`, `RESTORED`                                                                                                                                                 |
| `interview_answer_versions.source_type`              | `USER_EDITED`                                                                                                                                                                                           |
| `cover_letter_verifications.status`                  | `PENDING`, `PASSED`, `WARNING`, `FAILED`                                                                                                                                                                |
| `research_runs.status`                               | `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`                                                                                                                                                 |
| `research_runs.source_coverage`                      | 실행 중 `NULL`, terminal `SUFFICIENT`, `LIMITED`, `NONE`                                                                                                                                                |
| `mock_interview_sessions.status`                     | `READY`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`                                                                                                                                                        |
| `mock_interview_sessions.feedback_status`            | `NOT_REQUESTED`, `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`                                                                                                                                |
| `agent_runs.status`                                  | `QUEUED`, `RUNNING`, `WAITING_USER`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `INTERRUPTED`                                                                                                                  |
| `agent_steps.status`                                 | `PENDING`, `RUNNING`, `WAITING_USER`, `SUCCEEDED`, `FAILED`, `SKIPPED`, `REUSED`, `CANCELLED`, `INTERRUPTED`                                                                                            |
| `research_runs.research_quality`                     | `BASIC`, `ADVANCED`                                                                                                                                                                                     |
| `interview_questions.question_type`                  | `COVER_LETTER`, `RESUME`, `PORTFOLIO`, `TECHNICAL`, `PROJECT_DEEP_DIVE`, `BEHAVIORAL`, `COMPANY_MOTIVATION`, `FOLLOW_UP`                                                                                |
| 공개 품질                                            | `ECONOMY`, `BALANCED`, `HIGH_QUALITY`                                                                                                                                                                   |
| 내부 tier                                            | `LOW_COST`, `BALANCED`, `HIGH_QUALITY`                                                                                                                                                                  |
| `agent_runs.workflow_type`                           | `DOCUMENT_INGESTION`, `JOB_POSTING_EXTRACTION`, `JOB_ANALYSIS`, `COVER_LETTER_GENERATION`, `COVER_LETTER_VERIFICATION`, `INTERVIEW_PREPARATION`, `INTERVIEW_ANSWER_FEEDBACK`, `MOCK_INTERVIEW_FEEDBACK`, `GITHUB_INGESTION` |
| `agent_runs.workflow_type` (`PLANNED`)               | 기존 값에 `RESUME_GENERATION`, `PORTFOLIO_GENERATION` 추가                                                                                                                                            |
| `documents.document_type`                            | `RESUME`, `PORTFOLIO`, `CAREER_DESCRIPTION`, `CERTIFICATE`, `TRANSCRIPT`, `OTHER`                                                                                                                       |
| `profile_evidence.source_type`                       | `EDUCATION`, `CERTIFICATION`, `LANGUAGE_SCORE`, `AWARD`, `CAREER`, `ACTIVITY`, `DOCUMENT_CHUNK`, `EXPERIENCE`, `MANUAL`, `GITHUB_REPOSITORY`                                                            |
| `github_sources.source_kind`                         | `ACCOUNT`, `REPOSITORY`                                                                                                                                                                                  |
| `github_sources.account_type`                        | `USER`, `ORGANIZATION`; repository source에서는 `NULL`                                                                                                                                                   |
| `github_sources.source_status`                       | `DISCOVERING`, `WAITING_USER`, `QUEUED`, `RUNNING`, `READY`, `PARTIAL`, `FAILED`                                                                                                                        |
| `github_evidence_unit_links.relation_kind`           | `PRIMARY`, `SUPPORTING`                                                                                                                                                                                  |
| `career_artifacts.artifact_type` (`PLANNED`)         | `RESUME`, `PORTFOLIO`                                                                                                                                                                                    |
| `career_artifacts.lifecycle_status` (`PLANNED`)      | `ACTIVE`, `ARCHIVED`                                                                                                                                                                                     |
| `career_artifact_evidence_links.usage_type` (`PLANNED`) | `PRIMARY_EXPERIENCE`, `STRENGTH`, `SUPPORTING_FACT`                                                                                                                                                   |
| `job_postings.deadline_source`                       | `USER_ENTERED`, `AUTO_EXTRACTED`, `UNKNOWN`                                                                                                                                                             |
| `job_postings.closed_reason`                         | `DEADLINE_PASSED`, `USER_CLOSED`, `URL_INACTIVE`                                                                                                                                                        |
| `job_postings.description_source`                    | `AUTO_EXTRACTED`, `USER_ENTERED`                                                                                                                                                                        |
| `job_analyses.eligibility`                           | `ELIGIBLE`, `CONDITIONAL`, `INELIGIBLE`, `UNKNOWN`                                                                                                                                                      |
| `job_analysis_score_criteria.category`               | `REQUIRED_QUALIFICATION`, `CORE_RESPONSIBILITY_OR_SKILL`, `PREFERRED_QUALIFICATION`, `RELATED_EXPERIENCE_OR_DOMAIN`, `EDUCATION_CERTIFICATION_LANGUAGE`                                                 |
| `job_analysis_score_criteria.match_level`            | `MATCHED`, `PARTIAL`, `MISSING`, `UNKNOWN`                                                                                                                                                              |
| `research_topics.topic`                              | `COMPANY`, `INTERVIEW_PROCESS`, `ROLE_TECHNICAL`                                                                                                                                                        |
| `research_sources.source_type`                       | `OFFICIAL`, `TECH_BLOG`, `NEWS`, `INTERVIEW_REVIEW`, `COMMUNITY`, `OTHER`                                                                                                                               |
| `mock_interview_sessions.interview_type`             | `TECHNICAL`, `BEHAVIORAL`, `TECHNICAL_AND_BEHAVIORAL`                                                                                                                                                   |
| `mock_interview_sessions.difficulty`                 | `EASY`, `NORMAL`, `HARD`                                                                                                                                                                                |
| `mock_interview_sessions.feedback_timing`            | `AFTER_EACH`, `END_ONLY`                                                                                                                                                                                |
| `mock_interview_messages.role`                       | `USER`, `INTERVIEWER`                                                                                                                                                                                   |
| `mock_interview_turns.status`                        | `PENDING`, `COMPLETED`, `FAILED`                                                                                                                                                                        |
| `idempotency_records.state`                          | `IN_PROGRESS`, `COMPLETED`                                                                                                                                                                              |
| `object_deletion_outbox.status`                      | `PENDING`, `PROCESSING`, `SUCCEEDED`, `DEAD`                                                                                                                                                            |
| `ai_budget_reservations.status`                      | `RESERVED`, `SETTLED`, `RELEASED`, `EXPIRED`                                                                                                                                                            |
| `ai_usage_records.usage_type`                        | `CHAT`, `EMBEDDING`, `SEARCH`                                                                                                                                                                           |
| `feature_usage_reservations.status` (`PLANNED`)      | `RESERVED`, `COMMITTED`, `RELEASED`, `EXPIRED`                                                                                                                                                          |
| `feature_usage_policy_items.period_type` (`PLANNED`) | `DAILY`, `WEEKLY`, `MONTHLY`, `LIFETIME`                                                                                                                                                                |
| `feature_usage_events.outcome` (`PLANNED`)           | `SUCCEEDED`, `FAILED`, `CANCELLED`, `PARTIAL`, `REUSED`, `ADJUSTED`                                                                                                                                     |
| `feature_usage_events.charge_mode` (`PLANNED`)       | `METERED_ZERO_RATE`, `NO_CHARGE`                                                                                                                                                                        |
| `account_deletion_tasks.status`                      | `QUEUED`, `RUNNING`, `RETRY_WAIT`, `SUCCEEDED`, `DEAD`                                                                                                                                                  |
| `educations.education_status`                        | `ENROLLED`, `LEAVE_OF_ABSENCE`, `EXPECTED_GRADUATION`, `GRADUATED`, `WITHDRAWN`                                                                                                                         |
| `educations.education_level`                         | `OTHER`, `HIGH_SCHOOL`, `ASSOCIATE`, `BACHELOR`, `MASTER`, `DOCTORATE`                                                                                                                                  |
| `cover_letter_answer_versions.created_by`            | `USER`, `AI`                                                                                                                                                                                            |
| `career_guide_posts.status`                           | `DRAFT`, `PUBLISHED`, `ARCHIVED`                                                                                                                                                                         |

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
- GitHub source: 생성 직후 `DISCOVERING`; repository URL은 `DISCOVERING→QUEUED→RUNNING→READY|PARTIAL|FAILED`, account URL은 `DISCOVERING→WAITING_USER→QUEUED→RUNNING→READY|PARTIAL|FAILED`다. refresh는 같은 aggregate의 `source_revision`을 올리고 `READY|PARTIAL|FAILED→QUEUED`로 시작하며 삭제 row는 전이 대상에서 제외한다.
- Career Artifact (`PLANNED`): aggregate lifecycle은 `ACTIVE↔ARCHIVED`이고 generation 상태는 latest linked Agent Run에서 `NOT_STARTED|QUEUED|RUNNING|SUCCEEDED|FAILED|CANCELLED|INTERRUPTED`로 projection한다. 실패·취소·중단은 이미 성공한 `current_version_id`를 변경하지 않는다.

## 3. 사용자·프로필

### 3.1 `users`

| 컬럼                               | 타입·제약                                                 | 설명                                                      |
| ---------------------------------- | --------------------------------------------------------- | --------------------------------------------------------- |
| `id`                               | uuid PK                                                   | 사용자                                                    |
| `email`                            | varchar(320) NOT NULL                                     | 소문자 정규화; 물리 purge 전 존재하는 모든 row에서 unique |
| `password_hash`                    | varchar(255) NOT NULL                                     | BCrypt                                                    |
| `display_name`                     | varchar(100) NOT NULL                                     | 1..100                                                    |
| `role`                             | varchar(30) NOT NULL CHECK 현재 `USER`; P8.9-A 목표 `USER | ADMIN` (`PLANNED`)                                        | 일반 signup은 항상 USER, ADMIN은 통제 provisioning만 허용 |
| `status`                           | varchar(30) NOT NULL CHECK                                | lifecycle                                                 |
| `terms_agreed_at`, `ai_consent_at` | timestamptz NOT NULL                                      | 동의                                                      |
| `last_login_at`, `withdrawn_at`    | timestamptz NULL                                          | 이력                                                      |
| `created_at`, `updated_at`         | timestamptz NOT NULL                                      | 시간                                                      |

탈퇴 final purge가 user row를 제거한 뒤 같은 정규화 email을 다시 사용할 수 있다.

Spring Session framework table은 user principal을 조회 가능한 인덱스를 가지며 login·signup rotation, password 변경과 탈퇴의 session 폐기를 지원한다. 탈퇴 접수 transaction은 해당 사용자의 현재 session을 포함한 모든 framework session row를 제거한다.

### 3.2 `user_profiles`

`id uuid PK`, `user_id uuid NOT NULL UNIQUE FK users`, `legal_name varchar(100) NULL`, `introduction varchar(2000) NULL`, `desired_roles/desired_industries/desired_locations jsonb NOT NULL DEFAULT []`, `expected_graduation_date date NULL`, `version`, timestamps. 세 배열은 각각 최대 10개, 중복 없는 1..100자 문자열이다.

`profile_completed`는 입력 column이 아니라 다음 항목의 계산 projection이다: legal name, 각 희망 배열 1개 이상, active primary education 1개. 다섯 항목을 모두 충족하면 true이고 `completion_percent`는 충족 항목 수×20이다. 필요하면 transactionally maintained projection을 쓰되 같은 계산 규칙을 사용한다.

`profile_eligibility_declarations`: 사용자별 1:1 row로 `id,user_id UNIQUE`, `work_available_date date NULL`, `military_status`, `overseas_travel_eligibility`, `employment_disqualification_status`, `version`, timestamps를 가진다. 신규·기존 사용자 모두 세 enum을 `UNSPECIFIED`로 안전하게 초기화하며 owner scope와 optimistic version을 적용한다.

### 3.3 구조화 프로필 table

모두 `id,user_id,version,created_at,updated_at,deleted_at NULL`, `UNIQUE(user_id,id)`와 user FK를 가진다. 일반 목록과 최종 학력은 `deleted_at IS NULL` row만 사용하며, 직접 evidence 동기화는 학력을 제외한 자격증·어학·수상·경력·대외활동의 active row에만 적용한다.

| table             | domain column·상한                                                                                                                                                                                                                                                                       | 핵심 제약                                                                                                                                                                                                                                                                     |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `educations`      | `school_name varchar(200)`, `major varchar(200) NULL`, `degree varchar(100) NULL`, `education_level varchar(30)`, `education_status varchar(30)`, `admission_date/graduation_date date NULL`, `gpa/gpa_scale numeric(5,2) NULL`, `is_primary boolean`, `description varchar(5000) NULL`  | 날짜 순서, 둘 중 하나만 있는 GPA 금지, `gpa 0..10`, `gpa_scale 0.01..10`, `gpa<=gpa_scale`, level/status CHECK, `(user_id) WHERE is_primary AND deleted_at IS NULL` partial unique. 사용자 profile row lock 아래 `학력 단계 > 상태 > 날짜 > 등록 시각`으로 최종 학력을 재계산 |
| `certifications`  | `name varchar(200)`, `issuer/credential_number varchar(200) NULL`, `acquired_date/expires_at date NULL`, `description varchar(5000) NULL`, `evidence_document_id uuid NULL`                                                                                                              | document 복합 FK, 둘 다 있으면 만료>=취득                                                                                                                                                                                                                                     |
| `language_scores` | `test_name/score varchar(100)`, `grade varchar(100) NULL`, `tested_at/expires_at date NULL`, `evidence_document_id uuid NULL`                                                                                                                                                            | document 복합 FK, 둘 다 있으면 만료>=응시                                                                                                                                                                                                                                     |
| `awards`          | `name varchar(200)`, `organizer varchar(200) NULL`, `awarded_at date NULL`, `description varchar(5000) NULL`, `evidence_document_id uuid NULL`                                                                                                                                           | document 복합 FK                                                                                                                                                                                                                                                              |
| `careers`         | `organization varchar(200)`, `position varchar(200) NULL`, `employment_type varchar(50) NULL`, `started_at/ended_at date NULL`, `is_current boolean`, `responsibilities/achievements varchar(20000) NULL`                                                                                | current면 end null, 날짜 둘 다 있으면 역전 금지                                                                                                                                                                                                                               |
| `activities`      | `title varchar(200)`, `activity_type varchar(40)`, `organizer varchar(200)`, `started_at/ended_at date NULL`, `ongoing boolean`, `role varchar(200) NULL`, `description varchar(10000)`, `achievements varchar(10000) NULL`, `related_url varchar(1000) NULL`, `use_as_material boolean` | ongoing이면 end null, 날짜 역전 금지, absolute HTTP(S) URL application validation, owner별 active 조회 index                                                                                                                                                                  |

자격증·어학·수상·경력·대외활동 row 저장·수정·삭제와 직접 입력 `profile_evidence` 동기화는 한 transaction이다. 학력은 구조화 row로만 유지하고 evidence를 생성하지 않는다. 대외활동은 특정 document FK를 갖지 않으며 document 삭제에 연쇄 삭제되지 않는다. `use_as_material=false`는 연결 ACTIVITY evidence를 `REJECTED`, true는 `VERIFIED`로 유지한다.

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
- 직접 입력 evidence와 그 원본 대외활동은 document 삭제의 영향을 받지 않는다.
- `SOURCE_DELETED` evidence는 terminal·read-only여서 content 수정이나 `VERIFIED|REJECTED` 전이를 허용하지 않는다.
- `EXPERIENCE`는 `source_entity_id=experience_items.id`, `document_id=NULL`인 정규 근거다. 연결된 document evidence 대신 후속 AI snapshot에 한 번만 포함된다.
- 학력 source와 교육·학력 category는 active evidence로 허용하지 않는다. 기존 row는 source/document 연결, title/content, metadata, confidence를 제거한 `SOURCE_DELETED` tombstone으로 전환해 provenance ID만 보존하며 일반 조회와 분석에서는 제외한다.

### 4.5 `experience_items`

`id,user_id,canonical_evidence_id`, `evidence_category`, `title`, `content`, `verification_status`, `match_kind`, `matched_experience_item_id NULL`, `match_similarity NULL`, `match_policy_version`, `canonical_fingerprint`, `version`, timestamps, `deleted_at NULL`.

- active `(user_id,evidence_category,canonical_fingerprint)`은 unique이며 사용자 단위 advisory transaction lock으로 동시 문서 적용을 직렬화한다.
- `RELATED_DIFFERENT|CONFLICT`만 owner-matched `matched_experience_item_id`와 similarity를 가지며 `NEW`는 둘 다 null이다.
- `canonical_evidence_id`는 같은 사용자의 `profile_evidence(EXPERIENCE)`를 가리키며 승인된 항목이 원문 삭제 뒤에도 유지되는 근거 원천이다.

### 4.6 `experience_evidence_links`

`id,user_id,experience_item_id,profile_evidence_id,relation_kind,similarity NULL,match_policy_version,created_at`. source evidence는 사용자당 하나의 경험에만 연결하고 두 FK 모두 `(user_id,id)`로 owner를 강제한다.

### 4.7 `experience_item_embeddings`

`user_id,experience_item_id,experience_version`, `embedding vector(1536)`, immutable embedding policy tuple, `created_at`. PK는 경험 version·policy version·generation을 포함한다. 조회는 active item version과 enabled policy tuple을 모두 일치시킨 exact cosine Top-K다.

### 4.8 `object_deletion_outbox`

`id,user_id,document_id,storage_key varchar(500),reason,status(PENDING|PROCESSING|SUCCEEDED|DEAD),attempt_count,next_attempt_at`, `claim_token/lease_expires_at NULL`, `last_error_code varchar(100) NULL,created_at,completed_at NULL`. Active unique `(document_id,storage_key,reason)`.

상태는 `PENDING→PROCESSING→SUCCEEDED|DEAD`이고 lease가 만료된 PROCESSING은 PENDING으로 회수한다. retry는 1분, 5분, 30분, 2시간, 12시간, 이후 24시간이며 최대 10회 뒤 `DEAD`+운영 경보다. storage key와 provider 오류는 client에 노출하지 않는다.

## 5. 회사·공고·분석·취업 가이드

### 5.1 `companies`

전역 table: `id`, `normalized_name/display_name varchar(200) NOT NULL`, `official_website varchar(2000) NULL`, timestamps. `lower(normalized_name)` unique.

### 5.2 `job_postings`

`id,user_id`, `company_id uuid NULL`, `source_url/canonical_url varchar(2000)`, `title/position_name varchar(300) NULL`, `role_category/employment_type varchar(100) NULL`, `location varchar(200) NULL`, `description_text text NULL CHECK <=200000`, `description_source varchar(30) NULL`, `deadline_at NULL`, `deadline_source`, `deadline_confidence numeric(4,3) NULL`, 두 상태 축, `submitted_at/closed_at NULL`, `closed_reason varchar(30) NULL`, 내부 `content_hash char(64) NULL`, `latest_agent_run_id uuid NULL`, `posting_year integer NOT NULL CHECK 2000..9999`, `posting_half varchar(20) NOT NULL`, `version`, timestamps, `deleted_at NULL`.

- active partial unique `(user_id,canonical_url) WHERE deleted_at IS NULL`.
- `created_at`을 공고 시작 시각으로 사용하며 `Asia/Seoul` 현지 날짜의 1~6월은 `FIRST_HALF`, 7~12월은 `SECOND_HALF`로 분류한다. 기존 row도 같은 기준으로 backfill하고 insert·`created_at` 변경 trigger가 직접 SQL 경로에도 같은 불변식을 적용한다.
- 사용자별 active 기간 선택지 조회를 위한 `(user_id, posting_year DESC, posting_half DESC) WHERE deleted_at IS NULL` partial index를 둔다.
- create request에 usable `description_text`가 있으면 `description_source=USER_ENTERED`, `extraction_status=MANUAL_INPUT_PROVIDED`, `latest_agent_run_id=NULL`이며 extraction run을 만들지 않는다. 본문이 없으면 `extraction_status=QUEUED`와 `JOB_POSTING_EXTRACTION` run을 같은 transaction에서 만든다.
- `submitted_at`은 최초 SUBMITTED에서 설정하고 영구 보존한다. reopen은 현재 `closed_at/closed_reason`만 null로 만들며 history는 보존한다.
- status 변경과 history는 한 transaction, Scheduler도 업무 status만 변경한다.

### 5.3 `job_auto_analysis_requests`

`id,user_id,job_posting_id,job_version,job_content_hash char(64),quality_mode=BALANCED,status(PENDING|CLAIMED|LAUNCHED|BLOCKED|SUPERSEDED),attempt_count,claim_token/lease_expires_at NULL,next_attempt_at,agent_run_id NULL,error_code/error_message_safe NULL,created_at,updated_at,completed_at NULL`.

- unique `(user_id,job_posting_id,job_version)`으로 같은 공고 revision의 자동 접수를 최대 한 번만 claim한다. `id`는 launch할 `JOB_ANALYSIS` Agent Run ID로 재사용해 process crash 뒤에도 같은 run을 조회·재연결한다.
- `PENDING→CLAIMED→LAUNCHED|BLOCKED|SUPERSEDED`만 허용하며 만료된 `CLAIMED` lease는 reconciliation이 다시 claim한다. 일시 오류는 최대 3회와 고정 지연으로 `PENDING`에 되돌리고 이후 안전한 `BLOCKED`로 종료한다.
- usable 수동 본문 생성, URL extraction domain apply, 사용자 본문 보완·수정 transaction이 후속 의도를 함께 삽입한다. `NEEDS_MANUAL_INPUT`처럼 usable 본문이 없으면 row를 만들지 않는다.
- Agent Run 생성과 Provider 호출은 공고 transaction 밖에서 수행한다. 예산·본문 prerequisite·일시 오류는 공고 및 extraction 결과를 보존한다.
- owner/job 복합 FK와 nullable owner/run 복합 FK를 사용하며 raw provider 오류·prompt·본문을 저장하지 않는다.

### 5.4 `job_status_history`

`id,user_id,job_posting_id`, `from_status varchar(30) NULL`, `to_status,reason varchar(100),changed_by(USER|SCHEDULER|SYSTEM),changed_at`; 복합 parent FK.

### 5.5 `career_guide_posts`

사용자 소유 데이터가 아닌 전역 읽기 콘텐츠다. `id`, unique `slug varchar(120)`, `status`, `display_order int >=0`, `category varchar(60)`, `title varchar(200)`, `summary varchar(500)`, `body text <=10000`, `published_at NULL`, `version bigint >=0`, timestamps를 가진다.

- `PUBLISHED`는 `published_at`이 반드시 존재하며 사용자 조회는 `status=PUBLISHED AND published_at<=now`만 반환한다.
- 사용자 조회 정렬은 `display_order ASC, published_at DESC, id ASC`다.
- V17은 최초 게시 콘텐츠 5개를 안전한 seed로 제공하고 V18은 version 1·미수정 seed만 장문 본문으로 갱신해 version 2로 올린다. 관리자 화면과 mutation API는 현재 범위가 아니다.

### 5.6 `job_analyses`와 rubric

`job_analyses`: `id,user_id,job_posting_id,analysis_version`, 내부 job/profile/evidence hash, `eligibility`, `fit_score numeric(5,2) NULL CHECK 0..100`, `analysis_coverage numeric(5,2) NULL CHECK 0..100`, `analysis_summary varchar(10000) NULL`, `rubric_version`, `agent_run_id`, `created_at`; unique `(user_id,job_posting_id,analysis_version)`. `analysis_coverage`의 `NULL`은 이전 rubric 결과, `fit_score NULL + analysis_coverage=0.00`은 모든 criterion이 `UNKNOWN`인 v2 결과다.

`job_analysis_score_criteria`: `id,user_id,job_analysis_id,category,criterion varchar(2000)`, `weight numeric(5,2) CHECK 0..100`, `match_level`, `score numeric(5,2) CHECK 0..weight`, `explanation varchar(2000)`, `source_location varchar(500) NULL`.

`job_analysis_evidence_links`: `id,user_id,job_analysis_id,score_criterion_id NULL,profile_evidence_id,usage_type,created_at`; 모든 복합 FK. requirements·strength/gap snapshot JSON은 display용이고 provenance 원천은 link다.

`job_analysis_structured_fact_links`: `id,user_id,job_analysis_id,score_criterion_id NULL,source_entity_id,source_entity_version,fact_type,fact_hash,usage_type,created_at`. `PRIMARY_EDUCATION`, `EXPECTED_GRADUATION_DATE`, `WORK_AVAILABLE_DATE`, `MILITARY_STATUS`, `OVERSEAS_TRAVEL_ELIGIBILITY`, `EMPLOYMENT_DISQUALIFICATION_STATUS` provenance만 저장한다. 기존 evidence link 의미를 확장하지 않으며 analysis seal 이후 immutable이다.

가중치는 40/30/15/10/5이며 `Eligibility`와 점수는 별도다. v2에서 `UNKNOWN` criterion은 weight·score 0이고 점수 분모에서 제외되며, 전체 공고 criterion 기준 판정 비율은 `analysis_coverage`에 저장한다. 성공한 analysis는 criterion을 최소 1개 가지며 criterion을 추출하지 못하면 analysis row 없이 run을 `INSUFFICIENT_JOB_DATA`로 실패시킨다. stale 여부·reason은 저장 enum이 아니라 current hash 비교 projection이다.

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

`id,user_id,workflow_type,status,current_step NULL`, `progress_percent integer CHECK 0..100`, `workflow_version,input_hash,budget_policy_version,requested_quality_mode NULL,highest_model_tier_used NULL`, `estimated_cost_usd/reserved_cost_usd/actual_cost_usd numeric(12,6) CHECK >=0`, `retry_of_run_id NULL,root_run_id,run_attempt_no`, `error_code varchar(100) NULL,error_message_safe varchar(500) NULL,partial_result_json NULL,claim_token NULL,claimed_by NULL,lease_expires_at NULL,heartbeat_at NULL,cancel_requested_at NULL,waiting_reason NULL,state_version,queued_at,started_at NULL,completed_at NULL,updated_at,deleted_at NULL`.

자기소개서 생성·검증 v4는 `requested_quality_mode=NULL`이고 선택한 exact model ID를 기존 `input_reference_snapshot.model`에 저장한다. 별도 mutable model column을 추가하지 않으며 retry와 step routing은 이 immutable snapshot을 사용한다. v1~v3 run의 `requested_quality_mode`는 durable replay 호환성을 위해 유지한다.

Structured output 진단은 기존 run/step `error_code`에 값 없는 stable phase/reason code(`AI_SO_JSON_*`, `AI_SO_SCHEMA_*`, `AI_SO_JAVA_*`, `AI_SO_WORKFLOW_*`)로 저장한다. raw Provider response, 실제 invalid value, Jackson message, prompt와 문서 원문은 저장하지 않으며 이를 위한 새 column이나 migration을 추가하지 않는다.

`partial_result_json.failedScopeKeys`는 자기소개서 문항처럼 독립적으로 완료되지 못한 실제 scope에만 사용한다. 문서 evidence candidate filtering은 이 배열에 기록하지 않으며 적용 evidence ID만 `resultRefs`로 보존할 수 있다. 문서 apply step의 기존 `output_json`에는 candidate/applied/rejected count와 stable rejection reason별 count만 저장하고 candidate 값·chunk UUID·문서 원문은 저장하지 않는다. 이 계약은 기존 column으로 충족하므로 별도 migration을 요구하지 않는다.

- retry predecessor 복합 FK와 root lineage를 보존하고 `UNIQUE(user_id,retry_of_run_id) WHERE retry_of_run_id IS NOT NULL`로 모든 resource/generic retry 진입점이 predecessor당 successor를 하나만 만들게 한다. 호환되는 후속 retry는 같은 successor를 반환하고 option 충돌은 거부한다.
- claim은 조건부 update 또는 `FOR UPDATE SKIP LOCKED`, heartbeat 15초, lease 60초, reconciliation 30초다.
- lease가 만료된 RUNNING row는 immutable `INTERRUPTED`다.
- user retry는 새 run, WAITING_USER resume은 같은 run이다.
- cancel terminal 처리와 processing resource의 마지막 안정 상태 복원은 한 transaction이다. Job extraction은 usable source가 있으면 `EXTRACTED|MANUAL_INPUT_PROVIDED`, 없으면 `NEEDS_MANUAL_INPUT`; document parse는 같은 revision의 committed text/chunk가 있으면 `PARSED`, 없으면 `UPLOADED`; evidence extraction은 같은 revision의 prior 성공 snapshot이 있으면 `SUCCEEDED`, 없으면 `NOT_STARTED`로 복원한다.
- interview preparation cancel은 research run을 `CANCELLED`로 끝내고 preallocated question set은 질문·source link 없는 read-only cancelled 결과로 남긴다. interview answer feedback은 row를 만들지 않고, mock feedback은 session `feedback_status=CANCELLED`, cover verification은 연결 PENDING verification을 `FAILED`로 종결한다. reconciliation도 terminal run과 processing resource 불일치를 같은 mapping으로 복구한다.
- `deleted_at`은 terminal 상태와 non-null `completed_at`을 가진 row에만 설정한다. owner-visible 조회는 `deleted_at IS NULL`만 반환하며 history delete는 run/step, retry/root lineage, typed resource link, idempotency, budget reservation·usage를 물리 삭제하지 않는다.

### 9.3 `agent_run_resource_links`

`id,user_id,agent_run_id,resource_kind,document_id NULL,job_posting_id NULL,job_analysis_id NULL,cover_letter_id NULL,cover_letter_answer_version_id NULL,research_run_id NULL,question_set_id NULL,interview_answer_version_id NULL,mock_session_id NULL,created_at`을 저장한다. resource column은 정확히 하나만 non-null이고 각 column은 대상의 `(user_id,id)`에 복합 FK를 둔다. run당 primary resource link는 최대 하나이며 공개 `resourceType/resourceId`는 이 link에서 계산한다. idempotency record의 같은 이름 field는 replay metadata일 뿐 authoritative owner relation이 아니다.

### 9.4 `agent_steps`

`id,user_id,agent_run_id,step_key varchar(100),scope_key varchar(100) NULL,step_order,agent_name varchar(150),status,attempt,max_attempts 1..3,input_hash,output_hash NULL,input_refs jsonb,output_json jsonb NULL,output_schema_version,model_policy_version,prompt_version,reused_step_id NULL,error_code varchar(100) NULL,error_message_safe varchar(500) NULL`, `created_at,started_at NULL,completed_at NULL,updated_at`.

Unique `(user_id,agent_run_id,step_key,scope_key,attempt)`. `output_json`에는 result ref·hash·validation summary만 저장하며 원문·prompt/provider response를 저장하지 않는다.

## 10. AI policy·가격·예산·usage

### 10.1 versioned policy

`ai_model_policies`, `embedding_policy_versions`, `ai_budget_policy_versions`는 immutable version을 가진 전역 policy다. embedding active 값은 provider `OpenAI`, model `text-embedding-3-small`, dimension `1536`, cosine, active generation이다. budget policy는 전체 AI 사용량의 단일 일일 비용 한도와 reset zone을 한 version으로 묶는다. 공개 quality와 내부 tier를 별도 column으로 저장한다.

`user_ai_preferences`: `id,user_id,default_quality_mode(ECONOMY|BALANCED),high_quality_enabled,version,timestamps`; user당 active 1개. Provider 비용 예산은 사용자 preference와 분리해 전역 versioned 운영 policy에서 관리한다.

### 10.2 immutable 가격·ledger·reservation

- `ai_price_versions`: immutable catalog header와 effective range.
- `ai_price_items`: provider, product, unit, unit_price와 price version. 외부 provider 단가를 이 명세에 금액으로 고정하지 않는다.
- 자기소개서 allowlist의 각 exact OpenAI chat model은 활성 price version에 `CHAT_INPUT|CHAT_CACHED_INPUT|CHAT_OUTPUT` item이 모두 있어야 하며, 모델 추가·폐기는 새 Flyway migration과 새 immutable price version으로 수행한다.
- `ai_budget_ledgers`: `id,budget_date,budget_zone,spent_usd,reserved_usd,policy_version`; unique date/zone인 전역 원장이다. 기존 사용자별 원장은 migration에서 날짜·zone별 합계로 병합하고 reservation 연결을 전역 원장으로 이전한다.
- `ai_budget_reservations`: `id,user_id,operation_type,agent_run_id NULL,mock_turn_id NULL,reserved_usd,settled_usd,status,expires_at,budget_policy_version,price_version,timestamps`.
- `ai_usage_records`: `id,user_id,agent_run_id NULL,agent_step_id NULL,mock_session_id NULL,mock_turn_id NULL,operation_type,usage_type(CHAT|EMBEDDING|SEARCH),provider,product,model_tier,unit counts,price_version,price_item_id,provider_call_id NULL,cost_usd,duration_ms,created_at`.

chat input/cached input/output, embedding unit, BASIC/ADVANCED search를 가격 item별 별도 row로 기록한다. 같은 provider 호출의 row는 `provider_call_id + price_item_id`로 중복 저장을 차단한다. 무료/cache hit도 0 cost usage row를 남긴다. 동기 mock turn usage는 run/step FK가 null이고 session/turn 복합 FK가 필수다.

현재 상한은 전체 AI 기능이 공유하는 일일 USD 10.00이다. 분야·run·turn·session별 비용 상한은 두지 않으며 값은 환경 변수가 아니라 versioned policy로 관리한다.

### 10.3 제품 기능 한도·metering (`PLANNED` P8.6)

Provider USD budget table을 제품 사용 횟수에 재사용하지 않는다.

- `feature_usage_policy_versions`: immutable version, key, effective range, reset zone `Asia/Seoul`, created metadata.
- `feature_usage_policy_items`: policy version, canonical feature key, limit quantity nullable, unlimited, period type, consumption policy; unique version/feature.
- `user_feature_usage_assignments`: user별 active policy version과 effective range; 겹치는 active assignment 금지.
- `user_feature_usage_overrides`: user, feature, limit/unlimited, effective range, reason, actor/audit ref, optimistic version.
- `feature_usage_periods`: user, feature, policy/override snapshot, period start/end, committed/reserved quantity, version; unique user/feature/period.
- `feature_usage_reservations`: user, feature, period, idempotency/client request hash, Agent Run/resource/turn ref, status, quantity, expires/timestamps; logical request unique.
- `feature_usage_events`: append-only commit/release/adjustment event, reservation, user, feature, quantity, outcome, workflow/resource/run/client request ref, occurredAt.

원자 불변식:

- limited period의 `committed_quantity + reserved_quantity <= limit_quantity`.
- reservation은 `RESERVED→COMMITTED|RELEASED|EXPIRED` 한 방향이다.
- idempotency replay와 terminal replay는 같은 reservation/event를 사용한다.
- Provider 호출 전 실패는 release, Provider 호출 후 실패·취소/partial success와 새 사용자 의도의 reuse는 commit한다.
- 자동 retry는 같은 reservation, 새 client request ID retry는 새 reservation이다.
- 탈퇴 purge는 owner 상세을 제거하고 승인된 비식별 aggregate만 남긴다.

### 10.4 과금 가능 usage policy·집계 (`PLANNED` P8.7)

별도 `billing_usage_events`는 만들지 않는다. `feature_usage_events`의 commit snapshot이 다음 field를 가진다.

```text
billing_policy_version
billing_policy_item_id
billable_quantity
billing_unit
charge_mode(METERED_ZERO_RATE|NO_CHARGE)
```

- `billing_policy_versions/items`는 immutable zero-rate mapping이다.
- 고객 청구 금액, plan, subscription, payment, invoice, refund와 tax table은 없다.
- 내부 원가 source는 `ai_usage_records`, 제품·과금 가능 unit source는 `feature_usage_events`다.
- 초기 집계는 raw source에 index를 둔 SQL read model이다. aggregate table은 실제 대표 query p95/raw scan 임계 초과 증거 뒤 별도 migration으로만 추가한다.
- reconciliation은 Agent Run actual cost↔usage, period committed↔feature event, billing snapshot↔policy item을 비교하고 원본을 덮어쓰지 않는 finding/correction을 남긴다.

### 10.5 ADMIN·Backoffice audit (`PLANNED` P8.9-A)

- 새 migration이 `users.role` CHECK를 `USER|ADMIN`으로 확장한다. 일반 signup과 application default는 USER다.
- ADMIN provisioning은 공개 API가 아닌 배포 통제 command이며 user, before/after role, reason, actor correlation, request ID, timestamp를 append-only audit에 기록한다.
- `backoffice_access_audits`: admin user ID, access type, target user ID nullable, request ID, filter hash/summary, result count, occurredAt. 검색 원문과 민감한 query value는 저장하지 않는다.
- Backoffice read model은 기존 domain query port와 SQL projection을 사용하고 사용자 원문·prompt/response·key를 복제하지 않는다.
- P8.9-B mutation audit schema는 별도 승인 전 만들지 않는다.

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
4. Agent Run은 0원 reservation으로 접수하고 각 Provider 호출 직전에 최악 비용을 원자 reserve한다. catalog version으로 settle하고 terminal·WAITING_USER에 미사용액을 release한다.
5. Object upload 성공 뒤 DB 실패는 보상 삭제, DB logical delete 뒤 Object 삭제는 Outbox다.
6. 질문은 soft delete하고 answer version·verification·provenance를 보존한다.
7. document delete는 API 즉시 404, Object/text/chunk/embedding purge, 참조 evidence tombstone, 미참조 evidence 삭제다.
8. boot 시 configured embedding model dimension과 `vector(1536)` typmod가 다르면 fail fast한다.
9. live chunk 50,000개 이상 또는 대표 query p95가 200ms를 초과할 때만 별도 migration으로 HNSW를 검토한다.
10. Agent Run history delete는 terminal row의 `deleted_at`만 원자 갱신하고 audit·lineage FK row는 보존한다.
11. model·dimension 변경은 새 generation과 typed vector column/index 생성→backfill→검증→active switch→cleanup 순서다.

## 13. GitHub Source (`IMPLEMENTED`, V27)

이 절은 [`../design/github-career-artifact-design.md`](../design/github-career-artifact-design.md)의 목표 schema이며 [`V27__create_github_source_ingestion.sql`](../../backend/src/main/resources/db/migration/V27__create_github_source_ingestion.sql)에서 구현됐다. V26과 이전 migration은 수정하지 않았다.

### 13.1 `github_sources`

`id,user_id`, `source_kind varchar(20)`, `account_type varchar(20) NULL`, `original_url varchar(500)`, `canonical_url varchar(500)`, `owner_login varchar(100)`, `repository_name varchar(100) NULL`, `source_status varchar(30)`, `repository_discovery_truncated boolean DEFAULT false`, `latest_agent_run_id uuid NULL`, `source_revision bigint DEFAULT 0`, `last_successful_sync_at NULL`, `version`, timestamps, `deleted_at NULL`.

- active `UNIQUE(user_id,canonical_url) WHERE deleted_at IS NULL`이며 canonical URL은 application에서 `https://github.com/{owner}[/{repository}]` 형태로만 만든다.
- `ACCOUNT`는 `account_type IS NOT NULL AND repository_name IS NULL`, `REPOSITORY`는 `account_type IS NULL AND repository_name IS NOT NULL`이다.
- `source_revision>=0`, optimistic `version>=0`을 분리한다. refresh 접수는 aggregate lock 아래 revision과 version을 각각 증가시킨다.
- account discovery는 최근 push 기준 최대 200개 link만 저장하고 초과 여부를 `repository_discovery_truncated`로 보존한다. repository source는 false다.
- `latest_agent_run_id`는 resource link 생성 뒤 같은 사용자 Run을 가리키며 source 삭제가 Run history를 삭제하지 않는다.
- 일반 조회 index는 `(user_id,updated_at DESC,id DESC) WHERE deleted_at IS NULL`, worker/reconciliation index는 `(source_status,updated_at)`이다.

### 13.2 repository·선택

`github_repositories`: `id,user_id,external_repository_id bigint,node_id varchar(100),owner_login varchar(100),repository_name varchar(100),canonical_url varchar(500),default_branch varchar(255),is_private boolean,is_fork boolean,is_archived boolean,description varchar(500) NULL,pushed_at NULL`, timestamps.

- `UNIQUE(user_id,external_repository_id)`와 `UNIQUE(user_id,id)`를 두며 최초 vertical은 `is_private=false`만 source selection에 허용한다.
- rename은 external ID 기준으로 metadata와 canonical URL을 갱신하되 이전 immutable snapshot identity는 유지한다.

`github_source_repository_links`: `id,user_id,github_source_id,github_repository_id,selected boolean,selection_order integer NULL,discovered_at,updated_at`.

- 두 parent에 `(user_id,id)` 복합 FK, `UNIQUE(user_id,github_source_id,github_repository_id)`를 둔다.
- selected이면 `selection_order 1..10`, 아니면 null이고 active source별 selected order는 unique다.
- account selection command는 source row lock 아래 전체 selected 집합을 원자 교체한다. repository source는 정확히 한 링크가 selected다.

### 13.3 immutable snapshot·source unit

`github_repository_snapshots`: `id,user_id,github_repository_id,commit_sha char(40),tree_sha char(40) NULL,github_api_version varchar(30),retrieval_policy_version varchar(80),selection_complete boolean,upstream_truncated boolean,snapshot_storage_key varchar(500) UNIQUE,checksum_sha256 char(64),sanitized_bytes bigint,captured_at`.

- `UNIQUE(user_id,github_repository_id,commit_sha,retrieval_policy_version)`이며 update를 금지하는 immutable row다.
- SHA와 checksum은 lowercase hexadecimal, `sanitized_bytes 1..4000000`이다. API별 Unicode code point 상한은 DB byte 상한보다 작게 application에서 강제한다.
- snapshot object는 private storage의 gzip JSON이며 DB에는 raw file content를 저장하지 않는다.

`github_source_units`: `id,user_id,snapshot_id,unit_type varchar(40),repository_path varchar(1000),blob_sha char(40) NULL,language varchar(80) NULL,line_start/line_end integer NULL,content_hash char(64),excerpt varchar(500),snapshot_ordinal integer,created_at`.

- snapshot 복합 FK, `UNIQUE(user_id,snapshot_id,snapshot_ordinal)`, `UNIQUE(user_id,id)`를 둔다.
- line은 둘 다 null이거나 `1<=line_start<=line_end`; path는 정규화한 relative path이며 `..`, NUL과 backslash를 허용하지 않는다.
- excerpt는 secret masking과 source selection을 통과한 짧은 표시용 문자열이다. 전체 repository text와 prompt는 row에 저장하지 않는다.

`github_evidence_unit_links`: `id,user_id,profile_evidence_id,source_unit_id,relation_kind,created_at`.

- evidence와 unit에 owner 복합 FK, `UNIQUE(user_id,profile_evidence_id,source_unit_id,relation_kind)`를 둔다.
- 같은 evidence에는 `PRIMARY`가 최소 1개 있어야 하며 application apply transaction과 deferred validation으로 보장한다.

### 13.4 canonical 경험 연결

`profile_evidence`에 `github_repository_id uuid NULL`, `github_snapshot_id uuid NULL`, `github_claim_key char(64) NULL`을 additive하게 추가하고 `source_type` CHECK에 `GITHUB_REPOSITORY`를 추가한다.

- GitHub shape는 `source_type=GITHUB_REPOSITORY`, `source_entity_id=github_repository_id`, `document_id IS NULL`, 세 GitHub column non-null이다.
- 비 GitHub shape는 세 GitHub column이 모두 null이다.
- repository와 snapshot에 owner 복합 FK를 두고 snapshot이 해당 repository의 row인지 trigger 또는 composite alternate key FK로 강제한다.
- active raw claim은 `UNIQUE(user_id,github_repository_id,github_claim_key) WHERE source_type='GITHUB_REPOSITORY' AND source_deleted_at IS NULL`이다.
- canonical 적용은 기존 `experience_items`, `experience_evidence_links`, `experience_item_embeddings`의 fingerprint·0.94/0.82 정책과 사용자 advisory lock을 그대로 사용한다. GitHub 전용 canonical table이나 별도 threshold를 만들지 않는다.
- 신규·유사·충돌 항목은 `PENDING`; `SAME_EXPERIENCE`는 새 item 없이 기존 item의 `CORROBORATING` link만 추가한다.

### 13.5 Snapshot Object 삭제

`github_snapshot_object_deletion_outbox`: `id,user_id,github_source_id uuid NULL,snapshot_id uuid NULL,storage_key varchar(500),reason varchar(50),status,attempt_count,next_attempt_at,claim_token/lease_expires_at NULL,last_error_code varchar(100) NULL,created_at,completed_at NULL`.

- status와 claim/lease 규칙은 기존 `object_deletion_outbox`와 같고 active `UNIQUE(storage_key,reason) WHERE status IN ('PENDING','PROCESSING')`를 둔다.
- 정상 source 삭제는 source/snapshot ID를 보존하고, DB 반영 전 orphan upload 보상은 둘 다 null일 수 있다. storage key는 사용자 ID prefix 검증을 통과해야 한다.
- source soft delete, GitHub raw evidence tombstone/삭제, orphan canonical 정리는 한 DB transaction이고 object 삭제는 outbox worker가 수행한다.
- 승인된 canonical item은 source 삭제 뒤에도 유지한다. 참조된 GitHub raw evidence는 `SOURCE_DELETED` 최소 tombstone, 미참조 raw evidence와 다른 active source가 없는 미승인 orphan item은 제거한다.

## 14. Career Artifact (`PLANNED`, phase 미배정)

### 14.1 `career_artifacts`

`id,user_id`, `artifact_type varchar(20)`, `title varchar(120)`, `lifecycle_status varchar(20)`, `current_version_id uuid NULL`, `latest_agent_run_id uuid NULL`, `version`, timestamps, `deleted_at NULL`.

- `current_version_id`는 같은 `(user_id,artifact_id)`의 성공 immutable version만 가리킨다. circular FK는 version table 생성 뒤 추가하고 deferred constraint trigger로 parent 일치를 강제한다.
- generation 진행·실패 상태는 별도 mutable column에 복제하지 않고 latest linked Agent Run에서 projection한다.
- 실패·취소·중단한 재생성은 `current_version_id`를 바꾸지 않는다.
- 목록 index는 `(user_id,updated_at DESC,id DESC) WHERE deleted_at IS NULL`, type filter index는 `(user_id,artifact_type,updated_at DESC) WHERE deleted_at IS NULL`이다.

### 14.2 `career_artifact_versions`

`id,user_id,career_artifact_id,version_no integer,content_schema_version varchar(80),content_json jsonb,template_key varchar(80),template_version varchar(40),model_id varchar(64),agent_run_id uuid,render_profile_snapshot jsonb,storage_key varchar(500) UNIQUE,mime_type varchar(100),size_bytes bigint,checksum_sha256 char(64),created_at`.

- immutable row이며 `UNIQUE(user_id,career_artifact_id,version_no)`, `UNIQUE(user_id,id)`와 owner 복합 FK를 둔다. `version_no>=1`, `size_bytes>0`이다.
- artifact type과 MIME은 RESUME/DOCX, PORTFOLIO/PPTX 조합만 허용하고 storage key extension도 같은 조합인지 application과 integration test에서 검증한다.
- strict structured output, renderer 완료, OOXML 재개방·관계·overflow 검증, private object upload가 모두 성공한 뒤에만 row를 생성한다.
- `content_json`은 안전한 HTML preview projection의 source이며 schema version으로 검증한다. prompt/provider response, OOXML byte와 raw evidence 원문은 넣지 않는다.
- `render_profile_snapshot`은 최종 파일에 실제 삽입한 최소 display/contact/link와 포함 여부만 보존한다. LLM input, log, analytics에 복제하지 않고 artifact/account 삭제 시 함께 purge한다.

### 14.3 version provenance

`career_artifact_evidence_links`: `id,user_id,artifact_version_id,experience_item_id,profile_evidence_id,experience_version bigint,evidence_version bigint,usage_type,title_snapshot varchar(250),content_snapshot varchar(20000),snapshot_hash char(64),created_at`.

- version, experience, evidence에 owner 복합 FK를 두고 `UNIQUE(user_id,artifact_version_id,experience_item_id,profile_evidence_id,usage_type)`를 둔다.
- 입력은 접수와 workflow 실행 시 모두 active `VERIFIED` canonical experience인지 검증한다.
- snapshot은 생성 당시 fact check와 재현을 위한 immutable provenance다. GitHub raw source unit이나 문서 chunk를 직접 연결하지 않는다.
- artifact version 저장, provenance link 삽입과 `career_artifacts.current_version_id/latest_agent_run_id/version` 갱신은 한 transaction이다.

### 14.4 Artifact Object 삭제

`career_artifact_object_deletion_outbox`: `id,user_id,career_artifact_id uuid NULL,artifact_version_id uuid NULL,storage_key varchar(500),reason varchar(50),status,attempt_count,next_attempt_at,claim_token/lease_expires_at NULL,last_error_code varchar(100) NULL,created_at,completed_at NULL`.

- status·lease와 active storage key/reason unique는 13.5와 같다. DB apply 전 orphan upload에서는 artifact/version ID가 null일 수 있다.
- artifact delete는 즉시 owner API에서 404가 되고 모든 version object를 outbox에 넣는다. 과거 성공 version은 aggregate가 active인 동안 current가 아니어도 다운로드 가능하다.
- account deletion task는 GitHub snapshot outbox와 artifact outbox가 terminal 성공한 뒤 owner row를 purge한다.

### 14.5 Agent Run·멱등성 확장

- V27은 `agent_runs.workflow_type` CHECK에 `GITHUB_INGESTION`을 additive하게 추가했다. `RESUME_GENERATION|PORTFOLIO_GENERATION`은 planned다.
- V27은 `agent_run_resource_links`에 `github_source_id uuid NULL`, owner 복합 FK, exactly-one resource column CHECK와 `GITHUB_SOURCE` parity를 추가했다. `career_artifact_id|CAREER_ARTIFACT`는 planned다.
- GitHub retry successor는 같은 source에 정확히 하나 연결한다. Career Artifact retry 계약은 planned다.
- GitHub source 생성·선택·refresh와 artifact 생성·재생성은 기존 `idempotency_records`를 사용한다. request hash에는 canonical URL 또는 선택 ID/version/model/template/evidence version을 포함하고 renderer-only 연락처 원문은 넣지 않는다.
- GitHub snapshot fetch와 artifact render/upload는 transaction 밖에서 수행한다. apply transaction은 owner, source/artifact revision, active policy/model, evidence version, input/output hash를 재검증한다.

## 15. 향후 migration 책임

현재 latest implemented migration은 GitHub Source ingestion을 추가한 V27이다. 적용된 V1~V27은 수정하지 않는다. 아래 `PLANNED` 번호는 예약값이 아니며 실제 착수 직전 latest migration을 다시 확인한다. schema 변경이 없는 phase는 번호를 소비하지 않는다.

| 순서 책임                    | 목표 영역                                                                        |
| ---------------------------- | -------------------------------------------------------------------------------- |
| identity/session/idempotency | users, profile base, Spring Session ownership, idempotency, 독립 deletion task   |
| structured profile           | profile 5종, direct evidence, version·서버 계산 최종 학력 unique                 |
| Agent runtime/budget         | run/step claim·retry·cancel, policy, price, ledger, reservation, usage           |
| documents/evidence           | 두 상태 축, owner FK, text/chunk, vector(1536), Object outbox                    |
| jobs/analysis                | canonical active unique, 두 상태 축, history, rubric·provenance                  |
| cover letter                 | active partial unique, soft question, immutable answer/content/link/verification |
| research/interview           | combined research, source links, answer/feedback, mock turn/message/feedback     |
| additional implemented V15   | 사용자 직접 대외활동, ACTIVITY source와 direct evidence 불변식                   |
| additional implemented V16   | 공고 revision별 자동 분석 의도, lease reconciliation, Agent Run 연결             |
| additional implemented V17   | 전역 취업 준비 가이드 게시 상태·노출 순서·초기 콘텐츠                           |
| additional implemented V18   | 미수정 초기 가이드 5개의 장문 본문·content version 2 보강                        |
| additional implemented V19   | 프로필 지원 자격과 구조화 사실 provenance                                        |
| additional implemented V20   | 공고 분석 evidence coverage                                                       |
| additional implemented V21   | 공고 상·하반기 분류                                                               |
| additional implemented V22   | 공고 기간 제약 최종화                                                             |
| additional implemented V23   | 선택 가능한 OpenAI exact model 가격                                               |
| additional implemented V24   | 분야별 예산을 전역 일일 budget ledger로 교체                                      |
| additional implemented V25   | 취업 준비 가이드 본문 전면 개편                                                   |
| additional implemented V26   | canonical 경험·근거 link·embedding·문서 candidate apply                          |
| additional implemented V27   | GitHub source/repository/snapshot/unit/evidence/outbox와 typed Run link           |
| Career Artifact (`PLANNED`)   | 착수 시 next available; artifact/version/provenance/outbox/run link               |
| P8.6 (`PLANNED`)              | feature policy/assignment/override/period/reservation/event; 착수 시 next available |
| P8.7 (`PLANNED`)              | immutable billing policy, feature billing snapshot 제약, 집계 index              |
| P8.8                          | DB 변경 없음; safe code→failure presentation mapping은 code 계약                 |
| P8.9-A (`PLANNED`)            | USER/ADMIN role 확장, provisioning/access audit; 착수 시 next available          |
| P8.9-B                        | 번호 예약 없음; 실제 승인·착수 시 next available                                 |
| P9                            | 선행 승인 범위 완료 뒤 next available                                             |
| vector index 조건부           | 측정 기준을 넘을 때만 HNSW                                                       |

각 migration은 owner composite FK·unique·CHECK를 같은 단계에서 만들고 빈 DB와 직전 production-like schema upgrade를 검증한다.
