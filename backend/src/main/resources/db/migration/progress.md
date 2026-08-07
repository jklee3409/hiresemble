# Progress

## Overview

- `V1__enable_extensions.sql`은 pgvector `vector` extension을 idempotent하게 활성화한다.
- `V2__create_identity_session_idempotency.sql`은 P1의 `users`, 기본 `user_profiles`, JDBC Session, `idempotency_records`를 생성한다.
- `V3__create_structured_profiles_and_direct_evidence.sql`은 P2의 프로필 5종과 direct evidence 및 DB 불변식을 생성한다.
- `V4__create_agent_runtime_and_ai_budget.sql`은 P3 Run·Step과 AI policy·price·budget·usage 11개 table을 생성한다.
- `V5__create_documents_evidence_and_storage_outbox.sql`은 P4 문서·text·chunk·Object outbox·typed Run link와 evidence document FK를 생성한다.
- `V6__create_job_postings_and_extend_agent_resources.sql`은 P5 Company·Job·status history와 typed Job Run link를 생성한다.
- `V7__create_job_analyses_and_provenance.sql`은 P6 immutable analysis·criterion·VERIFIED evidence provenance와 typed analysis Run link를 생성한다.
- `V8__create_cover_letters_versions_and_verifications.sql`은 P7 자기소개서·질문·immutable answer/provenance/verification과 typed Run link를 생성한다.
- `V9__exclude_education_evidence_and_soft_delete_agent_runs.sql`은 학력 evidence 동기화를 제거하고 terminal Agent Run history soft delete를 추가한다.
- `V10__exclude_document_education_evidence.sql`은 문서 교육 category 근거를 정리하고 새 active row를 차단한다.
- `V11__derive_final_education.sql`은 학력 단계를 backfill하고 active 최종 학력을 hierarchy로 재계산한다.
- `V12__create_interview_research_questions_and_feedback.sql`은 P8 조사·질문·immutable 답변·피드백과 typed Agent Run link를 생성한다.
- `V13__add_external_ai_provider_price_catalog.sql`은 실제 OpenAI·Tavily immutable 가격 항목과 provider call usage identity를 추가한다.
- `V14__canonicalize_openai_embedding_policy.sql`은 legacy 정책을 보존하고 canonical `openai` version 2를 활성화한다.
- `V15__create_activities_and_extend_profile_evidence.sql`은 사용자 직접 대외활동과 ACTIVITY evidence 불변식을 추가한다.
- `V16__create_job_auto_analysis_requests.sql`은 공고 revision별 자동 분석 intent·lease claim·retry·terminal 상태를 추가한다.
- `V17__create_career_guide_posts.sql`은 게시 상태·노출 순서·version을 가진 전역 가이드와 초기 콘텐츠 5개를 생성한다.
- `V18__expand_career_guide_content.sql`은 미수정 초기 가이드 5개의 장문 본문을 version 2로 보강한다.
- `V19__add_profile_eligibility_and_structured_fact_provenance.sql`은 지원 자격 자기신고와 분석 structured fact provenance를 추가한다.
- `V20__add_job_analysis_coverage.sql`은 rubric v2의 nullable 적합도와 분석 커버리지를 추가한다.
- `V21__classify_job_posting_periods.sql`은 기존 공고를 서울 기준 등록 연도·상하반기로 backfill한다.
- `V22__finalize_job_posting_period_constraints.sql`은 기간 trigger·NOT NULL·CHECK와 owner 기간 index를 확정한다.
- `V23__add_selectable_openai_model_prices.sql`은 선택 가능한 OpenAI chat model 10개와 기존 embedding·검색의 immutable 가격 version을 추가한다.
- `V24__replace_scoped_ai_budgets_with_global_daily_budget.sql`은 분야별 비용 상한과 ledger 사용자 소유 차원을 제거하고 전역 일일 USD 10 policy와 날짜별 단일 ledger를 추가한다.
- `V25__rewrite_career_guide_content.sql`은 취업 준비 가이드 5편의 제목·요약·본문을 해요체와 체크리스트 중심으로 다시 쓴다.
- `V26__create_canonical_experience_library.sql`은 canonical 경험·다중 문서 출처 link·`vector(1536)` embedding과 `EXPERIENCE` evidence source를 추가한다.
- P9 모의 면접 table은 구현하지 않았다.

## [2026-08-07] Session Summary (V26 canonical 경험 라이브러리)

- What was done:
  - 경험 item·source link·embedding table과 owner FK·상태 CHECK·exact cosine index를 추가하고 기존 active 문서 근거를 backfill했다.
