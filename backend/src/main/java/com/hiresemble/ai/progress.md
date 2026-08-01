# Progress

## Overview

P3 fixed workflow runtime과 network-disabled gateway 기반에 P4 Document, P5 Job, P6 Job Analysis, P7 Cover Letter와 P8 Interview preparation·answer feedback workflow가 연결됐다. local Chat은 중앙 검증된 strict schema만 전송하고 응답 phase별 safe reason과 bounded repair retry를 적용하며 Tavily adapter는 명시적 opt-in에서만 활성화된다.

## [2026-08-01] Session Summary (공고 image capability·workflow v2)

- What was done:
  - 별도 image text gateway와 9단계 Job extraction v2 contribution을 runtime registry에 연결했다.
- Key decisions:
  - text-only ChatGateway는 변경하지 않고 image usage도 기존 run budget·usage recorder를 통과한다.
- Issues encountered:
  - legacy v1은 새 checkpoint로 해석하지 않도록 executable 없이 definition만 보존했다.
- Validation:
  - strict schema registry, provider 옵션, orchestrator retry/reuse와 전체 check 통과.
- Next steps:
  - live Provider 호출은 별도 승인 후 수행한다.

## [2026-08-01] Session Summary (workflow terminal partial 정책 분리)

- What was done:
  - executable contribution별 terminal partial policy와 문서 candidate rejection 성공 projection을 연결했다.
- Key decisions:
  - 공용 Orchestrator는 cover letter safe code를 알지 않으며 failed scope 의미는 실제 독립 scope로 제한한다.
- Issues encountered:
  - 과거 잘못 종료된 terminal Run은 자동 보정하지 않는다.
- Validation:
  - Backend 68 suites/466 tests 통과, Provider 호출 0회.
- Next steps:
  - 문서 terminal 상태를 live 1회 재검증한다.

## [2026-08-01] Session Summary (문서 Provider output 최소화와 phase별 retry)

- What was done:
  - server-owned identifier·metadata를 문서 Provider output에서 제거하고 local ref mapping, finish reason, typed validation/retry 경계를 연결했다.
- Key decisions:
  - strict mode와 public/domain 계약은 유지하고 safe correction이 있는 의미 오류만 한 번 고친다.
- Issues encountered:
  - 과거 live invalid field와 finish reason은 복구할 수 없다.
- Validation:
  - Backend 68 suites/459 tests 통과, Provider 호출 0회.
- Next steps:
  - persistent counter 정책의 별도 승인 뒤 P8.5-V Chat structured success와 document vertical을 각각 1회 검증한다.

## [2026-08-01] Session Summary (strict Provider output 경계 강화)

- What was done:
  - canonical prompt 열거, strict schema 생성·호환성 검증·registry, schema rejection 분류, 문서 metadata와 P7 TipTap Provider mapping을 연결했다.
- Key decisions:
  - Provider DTO와 domain/public 계약을 분리하고 strict 실패를 non-strict/Fake로 fallback하지 않는다.
- Issues encountered:
  - 당시 Provider raw error가 없어 장애 원인은 offline 재현 근거의 `HIGH_CONFIDENCE`로 유지한다.
- Validation:
  - Backend 68 suites/452 tests 통과, 외부 Provider 호출 0회.
- Next steps:
  - P8.5-V에서 Chat과 문서 vertical을 각 1회 재검증한다.

## [2026-07-31] Session Summary (P8 조사·질문·답변 피드백 workflow)

- What was done:
  - 정확한 preparation 10단계와 feedback 5단계, owner-scoped context·versioned prompt·structured output·failure compensation을 runtime에 등록했다.
  - privacy-limited search plan, URL dedupe·source 분류·coverage와 typed evidence/source provenance 검증을 구현했다.
- Key decisions:
  - `LIMITED|NONE`은 성공 결과이고 모든 search 호출 장애만 `FAILED`이며 검색 본문은 untrusted data로만 취급한다.
  - final education은 structured projection으로 사용하되 education evidence와 provenance로 위장하지 않는다.
- Issues encountered:
  - source가 같은 atomic apply에서 생성되는 경우 allowlist를 DB 선조회하지 않고 validated workflow input으로 검증하도록 보정했다.
- Validation:
  - workflow contract·boundary·WireMock Tavily tests와 P8 actual의 SUFFICIENT/LIMITED/NONE/FAILED·retry 분기가 통과했다.
- Next steps:
  - 실제 provider 활성화는 별도 운영 승인과 key 주입 뒤 수행한다.

## [2026-07-31] Session Summary (Document 학력 근거 추출 제외)

- What was done:
  - 문서 근거 추출 prompt에서 학력·교육 이력을 제외하고 application·DB 방어와 계약을 맞췄다.
- Key decisions:
  - model instruction은 UX 유도 계층이며 최종 차단은 deterministic validation과 CHECK가 담당한다.
- Issues encountered:
  - None.
- Validation:
  - Document integration 12 tests와 Backend 전체 385 tests 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Cover Letter generation·verification workflow)

- What was done:
  - 정확한 generation 8단계와 verification 6단계, typed structured output, bounded question fan-out과 Backend command-only apply를 추가했다.
  - 문항별 partial success, retry seed reuse, restart idempotency와 verification failure/cancel compensation을 기존 orchestrator에 연결했다.
