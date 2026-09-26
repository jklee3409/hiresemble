# Progress

## Overview

P3 versioned PromptRegistry에 P4 Document부터 P8 Interview, GitHub와 Career Artifact까지 structured prompt metadata가 구현됐고 canonical 목록이 runtime과 schema completeness 검사의 단일 열거 경계다.

## [2026-09-26] Session Summary (MATCH_EVIDENCE prompt v7)

- What was done:
  - `job-analysis-match-evidence-v7`: 각 요건의 `criterionIndex`를 그대로 복사하고 목록 위치로 번호를 매기지 않도록 하며, `batchNumber`/`batchCount` 분할 입력에서는 받은 요건만 매핑하고 요약을 한두 문장으로 쓰게 했다.
- Key decisions:
  - output schema(`job-analysis-match-output-v3`)는 유지했다.
- Issues encountered:
  - None
- Validation:
  - `JobAnalysisWorkflowContractTest` 통과.
- Next steps:
  - None

## [2026-09-25] Session Summary (공고 필드 추출 prompt v4 현지 마감 시각)

- What was done:
  - `EXTRACT_JOB_FIELDS`만 `job-posting-extraction-fields-prompt-v4`로 분리하고 output type을 `ExtractedJobFieldsOutput`/`job-fields-output-v4`로 바꿨다.
- Key decisions:
  - prompt는 마감일을 공고 현지 표기 그대로 반환하고 UTC 변환·offset 추측을 금지한다. 연도 생략은 요일·접수 기간 등 문맥으로만 추론한다. 다른 step은 prompt v3와 image prompt v4를 유지한다.
- Issues encountered:
  - None
- Validation:
  - prompt identity·schema nullable 계약 test 포함 집중 test(JobPostingExtraction contract 6·orchestrator 통합 15) 통과. rebase 후 `.\gradlew.bat check` 2회는 107 suites/733 tests 중 무관한 `AccountDeletionWorkerIntegrationTest.githubUninstallMustReachSucceededAndExpiredTaskLeaseIsRecovered` 1건(`@Scheduled` scan과 수동 `processDue` 경쟁 추정)으로 실패했고, 해당 suite 단독 실행은 6/6 통과해 전체 check green은 미확인이다.
- Next steps:
  - None

## [2026-09-24] Session Summary (생성 v5 출력 token 상한 확대)

- What was done:
  - reasoning token이 completion 상한에 포함되므로 v5 초안 16,000, 검토 20,000, 계획 12,000으로 출력 상한을 올렸다.
- Key decisions:
  - None
- Issues encountered:
  - None
- Validation:
  - Backend 전체 `check`(세션 Gradle mirror init script, 로컬 dockerd): 106 suites/718 tests 중 717 통과. 실패 1건 `S3ObjectStorageAdapterTest`는 Docker Hub의 `minio/minio` 이미지 pull 거부로 인한 초기화 오류이며 이번 변경과 무관해 미검증으로 남긴다.
- Next steps:
  - None

## [2026-09-24] Session Summary (자기소개서 생성 v5 prompt 정의)

- What was done:
  - `CoverLetterGenerationV5PromptDefinitions`를 추가해 분석 겸용 계획, 평문 초안, 채용 담당자 관점 검토·수정, verbatim claim 연결 prompt(`cover-letter-v5-*-prompt-v1`)를 등록했다. 초안·검토 입력 토큰 상한은 40,000, 근거 연결은 32,000이다.
  - 기존 `CoverLetterGenerationV3PromptDefinitions`의 v4 식별은 `COVER_LETTER_GENERATION_V4_VERSION`으로 고정해 durable v4 prompt text를 유지했다.
- Key decisions:
  - 회사 사실은 공고와 회사 조사에서만 쓰고, 정량 사실은 `VERIFIED` content에서만 쓰는 경계를 초안·검토·FactCheck에 동일하게 적용했다.
- Issues encountered:
  - None