- Key decisions:
  - 적용된 migration은 수정하지 않고 V26 forward migration으로 추가했으며 item-canonical evidence 생성은 deferrable owner FK를 사용한다.
- Issues encountered:
  - 기존 fingerprint backfill은 SQL `md5` 32자, 신규 runtime은 SHA-256 64자를 사용해 CHECK가 둘을 수용하도록 했다.
- Validation:
  - Flyway V1~V26 적용과 PostgreSQL Document 통합 테스트에서 schema·backfill 경계를 확인했다.
- Next steps:
  - 운영 migration 전 백업과 일반 upgrade 절차를 따른다.

## [2026-08-07] Session Summary (V25 취업 준비 가이드 본문 재작성)

- What was done: `career_guide_posts` 5편의 title·summary·body를 실제 준비 동작 중심으로 다시 쓰고, 서비스 UI와 같은 해요체로 맞췄다. 본문에는 모든 줄이 `- `로 시작하는 체크리스트 문단을 4편에 넣었다.
- Key decisions: V18과 같은 방식으로 시드 상태(`version = 2`, `updated_at = '2026-08-02T01:00:00Z'`)만 갱신해 관리자가 편집한 글을 덮어쓰지 않는다. slug·category·display_order·published_at은 그대로 두어 화면 순서와 아이콘 매핑을 유지했다.
- Issues encountered: None.
- Validation: 실제 PostgreSQL 컨테이너에 임시 DB를 만들어 V17 → V18 → V25를 순서대로 적용했다. 5행이 갱신되고 `version=3`, 본문 길이 628~788자, 체크리스트 줄바꿈이 보존되는 것을 확인한 뒤 임시 DB를 삭제했다.
- Next steps: None.

## [2026-08-06] Session Summary (V24 전역 AI 일일 budget)

- What was done: active budget policy version 2의 단일 `daily_budget_usd=10`, 사용자 소유 차원이 없는 날짜별 전역 ledger, 사용자 preference budget column 제거를 forward migration으로 추가했다.
- Key decisions: 기존 user ledger는 날짜·zone별 합계로 전역 ledger에 병합하고 reservation FK를 먼저 이전한 뒤 제거해 in-flight run의 settle 가능성과 총 비용을 보존한다.
- Issues encountered: 초기 `GLOBAL` scope 방식은 신규 column CHECK 설치 순서와 기존 in-flight reservation 호환 문제가 있어 기존 원장을 합산·FK 이전한 뒤 사용자 소유 차원을 제거하는 방식으로 정리했다.
- Validation: 초기 V24 migration은 실제 PostgreSQL Testcontainer에서 확인했으며, 최종 원장 병합 방식은 사용자 요청에 따라 별도 재검증하지 않았다.
- Next steps: 사용자 plan·credit 제한은 Provider 원가 ledger를 재사용하지 않고 별도 entitlement/credit ledger로 도입한다.

## [2026-08-06] Session Summary (선택 가능 OpenAI 모델 가격 catalog)

- What was done: 공식 standard token 단가를 price version `2026080601`에 추가하고 local workflow 설정을 해당 버전으로 전환했다.
- Key decisions: 기존 적용 migration을 수정하지 않고 V23 forward migration으로 model별 input·cached input·output 단가를 고정했다.
- Issues encountered: None.
- Validation: Flyway 포함 Backend 전체 `check` 통과, `docker compose config --quiet` 통과.
- Next steps: 공식 가격 변경은 새 immutable price version migration으로만 반영한다.

## [2026-08-05] Session Summary (V21~V22 공고 등록 반기 backfill)

- What was done:
  - 기존 `job_postings`에 `posting_year`·`posting_half`를 backfill하고 이후 insert·`created_at` 변경을 분류하는 trigger와 제약·index를 추가했다.
- Key decisions:
  - 서울 현지 1~6월은 `FIRST_HALF`, 7~12월은 `SECOND_HALF`로 고정했다.
- Issues encountered:
  - DML 뒤 동일 transaction의 ALTER가 pending trigger event로 거부되어 backfill V21과 제약 V22를 분리했다.
- Validation:
  - V20 populated upgrade의 6월 30일/7월 1일 서울 경계, trigger 재분류, CHECK와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (V20 Job Analysis coverage)

- What was done:
  - V20이 `fit_score` NOT NULL을 해제하고 0.00..100.00 nullable `analysis_coverage`를 추가한다.
