# Flyway migration 안내

## 디렉터리 목적

`backend/src/main/resources/db/migration/`은 PostgreSQL schema의 순차적 변경 이력을 Flyway naming 규칙으로 관리한다.

## 주요 파일 및 하위 디렉터리

| 경로                                                                                                                             | 역할                                                      |
| -------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| [`V1__enable_extensions.sql`](V1__enable_extensions.sql)                                                                         | pgvector의 `vector` PostgreSQL extension 활성화           |
| [`V2__create_identity_session_idempotency.sql`](V2__create_identity_session_idempotency.sql)                                     | users·기본 profile·Spring Session·idempotency schema      |
| [`V3__create_structured_profiles_and_direct_evidence.sql`](V3__create_structured_profiles_and_direct_evidence.sql)               | P2 기본·구조화 프로필과 직접 근거 schema·불변식           |
| [`V4__create_agent_runtime_and_ai_budget.sql`](V4__create_agent_runtime_and_ai_budget.sql)                                       | P3 Run·Step, AI policy·price·budget·usage schema          |
| [`V5__create_documents_evidence_and_storage_outbox.sql`](V5__create_documents_evidence_and_storage_outbox.sql)                   | P4 Document·chunk·typed Run link·Object outbox            |
| [`V6__create_job_postings_and_extend_agent_resources.sql`](V6__create_job_postings_and_extend_agent_resources.sql)               | P5 Company·Job·status history와 typed Job Run link        |
| [`V7__create_job_analyses_and_provenance.sql`](V7__create_job_analyses_and_provenance.sql)                                       | P6 immutable analysis·criterion·evidence·Run lineage      |
| [`V8__create_cover_letters_versions_and_verifications.sql`](V8__create_cover_letters_versions_and_verifications.sql)             | P7 Cover Letter·question·version·verification·Run link    |
| [`V9__exclude_education_evidence_and_soft_delete_agent_runs.sql`](V9__exclude_education_evidence_and_soft_delete_agent_runs.sql) | 학력 direct evidence 제거와 Agent Run history soft delete |
| [`V10__exclude_document_education_evidence.sql`](V10__exclude_document_education_evidence.sql)                                   | 문서 추출 교육·학력 evidence 정리·DB 차단                 |
| [`V11__derive_final_education.sql`](V11__derive_final_education.sql)                                                             | 학력 단계 backfill·제약과 최종 학력 재계산                |
| [`V12__create_interview_research_questions_and_feedback.sql`](V12__create_interview_research_questions_and_feedback.sql)         | P8 조사·질문·답변 version·feedback·typed Run link         |
| [`V13__add_external_ai_provider_price_catalog.sql`](V13__add_external_ai_provider_price_catalog.sql)                             | P8.5 provider call identity와 immutable 가격 catalog      |
| [`V14__canonicalize_openai_embedding_policy.sql`](V14__canonicalize_openai_embedding_policy.sql)                                 | OpenAI embedding provider key canonical 정책 전환         |
| [`V15__create_user_activities.sql`](V15__create_user_activities.sql)                                                             | 사용자 직접 대외활동과 ACTIVITY evidence 불변식           |

현재 하위 디렉터리는 없다. 향후 migration도 특별한 분리 요구가 없으면 이 위치에 순차적으로 둔다.

## 구성 요소 역할

- V1은 `CREATE EXTENSION IF NOT EXISTS vector`만 실행하며 P1에서도 byte 단위로 보존한다.
- V2는 P1에 필요한 users, user_profiles, Spring Session과 idempotency_records만 추가한다.
- V3는 `user_profiles` JSON·owner 제약을 보강하고 다섯 구조화 source와 `profile_evidence`를 추가한다.
- V4는 Agent Run·Step과 immutable model·embedding·budget·price version, preference, ledger·reservation·usage 11개 table을 추가한다.
- V5는 Document·revision text·`vector(1536)` chunk·Object deletion outbox·typed Run link와 profile document owner FK를 추가한다.
- V6는 Company·Job Posting·status history, owner 복합 FK, canonical active unique와 typed Job Agent Run link를 추가한다.
- V7은 immutable Job Analysis version·score criterion·VERIFIED evidence provenance와 secondary typed analysis Run link를 추가한다.
- V11은 `education_level`을 legacy degree·학교명에서 backfill하고 단계·상태·날짜 순으로 active 최종 학력을 재계산한다.
- V8은 자기소개서 active cardinality, 질문, immutable answer version·evidence provenance·verification·acknowledgement와 typed Run resource를 추가한다.
- V9는 학력 evidence 동기화 의무를 제거하고 기존 학력 근거를 tombstone 처리하며 terminal Agent Run의 `deleted_at`을 추가한다.
- V10은 문서 추출에서 생성된 교육·학력 category도 tombstone 처리하고 새 active row를 DB CHECK로 차단한다.
- V12는 조사 run/topic/source provenance, question set/question provenance, immutable answer version·feedback과 P8 typed Run link를 추가한다.
- V14는 과거 embedding 정책 version 1을 보존·비활성화하고 canonical `openai` provider key의 version 2를 활성화한다.
- V15는 문서와 독립된 사용자 소유 `activities`를 추가하고 명시적 소재 사용 선택을 ACTIVITY direct evidence와 같은 transaction에서 유지한다.
- P9 mock interview schema는 다음 forward migration으로 남긴다.

## 다른 디렉터리와의 의존 관계

- [`../../application.yml`](../../application.yml)이 Flyway를 활성화하고 JPA schema 검증보다 먼저 migration을 적용한다.
- 로컬 PostgreSQL image는 루트 [`../../../../../../compose.yaml`](../../../../../../compose.yaml)에서 pgvector를 제공한다.
- migration 설계의 기준은 [`../../../../../../docs/spec/db.md`](../../../../../../docs/spec/db.md)다.

## 변경 시 주의사항

- 한 번 적용된 migration은 내용이나 순서를 수정·삭제하지 않고 새 버전 파일로 보정한다.
- 빈 DB 적용과 기존 DB upgrade를 모두 검증하고 destructive 변경에는 명시적 migration·복구 계획을 둔다.
- `vector` extension 생성 권한과 대상 PostgreSQL 환경의 extension 지원 여부를 확인한다.
- 이 디렉터리의 Markdown도 classpath에 포함될 수 있으며 패키징 이슈는 [`../../progress.md`](../../progress.md)에서 추적한다.

## 관련 규칙 및 문서

- [DB 리소스 안내](../index.md)
- [Spring 백엔드 개발 규칙](../../../../../../docs/agent-rules/backend-development.md)
- [DB 명세](../../../../../../docs/spec/db.md)
- [Migration 진행 상황](progress.md)
