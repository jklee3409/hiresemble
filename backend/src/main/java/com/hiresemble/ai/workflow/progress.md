# Progress

## Overview

canonical workflow definition과 Document·Job·Cover Letter executable contribution 분리가 구현됐다.

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