- Key decisions:
  - 기존 분석은 backfill하지 않아 v1 이력의 의미를 변경하지 않는다.
- Issues encountered:
  - 전부 `UNKNOWN`인 v2 분석을 0점 불일치와 구분하려면 nullable fit score가 필요했다.
- Validation:
  - Flyway 적용을 포함한 Backend 전체 `check` 79 suites/538 tests 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (V19 지원 자격·structured fact provenance)

- What was done:
  - V19에서 owner 1:1 `profile_eligibility_declarations`와 immutable `job_analysis_structured_fact_links`를 추가했다.
- Key decisions:
  - 기존 V1~V18은 수정하지 않고 신규 사용자와 기존 사용자 모두 `UNSPECIFIED` 기본값으로 안전하게 초기화했다.
- Issues encountered:
  - None.
- Validation:
  - 빈 DB 전체 적용과 populated V18→V19 upgrade 테스트가 실제 PostgreSQL Testcontainers에서 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (V18 Career Guide 본문 보강)

- What was done:
  - 5개 초기 취업 준비 가이드를 공고 분석·경험 정리·강점 선택·면접 답변·마감 점검의 장문 실전 콘텐츠로 보강했다.
- Key decisions:
  - 적용된 V17은 수정하지 않고 stable ID, version 1, 미변경 timestamp를 모두 만족하는 row만 forward update한다.
- Issues encountered:
  - None.
- Validation:
  - fresh V18과 V17→V18 사용자 편집 보존 migration test 통과.
- Next steps:
  - P8.6 tentative migration은 V19부터 사용한다.

## [2026-08-02] Session Summary (V17 Career Guide 게시 스키마)

- What was done:
  - `career_guide_posts`와 게시·문자열·version 제약, published 정렬 index, 초기 콘텐츠 5개를 추가했다.
- Key decisions:
  - 사용자 작성물이 아닌 전역 B2C 읽기 콘텐츠로 두고 기존 V1~V16은 수정하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - 빈 DB와 populated V16→V17 upgrade를 포함한 Backend 전체 498 tests 통과.
- Next steps:
  - 관리자 mutation schema 확장은 실제 Backoffice 승인 시 검토한다.

## [2026-08-02] Session Summary (V16 공고 자동 분석 intent 스키마)

- What was done:
  - owner/job/version unique와 결정적 run ID, BALANCED 고정, PENDING·CLAIMED·LAUNCHED·BLOCKED·SUPERSEDED 상태 제약을 갖는 table과 due index를 추가했다.
- Key decisions:
  - 이미 적용된 migration은 수정하지 않고 V16으로만 추가했으며 외부 Provider 호출은 schema transaction에 포함하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 빈 PostgreSQL Testcontainers migration과 재시작·중복·quota 통합 회귀 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (V15 사용자 대외활동 스키마)

- What was done:
  - owner FK·기간 검증·소재 선택·soft delete/version을 갖는 `activities`와 ACTIVITY evidence 제약·deferred consistency trigger를 V15로 추가했다.
- Key decisions:
  - 적용된 V1~V14는 수정하지 않고 직접 활동을 문서 테이블과 분리했다.
- Issues encountered:
  - None.
- Validation:
  - 빈 DB V15와 V14 데이터 포함 upgrade migration 테스트 통과.
- Next steps:
  - 다음 migration은 V16부터 사용한다.

## [2026-08-01] Session Summary (Embedding provider 정책 V14 전환)

- What was done:
  - version 1 `OpenAI`를 비활성화하고 version 2 `openai`를 활성화하는 forward migration을 추가했다.
- Key decisions:
  - immutable content는 수정하지 않고 허용된 enabled 상태만 전환한다.
- Issues encountered:
  - None.
- Validation:
  - fresh latest와 populated V13→V14, V1~V13 SHA-256 보존 및 local DB 적용을 확인했다.
- Next steps:
  - P8.6 migration은 tentative V15부터 사용한다.

## [2026-08-01] Session Summary (외부 Provider 가격 catalog V13)

- What was done:
  - price version `2026073101`, gpt-5-mini Chat 3종, text-embedding-3-small, Tavily BASIC·ADVANCED 가격과 usage call identity를 추가했다.
- Key decisions:
  - 공식 공개 가격을 2026-07-31 기준으로 고정하고 변경은 새 forward migration으로만 수행한다.
- Issues encountered:
  - None.
- Validation:
  - fresh V1→V13, populated V12→V13, unique/FK와 V1~V12 SHA-256 불변 검증이 통과했다.
