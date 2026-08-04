# Progress

## Overview

canonical workflow definition과 Document·Job·Cover Letter·Interview executable contribution 분리가 구현됐다.

## [2026-08-04] Session Summary (Job Analysis source block·criterion RAG v2)

- What was done:
  - 공고 section/source block을 서버가 확정하고 display bullet과 atomic criterion을 분리했으며 criterion별 batch embedding·hybrid retrieval과 evidence scope 검증을 구현했다.
- Key decisions:
  - 역할 소개와 미분류 block은 점수에서 제외하고 model은 block 선택만 담당한다.
- Issues encountered:
  - 기존 단일 2,000자 query가 뒤 criterion을 잘라내고 검색 evidence를 모든 criterion이 공유했다.
- Validation:
  - Plateer형 4개 업무·3개 자격 fixture와 8단계 workflow·typed evidence 회귀 통과.
- Next steps:
  - 실제 Provider source block 선택 분포를 확인한다.

## [2026-08-04] Session Summary (ASSESS_ELIGIBILITY truncation 방지)

- What was done:
  - eligibility `ChatRequest`가 전용 8,000 output token 상한과 low reasoning/verbosity를 명시하도록 수정했다.
- Key decisions:
  - 추출·정규화·matching 비즈니스 로직과 retry 정책은 건드리지 않고, 회귀를 만든 요청 용량 정책만 수정했다.
- Issues encountered:
  - 최근 실제 Run은 18개 requirement 입력에서 출력이 정확히 2,048 token에 도달해 Provider LENGTH로 종료됐으며 gateway가 이를 안전한 non-retryable truncation으로 정규화했다.
- Validation:
  - 18개 requirement를 사용하는 Fake 8단계 workflow가 완료되고 ASSESS 요청의 8,000/low/low 설정을 확인했다.
- Next steps:
  - 실제 Provider에서 같은 공고를 재실행해 truncation이 재발하지 않는지 확인한다.

## [2026-08-03] Session Summary (Job requirement source v4·단일 정규화 정책)

- What was done:
  - Provider output을 source section/text/location/ordinal로 축소하고 `JobRequirementNormalizationPolicy`가 분할·section·required·support type·category·date·dedupe·provenance를 한 번에 결정하게 했다.
  - Workflow와 Job application의 `strictSupportType()` 이중 keyword 판정을 제거했다.
- Key decisions:
  - 모호한 일반 문장은 보수적 preferred/GENERAL로 남기고 positive typed compatibility·eligibility 독립성은 유지한다.
- Issues encountered:
  - 초기 nullable source test가 source section fallback을 반영하지 못해 assertion을 provenance 계약으로 교정했다.
- Validation:
  - 복합 fixture 10 criterion, 줄바꿈 분할·접속사 보존, 8단계 완료, typed support·잘못된 reference 차단과 전체 check 통과.
- Next steps:
  - 실제 Provider output 분포는 live gate에서 별도 확인한다.

## [2026-08-03] Session Summary (Job Analysis allowlist 참조 교정 경계)

- What was done:
  - eligibility와 match Provider 출력의 evidence ID·structured fact reference를 실제 snapshot allowlist와 대조하고, 불일치는 persistence 전에 repairable structured-output 오류로 변환했다.
  - match criterion/strength와 eligibility 모두 중복·null·공백·비허용 참조를 같은 규칙으로 차단했다.
- Key decisions:
  - 기존 domain validation을 제거하지 않고 그 앞에 correction-once 경계를 추가했다. 비허용 참조를 조용히 제거하거나 `UNKNOWN`으로 임의 변경하지 않는다.
- Issues encountered:
  - 최종 diff 검토에서 eligibility 교정 문구의 구 필드명 `evidenceDescriptors` 잔존을 발견해 실제 `verifiedEvidence`로 고치고 회귀 단언을 추가했다.
- Validation:
  - `JobAnalysisWorkflowTest`, workflow contract, strict structured output contract가 통과했고 실제 Provider E2E의 eligibility/match 단계도 모두 attempt 1로 성공했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Job Analysis 비교 단계 보수 복구)

- What was done:
  - allowlist 안의 evidence/fact가 requirement support type과 호환되지 않으면 해당 criterion을 `UNKNOWN`으로 강등하고 연관 strength를 제거했다.
  - 기존 match validation 실패 retry에서는 predecessor safe error를 확인해 provider 없이 전체 criterion을 보수 판정하고 후속 score·validate·persist를 계속한다.