- Validation:
  - AI package 테스트 242건 통과(v5 단일 문항 계획→초안→검토→근거 연결→저장 전체 흐름, Markdown·분량 하한 거부, 근거 연결 excerpt·evidence 거부, v5 prompt 계약 포함).
  - Backend 전체 `check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/713 tests 중 712 통과. 실패 1건 `S3ObjectStorageAdapterTest`는 Docker Hub의 `minio/minio` 이미지 pull 거부로 인한 초기화 오류이며 이번 변경과 무관해 미검증으로 남긴다.
- Next steps:
  - None

## [2026-09-24] Session Summary (자기소개서 v4 writer 채용 담당자 관점 prompt v7)

- What was done:
  - v4 `WRITE_ANSWER` prompt를 `cover-letter-write-answer-prompt-v7`로 올리고 두괄식 첫 문장, 1~2개 구체 경험과 1인칭 개인 행동, 직무 요건 연결, 상투 표현 금지, 짧은 문단 규칙을 명시했다.
  - "Prefer a concise direct answer" 지시를 v4에서 제거하고 서버가 준 `targetCharacterCount`·`minimumCharacterCount`를 기준 분량으로 삼게 했다. `PLAN_QUESTIONS` v7은 제한의 약 90%를 목표로 계획한다.
  - v4 `FACT_CHECK_ANSWER` prompt v5와 입력 type `FactCheckAnswerInputV4`를 등록해 원문 발췌의 정성 서술은 해당 evidenceId 근거로, 정량 사실은 근거 content로만 판정하게 했다.
- Key decisions:
  - v3 prompt text와 version identity는 byte 단위로 유지했다.
- Issues encountered:
  - None
- Validation:
  - 자기소개서 workflow·prompt·policy 집중 테스트 32건 통과(신규 v4 writer 원문 발췌·분량 목표/하한·claim 없는 사실 표현 경고·단일 문항 미지원 수치 ERROR·v3 기존 거부 유지·v4 prompt 계약·policy 경계 포함).
  - Backend 전체 `./gradlew check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/703 tests 중 702 통과. 유일한 실패 `S3ObjectStorageAdapterTest`는 이 환경에서 Docker Hub가 `minio/minio:RELEASE.2025-09-07T16-13-09Z` pull을 거부한 초기화 오류로, 이번 변경과 무관하며 미검증으로 남긴다.
- Next steps:
  - 실제 provider 출력으로 문체 규칙 준수율과 분량 달성률을 관측한다.

## [2026-08-10] Session Summary (GitHub 중심 경험 한국어 extraction prompt v2)

- What was done:
  - GitHub 후보 제목·본문을 자연스러운 한국어로 작성하고 repository의 주된 목적·핵심 설계/구현·중요 문제 해결·명시된 성과만 최대 3개 반환하도록 extraction prompt v2를 등록했다.
- Key decisions:
  - 기술명·고유명사는 보존하되 작은 설정·의존성·파일 변경·고립된 테스트/문서·경미한 refactor·기술 나열은 제외하고 관련 근거는 하나로 합친다. 중심 경험이 없으면 0개를 허용한다.
- Issues encountered:
  - 기존 v1은 후보를 12개까지 허용해 사용자가 원하는 굵은 경험 경계와 맞지 않았다.
- Validation:
  - prompt registry/contract를 포함한 Backend 전체 `check`가 통과했다.
- Next steps:
  - None.

## [2026-08-09] Session Summary (Portfolio 실제 provider prompt v2)

- What was done:
  - 첫 60초는 내부 편집 목표일 뿐 생성 content의 수치·read-time·slide 제약 문구가 아님을 명시하고 Portfolio prompt identity를 v2로 올렸다.
- Key decisions:
  - 면접관 중심 story 정책은 유지하면서 presentation constraint가 deterministic metric validator와 충돌하지 않게 했다.
- Issues encountered:
  - v1의 60초 지시가 실제 content에 출력되면 승인 근거에 없는 숫자로 판정될 수 있었다.