- Next steps:
  - 가격 변경 시 기존 row를 수정하지 않고 새 version을 추가한다.

## [2026-07-31] Session Summary (P8 조사·질문·답변·피드백 V12)

- What was done:
  - 11개 P8 table과 owner 복합 FK, source/topic·question provenance, answer current partial unique, immutable feedback와 typed resource 제약을 추가했다.
- Key decisions:
  - V1~~V11은 수정하지 않고 forward-only V12로 추가했으며 soft-deleted Agent Run이 domain row를 cascade 삭제하지 않게 했다.
- Issues encountered:
  - 순환 참조는 생성 순서와 deferred FK/trigger를 조합해 fresh·upgrade 모두 안전하게 적용했다.
- Validation:
  - 빈 DB와 populated V11→V12, cross-user 실패, V1~~V11 SHA-256 불변 테스트가 통과했다. V12 SHA-256은 `c7bc2332e5bcdfb112c91debe94f8cb98cebd6108dee5f96744c3ea17537c23f`다.
- Next steps:
  - P9 schema는 새 forward migration으로만 추가한다.

## [2026-07-31] Session Summary (최종 학력 V11 backfill)

- What was done:
  - `education_level` column·CHECK를 추가하고 legacy degree·학교명으로 단계를 분류했다.
  - active 학력을 단계·상태·날짜·등록 순으로 재정렬해 사용자별 최종 학력을 다시 지정했다.
- Key decisions:
  - 적용된 V1~V10은 수정하지 않고 forward-only V11로 기존 데이터와 partial unique index를 보정했다.
- Issues encountered:
  - None.
- Validation:
  - 빈 DB, populated V10 upgrade와 개발 DB Flyway 11·사용자별 primary 1개 invariant가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (학력 evidence 정리·Agent Run soft delete V9~V10)

- What was done:
  - 학력 구조화 source의 deferred evidence trigger·unique 의무를 제거하고 기존 EDUCATION row를 비식별 `SOURCE_DELETED` tombstone으로 전환했다.
  - 문서 추출 교육 category도 같은 방식으로 정리하고 active 교육 category CHECK를 추가했다.
  - `agent_runs.deleted_at`, terminal/completed timestamp CHECK와 owner-visible partial index를 추가했다.
- Key decisions:
  - 적용된 V1~V8은 수정하지 않고, V9 적용 뒤 발견한 문서 category 경계는 V10 forward migration으로 보정했다.
  - provenance FK와 비용 audit을 보존하기 위해 학력 근거와 Agent Run 모두 물리 삭제하지 않는다.
- Issues encountered:
  - V9 첫 로컬 시도는 pending deferred trigger event 때문에 rollback됐고 trigger disable 순서를 적용 전 수정해 재실행했다.
  - non-web bootRun은 V10 적용 성공 뒤 `HttpSecurity` bean 부재로 종료됐다.
- Validation:
  - 빈 DB Testcontainers migration을 포함한 Backend 385 tests 통과.
  - 개발 PostgreSQL Flyway V9·V10 success, active 학력 evidence 0·tombstone 1·차단 constraint 1 확인.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Cover Letter V8 migration)

- What was done:
  - `cover_letters`, questions, immutable answer versions·evidence links·verifications·acknowledgements와 Cover Letter/Answer Version Agent Run resource를 V8로 추가했다.
- Key decisions:
  - 모든 사용자 콘텐츠와 교차 참조에 owner 복합 FK를 적용하고 active cover letter·question order·current answer는 partial unique로 제한한다.
  - answer version·verification·acknowledgement는 immutable history로 보존하고 soft-deleted question의 provenance를 삭제하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - 빈 DB V1→V8, populated V7→V8, cross-user·enum·partial unique·immutability negative와 V1~V7 SHA-256 불변이 통과했다.
  - `ddl-auto=validate`, Backend 전체 377 tests와 actual P7 DB assertions가 통과했다.
- Next steps:
  - P8 schema는 V8을 수정하지 않고 새 forward migration으로 추가한다.

## [2026-07-29] Session Summary (P6 Job Analysis V7 migration)

- What was done:
  - `job_analyses`, `job_analysis_score_criteria`, `job_analysis_evidence_links`와 `JOB_ANALYSIS` secondary Run link를 V7로 추가했다.
- Key decisions:
  - 모든 owner FK는 `(user_id,id)` 복합 경계로 강제하고 analysis sealing·child immutability·monotonic version·최소 criterion·VERIFIED evidence를 trigger/CHECK로 보장한다.
