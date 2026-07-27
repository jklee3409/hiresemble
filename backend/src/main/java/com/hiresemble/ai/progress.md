# Progress

## Overview

P3 fixed workflow runtime과 no-network gateway 기반에 P4 Document와 P5 Job business workflow가 연결됐고 실제 provider adapter는 없다.

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