- Validation:
  - prompt version·핵심 문구 contract와 실제 Portfolio 생성 성공을 확인했다.
- Next steps:
  - None.

## [2026-08-08] Session Summary (Career Artifact prompt contract)

- What was done:
  - Resume·Portfolio PLAN/DRAFT/FACT_CHECK prompt와 typed schema를 versioned definition으로 추가했다.
- Key decisions:
  - Portfolio prompt는 면접관 독자·첫 60초·case-study 순서·한 slide 한 message·근거 없는 수치 금지를 고정한다.
- Issues encountered:
  - None.
- Validation:
  - 핵심 정책·schema/version·빈 tool allowlist contract가 통과했다.
- Next steps:
  - 전체 문자열 snapshot은 사용하지 않는다.

## [2026-08-07] Session Summary (Document ingestion v2 prompt identity)

- What was done:
  - active v2 prompt identity를 추가하고 legacy `p0-contract-v1` prompt를 durable replay용으로 보존했다.
- Key decisions:
  - 후보 embedding은 별도 gateway 단계이며 Chat output schema에는 vector를 노출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - prompt registry와 strict schema 집중 테스트 통과.
- Next steps:
  - None.

## [2026-08-06] Session Summary (자기소개서 memo-aware v4 prompt)

- What was done: generation plan·write prompt에 bounded memo를 전달하는 v4 identity를 추가하고 verification v4를 등록했다.
- Key decisions: memo를 사용자 의도·강조 방향으로 명시하고 사실 주장 근거로 사용하지 못하게 했다.
- Issues encountered: None.
- Validation: prompt registry를 포함한 Backend 전체 `check` 통과.
- Next steps: quality rubric과 golden set으로 prompt 개선 효과를 회귀 측정한다.

## [2026-08-06] Session Summary (Cover Letter Writer 길이 계약 v5)

- What was done:
  - Writer prompt를 v5로 격리하고 최종 답변의 Unicode code point 수가 문항 `maxLength` 이하가 되도록 짧고 직접적으로 작성하라는 계약을 추가했다.
- Key decisions:
  - 다른 v3 단계와 legacy prompt identity는 유지했다.
- Issues encountered:
  - 기존 v4는 길이 값은 전달했지만 최종 문자열의 정확한 길이 준수와 초과 시 축약 우선순위를 충분히 명시하지 않았다.
- Validation:
  - prompt contract test와 Backend 전체 `check` 통과. 실제 성공 Run에서 `cover-letter-write-answer-prompt-v5`와 943/1,000자 결과를 확인했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (Cover Letter plan·writer prompt 계약 보정)

- What was done:
  - `PLAN_QUESTIONS` prompt를 v5로 격리하고 `cover-generation-plan-output-v3` exact schemaVersion, 문항 수·순서, question type-framework-section mapping, weight·text·index·nullable 계약을 명시했다.
  - Writer prompt를 v4로 격리하고 exact output schemaVersion과 questionId 복사 조건을 추가했다.
- Key decisions:
  - 기존 v1/v2 prompt identity와 v3의 다른 step identity는 변경하지 않았다.
- Issues encountered:
  - input/output에 모두 `schemaVersion`이 있어 exact output value를 명시하지 않으면 schema-valid JSON이 Java record 의미 검증에서 반복 탈락했다.
- Validation:
  - prompt metadata·nullable normalization contract test와 Backend 전체 `check` 성공. 실제 `PLAN_QUESTIONS` step이 v5 1차에 성공했다.
- Next steps:
  - None.

## [2026-08-05] Session Summary (Cover Letter v3 prompt identity)

- What was done:
  - generation·verification v3 단계별 prompt에 `ko-KR`, Context availability, bounded/truncated text, exact excerpt claim, issue matrix와 partial sibling 지시를 명시했다.
- Key decisions:
  - v1/v2 PromptKey는 변경하지 않고 v3 key/schema만 추가해 durable checkpoint 호환을 보존한다.