- Issues encountered:
  - immutable trigger와 테스트 cleanup 충돌은 운영 DDL을 약화하지 않고 test fixture가 분석 table을 먼저 truncate하도록 해결했다.
- Validation:
  - 빈 DB V1→V7, populated V6→V7, cross-user·score·immutability negative와 V1~V6 SHA-256 불변 3개가 통과했다.
- Next steps:
  - P7 이후 schema는 V7을 수정하지 않고 새 forward migration으로 추가한다.

## [2026-07-27] Session Summary (P5 Job V6 migration)

- What was done:
  - `companies`, `job_postings`, `job_status_history`와 Job typed resource owner FK·index·CHECK·partial unique를 V6로 추가했다.
- Key decisions:
  - V1~V5는 byte 단위로 보존하고 일반 삭제는 `deleted_at` 기반 soft delete로 유지한다.
- Issues encountered:
  - 없음.
- Validation:
  - 빈 DB V1→V6와 V5-only upgrade, owner FK·상태 CHECK·canonical active unique·history FK와 P6 table 부재 6개가 통과했다.
  - V1~V5 Git blob과 SHA-256은 기준선과 동일하다.
- Next steps:
  - P6 schema는 V6를 수정하지 않고 새 forward migration으로 추가한다.

## [2026-07-19] Session Summary (P4 Document·evidence·storage V5 migration)

- What was done:
  - `documents`, `document_texts`, `document_chunks`, `object_deletion_outbox`, `agent_run_resource_links`와 owner FK를 단일 V5로 추가했다.
  - profile evidence·자격증·어학·수상 document FK와 active embedding/model policy metadata를 forward migration했다.

- Key decisions:
  - typed link를 authoritative 원천으로 선택하고 V4 legacy resource pair parity를 deferred trigger로 강제했다.
  - embedding은 `vector(1536)`·cosine·generation 1이며 초기 HNSW와 business checksum unique는 만들지 않았다.

- Issues encountered:
  - V4의 prospective nullable document reference는 owner FK를 만족할 실제 row가 없으므로 structured hint는 null, legacy DOCUMENT_CHUNK는 tombstone으로 안전 이관했다.

- Validation:
  - 빈 DB V1→V5와 V1/V2/V3/V4-only upgrade, CHECK·owner FK·typed link·outbox active unique·P5 table 부재가 통과했다.
  - V1–V4 SHA-256은 각각 `9e9b2cfe…191c`, `c43f2d9a…21dcf`, `6ac81b6a…4347`, `706db49c…e01f`로 불변이다.
  - 최종 read-only Validator가 V5 범위와 기존 migration 불변을 `PASS`로 판정했다.

- Next steps:
  - P5 이후 schema는 V5를 수정하지 않고 다음 forward migration으로 추가한다.

## [2026-07-19] Session Summary (P3 Agent runtime·AI budget V4 migration)

- What was done:
  - `agent_runs`, `agent_steps`, immutable policy·price, preference, ledger·reservation·usage 11개 table과 제약·trigger를 추가했다.
  - 기존 사용자 preference를 ECONOMY·1.00 USD·high quality false로 backfill했다.

- Key decisions:
  - V1·V2·V3는 수정하지 않고 V4 단일 forward migration만 추가했다.
  - 실제 provider 가격은 seed하지 않고 Fake 가격은 test fixture에서만 만든다.
  - document/job typed resource link와 FK는 해당 phase로 이관한다.

- Issues encountered:
  - 기존 개발 DB Flyway history 불일치를 수정하지 않고 격리 PostgreSQL upgrade만 수행했다.

- Validation:
  - 빈 DB와 V1/V2/V3-only upgrade, V4의 71개 CHECK 설치, owner FK·unique·immutability·음수 거부·P4 table 부재가 통과했다.
  - 최종 read-only Validator가 V1·V2·V3 불변과 단일 V4 범위를 `PASS`로 판정했다.

- Next steps:
  - P4에서 documents와 typed Agent Run link를 새 migration으로 추가한다.

## [2026-07-19] Session Summary (P2 구조화 프로필·direct evidence V3 migration)

- What was done:
  - `user_profiles` JSON·owner 제약을 보강하고 educations·certifications·language_scores·awards·careers·profile_evidence를 추가했다.
  - 배열 canonical helper, 대표 학력 partial unique, 날짜·GPA·metadata와 source/evidence owner·1:1 trigger를 추가했다.

- Key decisions:
  - V1·V2는 byte 단위로 보존하고 V3 forward migration만 추가했다.
  - nullable document column은 유지하되 documents table과 복합 FK는 P4로 이관했다.

