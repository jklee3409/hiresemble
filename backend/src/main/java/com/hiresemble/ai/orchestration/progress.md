# Progress

## Overview

fixed-sequence AgentOrchestrator와 checkpoint·multi-usage·apply, deterministic reuse·partial seed 및 atomic completion 경계가 구현됐다.

## [2026-08-02] Session Summary (검증 후 context-aware output mapping hook)

- What was done:
  - Provider 검증값을 현재 Run 상태가 포함된 내부 DTO로 바꿀 수 있도록 minimal·ephemeral output에 context-aware 기본 hook을 추가했다.
- Key decisions:
  - 기존 workflow executor는 이전 context-free 기본 메서드로 그대로 동작하고 Job Analysis Provider executor만 새 hook을 강제한다.
- Issues encountered:
  - None.
- Validation:
  - 공통 hook 기본값 계약과 Job Analysis mapping 집중 테스트는 통과했다. 전체 506 tests에서는 범위 밖 Object Deletion Outbox 2건만 실패했다.
- Next steps:
  - 새 Provider/internal DTO 분리 사용처도 Provider DTO 자체를 checkpoint에 저장하지 않는다.

## [2026-08-01] Session Summary (공용 partial 오류 하드코딩 제거)

- What was done:
  - terminal failed scope 판정을 contribution policy에 위임하고 공용 `COVER_LETTER_GENERATION_PARTIAL_FAILURE` 하드코딩을 제거했다.
- Key decisions:
  - success policy는 budget settle 후 100% 성공, failure policy는 unused reservation release와 workflow safe error를 사용한다.
- Issues encountered:
  - candidate filtering 통계와 partial execution 결과가 같은 accumulator에 섞여 있었다.
- Validation:
  - 정책 성공·실패, Cover Letter partial과 usage/budget 통합 회귀 통과.
- Next steps:
  - partial 통계를 failed scope로 변환하지 않는 규칙을 유지한다.

## [2026-08-01] Session Summary (bounded structured repair retry)

- What was done:
  - exception별 attempt cap을 적용하고 repairable structured retry에만 safe correction guidance를 추가했다.
- Key decisions:
  - `maxModelCalls`는 attempt 내부 상한이며 모든 retry Provider 호출은 새 attempt·usage로 기록한다.
- Issues encountered:
  - None.
- Validation:
  - transient 3회, semantic 최대 2회, deterministic 1회와 tier 승격 회귀 통과.
- Next steps:
  - P8.8 UX 확장 전에도 raw invalid value는 checkpoint에 남기지 않는다.

## [2026-08-01] Session Summary (Provider multi-usage·failure accounting)

- What was done:
  - gateway response와 safe provider exception의 usage list를 validation/retry 전에 item별로 기록하도록 확장했다.
- Key decisions:
  - 호출 비용 합계를 한 번 top-up 검사한 뒤 row별로 누적한다.
- Issues encountered:
  - None.
- Validation:
  - structured failure·retry·cancel·actual E2E 회귀가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 partial scope retry·interruption 경계)

- What was done:
  - predecessor partial result seed를 retry Run에 전달하고 성공 scope checkpoint/domain result를 재사용하도록 orchestration context를 확장했다.
  - 실패·취소 시 workflow resource compensation을 단일 interruption service로 연결했다.
- Key decisions:
  - fresh/reused checkpoint와 domain apply의 기존 `SERIALIZABLE` 원자성을 유지하고 provider 호출은 transaction 밖에 둔다.
- Issues encountered:
  - 없음.
- Validation:
  - partial success·retry·cancel·restart·commit interruption 회귀와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 reuse provider routing·atomic completion 보강)

- What was done:
  - executor context에 따라 model-backed step의 compatible reuse branch만 provider routing을 생략하는 hook을 추가했다.
  - fresh/reuse domain apply와 성공 checkpoint를 실제 Spring `SERIALIZABLE` transaction으로 묶었다.
- Key decisions:
  - 기존 workflow 기본값은 provider required이며 gateway·usage·validation은 transaction 밖에 두고 완료된 checkpoint만 committed domain apply를 보장한다.
- Issues encountered:
  - 1차 validator가 checkpoint 후 별도 apply의 crash window를 MAJOR로 확인해 부분 domain write와 성공 checkpoint를 함께 rollback하도록 보정했다.
- Validation:
  - fresh/reuse rollback과 commit 직후 crash/restart를 포함한 orchestrator 13 tests, P6 workflow 11 tests와 Backend 352 tests가 통과했다.
  - 최종 read-only validator가 atomic completion MAJOR 해소를 확인했다.
- Next steps:
  - None.

## [2026-07-19] Session Summary (Document failure handler·resource 실행 연결)

- What was done:
  - workflow별 failure handler와 Document stable compensation을 orchestrator에 연결했다.
- Key decisions:
  - terminal Run은 다시 열지 않고 WAITING_USER active Run만 비용 재예약 뒤 resume한다.
- Issues encountered:
  - None.
- Validation:
  - retry lineage, cancel stable mapping, resource-linked generic retry와 P3 회귀가 통과했다.
- Next steps:
  - 자유 loop나 resource repository 직접 접근을 추가하지 않는다.

## [2026-07-19] Session Summary (bounded AgentOrchestrator 구현)

- What was done:
  - claim된 Run을 registry 순서로 실행하고 호출 중 주기 heartbeat, cancel, reuse, budget, validation, usage와 idempotent apply를 연결했다.
  - WAITING resume, terminal failure·interruption과 reserve 정리를 구현했다.

- Key decisions:
  - checkpoint와 domain apply는 port별 transaction으로 수행하고 gateway 호출은 transaction 밖에 둔다.

- Issues encountered:
  - resume 시 PENDING attempt를 그대로 재개하고 model policy version을 context에서 checkpoint까지 전달하도록 보정했다.
  - 최초 Validator 지적에 따라 gateway 호출 전후의 단발 heartbeat를 별도 scheduler 기반 주기 heartbeat port로 교체했다.

- Validation:
  - 실제 PostgreSQL Fake 3-step integration과 lease보다 긴 blocking gateway/reconciliation 경쟁 테스트가 통과했다.

- Next steps:
  - P4 이후 실제 contribution은 현재 heartbeat port를 재사용하고 provider별 timeout을 추가한다.