- Issues encountered:
  - None.
- Validation:
  - PromptRegistry completeness와 OpenAI strict schema compatibility 테스트 통과.
- Next steps:
  - 실제 Provider 한국어 품질은 호출 승인 후 확인한다.

## [2026-08-05] Session Summary (Cover Letter 단계별 v2 prompt identity)

- What was done:
  - generation plan·analysis·allocation·write·fact-check와 verification facts·quality에 단계별 v2 prompt identity와 strict output schema를 등록했다.
  - 기존 generation/verification v1 PromptKey와 prompt 전문은 durable Run용으로 보존했다.
- Key decisions:
  - 기업 조사·기업 유형 하드코딩·Writer web search 없이 supplied job Context와 current VERIFIED evidence만 사용한다.
  - 품질 이슈는 기존 `OTHER` WARNING/suggestion을 우선하고 공개 issue enum은 확장하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - canonical/legacy prompt completeness, 중복 PromptKey, OpenAI strict schema compatibility를 포함한 Backend 전체 check가 통과했다.
- Next steps:
  - 실제 Provider 품질 UAT는 별도 opt-in 작업으로 남긴다.

## [2026-08-04] Session Summary (Job requirement source-only prompt v9)

- What was done:
  - requirement prompt/schema를 v9/v6로 올리고 각 항목이 sourceBlockId·sourceText·sourceOrdinal만 exact-copy하도록 변경했다.
- Key decisions:
  - section/location/JSONPath는 Provider 출력 대상이 아니며 sourceText 번역·의역도 금지한다.
- Issues encountered:
  - v8/v5는 server-owned라고 선언한 section/location을 strict Provider schema에 계속 요구하는 모순이 있었다.
- Validation:
  - prompt identity와 field 부재 contract, strict OpenAI schema 검사가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Job requirement source block prompt v8)

- What was done:
  - requirement prompt와 schema를 v8/v5로 올리고 model이 scorable source block ID·text만 복사하도록 제한했다.
- Key decisions:
  - section·source location·canonical category는 server-owned 계약으로 유지한다.
- Issues encountered:
  - 이전 prompt는 model이 source section을 생성해 역할 소개와 자격의 경계가 흔들릴 수 있었다.
- Validation:
  - prompt registry·strict structured output·workflow contract를 새 schema와 대조했다.
- Next steps:
  - 외국어 공고의 exact source와 한국어 표시 계약은 별도 확장 시 검토한다.

## [2026-08-04] Session Summary (Eligibility 전용 output 정책 복구)

- What was done:
  - `ASSESS_ELIGIBILITY_MAX_OUTPUT_TOKENS`를 단계 전용 상수로 분리하고 8,000으로 설정했다.
- Key decisions:
  - 실제 실패는 prompt 의미나 schema 오류가 아니라 2,048 token 상한의 LENGTH 종료였으므로 prompt version은 올리지 않았다.
- Issues encountered:
  - None.
- Validation:
  - workflow contract test가 EXTRACT 4,096과 ASSESS 8,000의 단계별 정책을 고정하고 기존 MATCH 6,144 정책을 유지한다.
- Next steps:
  - 실제 Provider 수직 검증은 인증 가능한 로컬 세션에서 별도로 수행한다.

## [2026-08-03] Session Summary (Job Analysis 단계별 prompt·token identity)

- What was done:
  - 8개 Job Analysis step의 prompt version을 분리하고 source requirements v4를 `4,096` output tokens·low reasoning·low verbosity로 제한했다.
- Key decisions:
  - workflow version은 유지하고 step prompt/schema/canonical input hash만 필요한 checkpoint 범위를 무효화한다.
- Issues encountered:
  - None.
- Validation:
  - 단계별 identity 중복 부재, requirements 8,000 미사용과 strict schema contract가 통과했다.
- Next steps:
  - None.

## [2026-08-03] Session Summary (Job Analysis 실제 입력 경로 prompt v6)

