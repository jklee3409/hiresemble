# Progress

## Overview

canonical workflow definition과 Document·Job·Cover Letter·Interview executable contribution 분리가 구현됐다.

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