- Issues encountered:
  - 로컬 기존 DB는 Flyway 이력과 Session table이 불일치해 실제 E2E는 별도 빈 DB에서 수행했다.

- Validation:
  - 빈 DB·V1-only·V2-only upgrade와 모든 P2 CHECK·unique·cross-user·rollback 경계를 PostgreSQL 18에서 통과했다.

- Next steps:
  - P4 document migration에서 owner 복합 FK를 새 version으로 추가한다.

## [2026-07-19] Session Summary (P1 identity·Session·idempotency V2 migration)

- What was done:
  - 사용자 UUID·정규화 email unique·역할 USER·상태 ACTIVE와 기본 프로필을 위한 schema를 추가했다.
  - Spring Session JDBC table·index와 scope/state/HMAC request hash/response replay/24시간 TTL을 저장하는 idempotency table을 추가했다.

- Key decisions:
  - V1은 변경하지 않고 V2에서 필요한 enum constraint·foreign key·unique·조회 index를 선언했다.
  - idempotency에는 요청 원문·비밀번호를 저장하지 않고 hash와 replay용 status·JSON만 저장한다.
  - P1 제외 table과 미래 migration placeholder를 만들지 않았다.

- Issues encountered:
  - PostgreSQL 시간 값의 JDBC 타입 추론은 repository에서 UTC `OffsetDateTime`으로 명시해 해결했다.
  - extension 권한이 필요한 V1을 포함하므로 pgvector PostgreSQL Testcontainers image로 실제 적용했다.

- Validation:
  - 빈 DB V1부터 전체 적용, V1만 적용된 DB의 V2 upgrade, schema constraint·index·unique와 JPA validate가 모두 통과했다.
  - V1은 Git blob `0aa0fc22558644b6dec3f8f24e90d6523c8d12a6`, SHA-256 `9e9b2cfec47519f49ee73cb533c459e22f8ca54fe5ba1cbec59f3d5883fe191c`로 작업 전과 동일하다.

- Next steps:
  - P2 실제 idempotent endpoint에서는 validation·인증·소유권 통과 뒤에만 record를 생성하도록 현재 service를 사용한다.
  - 이후 schema는 기존 migration을 수정하지 않고 새 Flyway version으로 확장한다.

## [2026-07-17] Session Summary (pgvector 확장 V1 migration 구성)

- What was done:
  - 당시 구현 상태:
    - `V1__enable_extensions.sql` 하나만 존재하며 pgvector `vector` extension을 idempotent하게 활성화한다.
    - 사용자, 문서, 공고, 자기소개서, 면접과 Agent Run 업무 table은 아직 구현되지 않았다.
  - 완료된 작업:
    - pgvector 기능을 후속 schema에서 사용할 수 있도록 첫 Flyway migration을 추가했다.
    - migration 디렉터리의 목적, 불변 규칙과 현재 상태를 추적하는 문서를 생성했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 migration 작업은 없다. 문서 체계 초기화는 완료됐고 기존 V1 SQL은 변경하지 않았다.

- Key decisions:
  - 기존 V1은 적용 이력이 생길 수 있는 migration으로 간주해 수정하지 않는다.
  - schema 변경은 Hibernate 자동 생성이 아니라 새로운 Flyway 버전 파일로만 수행한다.
  - 로컬 DB는 pgvector 포함 PostgreSQL image를 사용한다.

- Issues encountered:
  - V1은 대상 DB에 extension 생성 권한과 pgvector 설치가 필요하다.
  - 업무 table이 전혀 없어 현재 migration만으로는 제품 데이터를 저장할 수 없다.
  - 이 위치의 추적 Markdown도 기본 Gradle resource 처리에서 classpath에 복사된다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 성공했다.
  - `V1__enable_extensions.sql`이 `CREATE EXTENSION IF NOT EXISTS vector;`만 포함함을 직접 확인했다.
  - 이번 문서 작업에서는 빈 DB와 기존 DB upgrade migration 실행을 새로 수행하지 않았다.

- Next steps:
  - [`../../../../../../docs/spec/db.md`](../../../../../../docs/spec/db.md)의 우선순위에 따라 V2 이상의 업무 schema migration을 설계한다.
  - 새 migration마다 빈 DB 적용, 기존 DB upgrade, JPA validate와 DB constraint를 검증한다.
  - pgvector column에는 embedding model/dimension 추적과 사용자 격리 index 전략을 함께 설계한다.