- What was done:
  - eligibility prompt에 `approvedProfile.verifiedEvidence[].id`와 `approvedProfile.structuredProfileFacts[].reference`, match prompt에 `verifiedEvidenceCandidates[].evidenceId`와 `structuredProfileFacts[].reference`의 정확한 복사 경로를 명시했다.
  - 빈 allowlist의 빈 배열 처리, cross-field 이동 금지, evidence가 없을 때 strength 금지, 요구사항 중복 금지를 명시했다.
- Key decisions:
  - output schema version은 유지하고 prompt identity만 v6로 올려 기존 checkpoint와 잘못된 구조 안내를 격리했다.
- Issues encountered:
  - v5 실제 E2E에서 eligibility는 통과했지만 match가 비허용 criterion/evidence 참조로 실패해 match 입력 경로도 같은 수준으로 명시해야 함을 확인했다.
- Validation:
  - prompt registry/contract 테스트와 실제 Provider v6 E2E 8단계가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 분석·문서 소재 한국어 prompt v3)

- What was done:
  - Job Analysis 세 Chat prompt에 requirement·eligibility·match 결과의 자연스러운 한국어 출력과 내부 source path 금지 규칙을 추가했다.
  - Document evidence prompt에 이력서·자기소개서 소재 category·title·content·warning 한국어 출력 규칙을 추가했다.
- Key decisions:
  - output schema는 v2를 유지하고 변경된 언어 계약의 checkpoint 격리를 위해 prompt identity만 각각 v3로 올렸다.
- Issues encountered:
  - None.
- Validation:
  - 두 prompt registry의 identity·instruction contract 집중 테스트 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Job image reference binding prompt v4)

- What was done:
  - 이미지 reference가 Provider-visible message text에 해당 이미지와 명시적으로 결합된다는 계약으로 image step prompt를 갱신했다.
- Key decisions:
  - 다른 Job extraction step은 prompt v3를 유지하고 `EXTRACT_JOB_IMAGE_TEXT`만 `job-posting-extraction-image-text-prompt-v4`로 분리한다.
- Issues encountered:
  - None.
- Validation:
  - prompt registry identity와 Job extraction workflow contract test가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Job Analysis 모델 소유 출력 prompt v2)

- What was done:
  - 세 Chat 단계 prompt를 모델 소유 필드만 반환하는 `job-analysis-prompt-v2`와 output schema v2로 갱신했다.
- Key decisions:
  - 공고 본문 untrusted 경계와 injection 방어를 유지하고 section/category/required, allowlist, missingReason/null, nonblank summary 규칙을 명시했다.
- Issues encountered:
  - None.
- Validation:
  - prompt/schema identity와 Provider output type field allowlist 집중 테스트는 통과했다. 전체 Backend check는 범위 밖 Object Deletion Outbox 2건 실패로 미통과했다.
- Next steps:
  - None.

## [2026-08-01] Session Summary (Job image prompt v3)

- What was done:
  - image prompt에 supplied local `imageRef` 보존·누락 허용·중복 금지·untrusted instruction 무시 계약을 추가했다.
- Key decisions:
  - URL·filename·Job ID·UUID 생성과 field 추론을 금지한다.
- Issues encountered:
  - None.
- Validation:
  - prompt/schema metadata contract와 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (문서 evidence prompt v2)

- What was done:
  - 문서 evidence prompt를 단일 output policy에서 생성하고 local ref·candidate/warning/null/dedupe 규칙을 명시했다.
- Key decisions:
  - output schema는 `document-evidence-provider-output-v2`, max output은 8,192 token이다.
- Issues encountered:
  - Spring AI schema description은 Jackson property description을 사용해야 strict subset의 unsupported `default`가 생기지 않았다.
- Validation:
  - prompt-policy equality, schema description과 registry completeness test 통과.
- Next steps:
  - live 성공 전 prompt 품질 상태를 verified로 올리지 않는다.