- Key decisions:
  - unknown reference는 강등으로 숨기지 않고 기존 nonretryable evidence 검증으로 차단한다.
- Issues encountered:
  - 실제 correction 출력도 근거 호환성 규칙을 지키지 못해 retry fallback이 필요했다.
- Validation:
  - 호환·비호환·retry fallback 단위 회귀와 실제 공개 API의 8단계 완료·분석 저장을 확인했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Job Analysis 한국어 record 검증)

- What was done:
  - requirement·eligibility explanation·criterion·strength·gap·summary가 한국어 사용자 문장인지 Java record phase에서 검증했다.
  - sourceLocation의 JSONPath·내부 field path를 repairable 실패로 거부했다.
- Key decisions:
  - reuse checkpoint의 서버 생성 placeholder는 검증 대상에서 제외하고 새 Provider 출력만 correction-once 대상으로 삼는다.
- Issues encountered:
  - None.
- Validation:
  - 정상 한국어 분석, 영어 requirement와 내부 경로 거부, 기존 의미 제약 회귀 테스트 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (retrieval embedding route 분리)

- What was done:
  - Job Analysis와 Cover Letter retrieval executor가 active embedding policy route로 embedding을 호출하고 provider·product·dimension·generation을 포함한 hash로 checkpoint 재사용을 격리하도록 변경했다.
- Key decisions:
  - Chat workflow routing은 유지하고 embedding capability만 정책 snapshot 경계로 분리했다.
- Issues encountered:
  - 해당 executor의 refs 선언 타입을 정확히 좁히기 전 compile 오류가 발생했으며 최종 `ObjectNode` 선언으로 교정했다.
- Validation:
  - `compileJava`와 두 workflow focused test가 통과했다.
- Next steps:
  - 기존 terminal run은 재해석하지 않고 retry successor만 새 route를 사용한다.

## [2026-08-02] Session Summary (Job image association checkpoint identity v4)

- What was done:
  - `EXTRACT_JOB_IMAGE_TEXT` input hash에 provider-visible reference binding v4 policy identity를 반영했다.
- Key decisions:
  - 9단계 workflow와 output schema v3는 유지하고 구 요청 결합 계약의 image step checkpoint만 재사용하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Job extraction contract·orchestrator 집중 테스트가 통과했다.
- Next steps:
  - 기존 terminal 실패 retry는 최신 canonical v3 successor에서 새 identity를 사용한다.

## [2026-08-02] Session Summary (Job Analysis Provider DTO→내부 DTO mapping)

- What was done:
  - requirements·eligibility·match Provider record를 내부 output에서 분리하고 검증 뒤 trim·defensive copy·서버 재사용 상태 주입 mapping을 추가했다.
- Key decisions:
  - 재사용은 같은 공개 8단계에서 Provider 0회로 서버가 `true/analysisId`를 주입하며, 신규 분석은 Provider 결과에 `false/null`을 주입한다. `analysisSummary`는 신규 분석 필수 nonblank다.
- Issues encountered:
  - 기존 executor의 context-free output hook만으로는 서버 상태를 안전하게 주입할 수 없어 기존 기본 동작을 보존하는 context-aware hook을 최소 추가했다.
- Validation:
  - 신규·재사용 전체 흐름, nullable mapping, 기존 deterministic score·persist, legacy 내부 checkpoint 역직렬화와 세부 safe reason 테스트가 통과했다.
- Next steps:
  - 실제 Provider 1회 검증에서 세 Provider 단계가 v2 schema로 완료되는지 확인한다.

## [2026-08-01] Session Summary (JOB_POSTING_EXTRACTION v3 trusted aggregate)

- What was done:
  - `ImageTextItem(imageRef,text,truncated)`, trusted 순서 복원, item 20자/aggregate 120자와 v3 checkpoint identity를 구현했다.
- Key decisions:
  - 누락 reference는 유지하고 cross-image 중복 line 제거 뒤 DOM과 합산한다. v1·v2 executable은 두지 않는다.
- Issues encountered:
  - mixed DOM 한 line fixture가 합산되지 않아 substantive fragment의 최소 line을 1로 계약에 맞췄다.
- Validation:
  - I1/I3 누락·재정렬, invalid refs, 19/20와 119/120/121 경계, 80+80·30+100·mixed·noise 테스트 및 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (JOB_POSTING_EXTRACTION v2 9단계)

