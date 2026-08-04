# Progress

## Overview

P3 versioned PromptRegistry에 P4 Document부터 P8 Interview까지 structured prompt metadata가 구현됐고 canonical 목록이 runtime과 schema completeness 검사의 단일 열거 경계다.

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