## [2026-08-01] Session Summary (canonical strict output definition 열거)

- What was done:
  - 구현된 prompt provider를 canonical 목록으로 모으고 Chat strict output definition 자동 열거를 추가했다.
  - 문서 metadata entry와 필수 nullable warning prompt 의미를 Java schema와 맞췄다.
- Key decisions:
  - 새 Chat step은 canonical workflow와 prompt registry 양쪽 completeness 검사에서 누락될 수 없다.
- Issues encountered:
  - 없음.
- Validation:
  - 전체 14개 strict output parameterized schema 검사가 통과했다.
- Next steps:
  - 새 output 추가 시 version과 nullable 의미를 함께 등록한다.

## [2026-07-31] Session Summary (P8 versioned prompt)

- What was done:
  - public search plan·question generation과 immutable answer feedback prompt definition을 추가했다.
- Key decisions:
  - 검색 결과는 instruction이 아닌 untrusted data이며 개인 사실 ID는 server allowlist로만 허용한다.
- Issues encountered:
  - 1차 self-audit에서 `FOLLOW_UP`을 nested follow-up으로만 제한한 문구를 output 전용 canonical question type도 허용하도록 보정했다.
- Validation:
  - 제한 보정 후 exact prompt version·structured schema·민감정보 부재 계약 테스트가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (문서 학력 근거 추출 금지)

- What was done:
  - Document evidence extraction prompt에 학력·교육 이력 후보를 만들지 않는 instruction을 추가했다.
- Key decisions:
  - prompt만 신뢰하지 않고 application validation과 DB CHECK를 함께 적용한다.
- Issues encountered:
  - None.
- Validation:
  - Document workflow 통합 12 tests와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 생성·검증 structured prompt)

- What was done:
  - question plan/analysis, evidence allocation, answer/fact-check와 verification fact/requirement/aggregate record schema를 등록했다.
- Key decisions:
  - evidence ID는 typed field로 전달하고 모델이 source·createdBy·finalization을 지정하지 못하게 한다.
- Issues encountered:
  - 없음.
- Validation:
  - schema version·invalid output·timeout/retry와 workflow contract 테스트가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 분석 prompt 계약)

- What was done:
  - requirement·eligibility·matching record schema, call cap와 untrusted job content instruction 경계를 등록했다.
- Key decisions:
  - 모델에 final score·owner·persist 권한을 주지 않고 공고 내부 instruction을 실행하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - prompt metadata contract와 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (Job Posting Extraction prompt 계약 추가)

- What was done:
  - P5 추출 prompt version·output schema와 호출/token 제한을 registry에 추가했다.
- Key decisions:
  - Chat 호출은 추출 step에만 attempt당 1회 허용하고 tool 호출은 허용하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - schema version·structured invalid·provider timeout 분류 테스트가 통과했다.
- Next steps:
  - P6 prompt는 이번 registry에 선행 등록하지 않는다.

## [2026-07-19] Session Summary (Document evidence structured prompt 추가)

- What was done:
  - masked chunk와 source reference만 받는 Document evidence prompt·schema definition을 추가했다.
- Key decisions:
  - candidate는 source chunk에 grounded되어야 하고 전체 prompt·response를 저장하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - structured validation과 존재하지 않는 chunk·중복·부분 성공 경계가 통과했다.
- Next steps:
  - 실제 provider별 prompt tuning은 별도 version으로 추가한다.

## [2026-07-19] Session Summary (Prompt Registry 기반 구현)

- What was done:
  - workflow·step key, prompt/schema version, DTO type, tool allowlist와 call/token cap을 정의했다.

- Key decisions:
  - production에는 P4 이후 workflow prompt 파일을 생성하지 않았다.

- Issues encountered:
  - None.

- Validation:
  - Fake 3-step이 test resource prompt를 정확히 조회하는 통합 테스트가 통과했다.

- Next steps:
  - 실제 prompt는 해당 domain schema와 함께 versioned asset으로 추가한다.