- What was done:
  - fetch→inspect→image fetch→image text→compose→fields→override→validate→apply 순서를 구현했다.
  - DOM metrics/candidate scoring, source tag 병합, semantic null/U+FFFD/본문 품질과 manual fallback을 고정했다.
- Key decisions:
  - text 충분 분기는 image call 없이 no-op이며 성공 image text는 content hash와 bounded text checkpoint로 재사용한다.
- Issues encountered:
  - 짧은 과거 synthetic fixture가 새 품질 기준에 걸려 fixture 전용 threshold를 명시했다.
- Validation:
  - text/image/manual/invalid/retry/restart 통합 테스트와 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (명시적 terminal partial policy)

- What was done:
  - 모든 executable contribution에 terminal partial policy를 필수화하고 Cover Letter 실패 code/retry 정책을 workflow로 이동했다.
- Key decisions:
  - Document rejection과 Interview LIMITED/NONE은 failed scope를 만들지 않는 정상 결과다.
- Issues encountered:
  - policy 누락이 공용 기본값으로 숨지 않도록 registry fail-fast 검증이 필요했다.
- Validation:
  - registry completeness와 Document·Cover Letter 계약 테스트, 전체 check 통과.
- Next steps:
  - 새 executable workflow도 명시적 policy를 제공한다.

## [2026-08-01] Session Summary (call cap 의미와 evidence schema v2)

- What was done:
  - 문서 evidence step output schema v2를 canonical definition에 연결하고 `maxModelCalls`의 attempt 내부 의미를 명시했다.
- Key decisions:
  - workflow version·공개 step key는 유지하고 evidence step은 non-reusable contract version으로 구분한다.
- Issues encountered:
  - 기존 다른 workflow의 malformed JSON retry 기대를 새 deterministic 정책과 맞췄다.
- Validation:
  - canonical registry, Job·Document orchestration 회귀와 전체 check 통과.
- Next steps:
  - workflow별 model-repairable reason만 구체 typed policy로 확장한다.

## [2026-08-01] Session Summary (schema rejection failure kind 분리)

- What was done:
  - Provider 요청 schema 거절을 모델 응답 structured validation과 구분하는 `STRUCTURED_SCHEMA` failure kind를 추가했다.
  - `WRITE_ANSWER`의 recursive TipTap을 공개 DTO에서 Provider 전용 record와 bounded mapper로 분리했다.
- Key decisions:
  - schema 거절은 동일 요청 자동 retry 불가이며 기존 public HTTP·TipTap 계약은 변경하지 않는다.
- Issues encountered:
  - 강화한 keyword 감사가 공개 DTO annotation에서 생성된 `default`를 추가 발견했다.
- Validation:
  - Gateway·workflow focused 회귀와 전체 Backend check 통과.
- Next steps:
  - P8.8 구현 전까지 기존 Agent Run safe error projection을 유지한다.

## [2026-08-01] Session Summary (price-versioned Provider 요청 연결)

- What was done:
  - P5~P8 Chat/Search 호출에 run 고정 price version, output type과 token bound를 전달했다.
- Key decisions:
  - workflow step·retry 상한은 유지하고 provider 내부 retry만 0회로 고정한다.
- Issues encountered:
  - None.
- Validation:
  - workflow unit/integration와 P5~P8 actual 회귀가 통과했다.
- Next steps:
  - P9 workflow는 이번 범위에서 시작하지 않는다.

## [2026-07-31] Session Summary (P8 preparation 10단계·feedback 5단계)

- What was done:
  - canonical exact step order, source coverage/provenance validation, feedback bound validation과 atomic persist/compensation을 구현했다.
- Key decisions:
  - coverage는 Java 결정론 정책으로 계산하고 성공 checkpoint에는 ID·hash·count·validation summary만 기록한다.
- Issues encountered:
  - persist 전 source ID 선조회는 같은 transaction 입력 allowlist로 바꿔 새 source provenance를 안전하게 검증했다.
  - 1차 self-audit에서 request 금지 enum인 `FOLLOW_UP`을 생성 output에서도 거부하던 검증을 발견해 output 전용 질문은 허용하도록 보정했다.
- Validation:
  - 제한 보정 후 exact order/version, privacy, coverage, hallucinated/cross-user ID, output-only follow-up, output boundary, failure/cancel/restart 테스트가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (Cover Letter suggestion structured output 보정)

