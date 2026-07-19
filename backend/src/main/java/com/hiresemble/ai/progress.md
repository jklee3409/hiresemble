# Progress

## Overview

P3 fixed workflow runtime 계약과 no-network gateway 기반이 구현됐고 실제 business workflow·provider adapter는 없다.

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