- Key decisions:
  - 현재 VERIFIED evidence만 긍정 근거로 사용하고 masked chunk는 탐색·모순 확인의 ephemeral context로 제한한다.
  - provider 호출은 transaction 밖에서 수행하고 checkpoint와 answer/verification apply를 원자적으로 완료한다.
- Issues encountered:
  - 성공 scope retry가 중복 version을 만들지 않도록 predecessor partial result를 새 Run seed에 병합했다.
- Validation:
  - Cover Letter workflow·orchestrator·Agent Run 테스트와 Backend 전체 377 tests, actual P7 partial success/retry DB assertions가 통과했다.
- Next steps:
  - 실제 provider 연결은 별도 승인·가격 정책 없이는 활성화하지 않는다.

## [2026-07-29] Session Summary (P6 JOB_ANALYSIS 고정 workflow·RAG)

- What was done:
  - snapshot→requirement→eligibility→verified retrieval→matching→score→validation→persist의 정확한 8단계를 실행 가능 contribution으로 등록했다.
  - structured record, prompt-injection 경계, owner-scoped retrieval, evidence allowlist와 Backend command-only apply를 구현했다.
- Key decisions:
  - requirement·eligibility·matching과 query embedding만 provider를 사용하고 score·validation·persist·reuse는 결정론적 local step으로 실행한다.
  - 동일 snapshot reuse도 공개 8단계를 유지하되 chat·embedding·search를 0회로 만들고 기존 analysis만 새 Run에 연결한다.
  - fresh/reuse domain apply와 완료 checkpoint는 gateway 호출 밖의 `SERIALIZABLE` transaction에서 함께 commit한다.
- Issues encountered:
  - 공통 completed-step 재실행 변경을 되돌리고 executor별 provider 생략 hook만 남겼으며 embedding dimension 하드코딩을 active policy port로 교체했다.
  - 1차 validator가 완료 checkpoint와 분석 저장 사이 crash window를 MAJOR로 판정해 rollback·commit 직후 crash/restart 회귀를 추가했다.
- Validation:
  - P6 workflow 11 tests, 완료 transaction 집중 검증 13 tests와 Backend 전체 352 tests가 통과했다.
  - 최종 validator가 atomic apply finding 해소를 확인했다. 전체 P6 verdict는 actual E2E 미검증 때문에 `FAIL`이다.
- Next steps:
  - 실제 provider 활성화 전 chat route product와 embedding product 정책 분리를 검증한다.

## [2026-07-27] Session Summary (P5 Job Posting Extraction workflow 연결)

- What was done:
  - URL fetch부터 sanitize·structured extract·override merge·domain apply까지 고정 5단계 workflow를 추가했다.
- Key decisions:
  - raw HTML·전체 prompt·provider response를 checkpoint에 저장하지 않고 사용자 입력을 AI 값보다 우선한다.
- Issues encountered:
  - 없음.
- Validation:
  - Job workflow 계약·orchestrator 통합 테스트와 전체 Backend check가 통과했다.
- Next steps:
  - P6 분석·RAG workflow는 별도 phase에서 추가한다.

## [2026-07-19] Session Summary (P4 Document ingestion workflow 연결)

- What was done:
  - 실제 Document aggregate에 연결되는 고정 8단계 workflow와 failure compensation을 추가했다.
- Key decisions:
  - masked chunk만 gateway에 전달하고 checkpoint·Step output에는 reference와 safe summary만 남긴다.
- Issues encountered:
  - V5 active model policy seed를 추가해 production Agent Step FK와 일치시켰다.
- Validation:
  - 성공·WAITING_USER resume·partial failure와 P3 orchestrator 회귀가 전체 check에서 통과했다.
- Next steps:
  - production 기본 provider `none`과 network-free failure를 유지한다.

## [2026-07-19] Session Summary (P3 고정 AI workflow 기반 구현)

- What was done:
  - canonical 8개 WorkflowType definition, executable contribution 분리와 고정 `AgentOrchestrator`를 구현했다.
  - ContextBuilder, ModelRouter, PromptRegistry, 5단계 structured validation과 Chat·Embedding·Search port를 구현했다.
  - test-scope `LOAD_FIXTURE → TRANSFORM_FIXTURE → APPLY_FIXTURE` PostgreSQL 시나리오를 추가했다.

- Key decisions:
  - P4 이후 workflow는 metadata만 있고 실행 handler는 등록하지 않는다.
  - production gateway는 안전한 configuration 오류만 반환하는 disabled adapter이며 network fallback이 없다.
  - immutable model policy version과 quality가 step input hash·checkpoint에 포함된다.

- Issues encountered:
  - JSONB field 순서에 따른 재사용 hash 차이를 canonical upstream result hash로 보정했다.
  - WAITING resume이 같은 attempt를 재개하도록 PENDING attempt를 보존했다.
  - 최초 Validator가 장시간 gateway 호출의 lease 만료 위험을 지적해 blocking call 전 구간에 주기 DB heartbeat port를 적용했다.

- Validation:
  - AI targeted 19 tests와 Backend 전체 243 tests가 통과했다.
  - success, transient/exhausted, structured failure, waiting/resume, cancel 두 경계, interruption, reuse 품질 제한을 검증했다.
  - 최종 read-only Validator가 호출 중 heartbeat와 repository/provider 경계를 포함해 `PASS`로 판정했다.

- Next steps:
  - 실제 provider와 domain executable contribution은 P4 이후 가격 catalog와 현재 heartbeat 경계를 유지해 연결한다.