- What was done:
  - generation `FACT_CHECK_ANSWER[*]`와 verification `CHECK_FACTS`·`CHECK_REQUIREMENTS_AND_LENGTH`·`AGGREGATE_VERIFICATION` suggestion을 최대 20개·항목 1~1000자로 통일했다.
  - facts와 requirements의 서로 다른 유효 제안이 합쳐질 때 aggregate가 앞선 우선순위를 보존하며 20개로 제한되도록 했다.
- Key decisions:
  - structured output 경계 위반은 빈 성공이나 잘린 model 성공으로 처리하지 않고 validation failure로 분류하며 로컬 aggregate만 결정론적으로 공개 최대치를 적용한다.
- Issues encountered:
  - 각 model step이 개별적으로 20개를 반환하면 단순 합산이 aggregate 계약을 넘을 수 있는 통합 경계를 추가로 발견했다.
- Validation:
  - 20/21개·1000/1001자와 두 단계 합산 경계 회귀, Backend 전체 54 suites/380 tests가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (Cover Letter generation 8단계·verification 6단계)

- What was done:
  - question ID deterministic order의 bounded fan-out generation과 immutable answer snapshot verification contribution을 추가했다.
  - 문항별 성공/실패 resultRefs, retry seed reuse, source-deleted exclusion과 PENDING compensation을 구현했다.
- Key decisions:
  - 한 문항 실패는 성공 문항 apply를 rollback하지 않고 같은 snapshot hash의 성공 scope는 재실행하지 않는다.
- Issues encountered:
  - commit 직후 interruption 후 restart에서 중복 answer version이 없도록 apply idempotency를 run/scope/hash로 고정했다.
  - verification provenance의 claim text가 checkpoint 최소 출력에 남을 수 있어 해당 로컬 단계를 non-reusable로 바꾸고 hash·ID·count만 저장하도록 보정했다.
- Validation:
  - P7 workflow 3 suites와 orchestrator 통합, actual partial failure→failed-scope retry 시나리오가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (JOB_ANALYSIS 8단계 executable contribution)

- What was done:
  - `job-analysis-v1` 8단계 executor, 단계별 structured output와 verified evidence allowlist·deterministic apply를 추가했다.
- Key decisions:
  - reuse branch는 동일 step contract를 local로 실행하고 provider routing만 생략하며 persistence는 Job command port만 사용한다.
- Issues encountered:
  - embedding dimension을 active policy에서 읽도록 보정하고 기존 workflow 기본 provider 요구 의미를 회귀 테스트로 고정했다.
- Validation:
  - workflow contract 3개와 Fake 실행 8개, 전체 Backend check가 통과했다.
- Next steps:
  - production provider가 활성화될 때 embedding product routing을 별도 계약으로 보강한다.

## [2026-07-27] Session Summary (JOB_POSTING_EXTRACTION executable contribution 추가)

- What was done:
  - 5단계 고정 순서와 retry·cancel·reuse·WAITING_USER failure handler를 연결했다.
- Key decisions:
  - 모델 이후 실제 필드는 ephemeral record로만 전달하고 checkpoint에는 hash·길이 등 안전 참조만 남긴다.
- Issues encountered:
  - 없음.
- Validation:
  - 순서·override·invalid output·timeout·cancel·restart·reuse·privacy 계약 테스트가 통과했다.
- Next steps:
  - P6 분석 workflow는 새 contribution으로 추가한다.

## [2026-07-19] Session Summary (DOCUMENT_INGESTION executable contribution 추가)

- What was done:
  - canonical 8단계 metadata에 실제 Document contribution과 failure handler를 연결했다.
- Key decisions:
  - 고정 step 순서와 progress weight를 유지하고 deterministic parse step만 revision 범위에서 재사용한다.
- Issues encountered:
  - None.
- Validation:
  - registry·orchestrator·P4 integration과 P3 Fake workflow 회귀가 통과했다.
- Next steps:
  - 다른 canonical workflow는 해당 phase 전까지 executable로 등록하지 않는다.

## [2026-07-19] Session Summary (Workflow Registry 계약 구현)

- What was done:
  - workflow version, 고정 step, fan-out·schema·tool·call·retry·weight metadata를 등록했다.
  - test-only 3-step contribution만 실행 가능하게 만들었다.

- Key decisions:
  - canonical definition은 P0 계약을 설명하지만 P4 이전 실행 가능성을 의미하지 않는다.

- Issues encountered:
  - None.

- Validation:
  - canonical coverage, duplicate, weight와 executable sequence unit test 3개가 통과했다.

- Next steps:
  - 각 phase가 실제 domain port와 함께 contribution을 등록한다.
