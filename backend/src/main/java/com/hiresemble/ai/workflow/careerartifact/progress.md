# Progress

## Overview

Resume와 Portfolio canonical 8단계 workflow가 executable contribution으로 등록됐다.

## [2026-08-09] Session Summary (실제 provider grounding 경계 보정)

- What was done:
  - PLAN·DRAFT·FACT_CHECK의 provider output을 승인 evidence metadata로 canonicalize한 뒤 검증·minimal hash·ephemeral render handoff가 같은 값을 사용하도록 통일했다.
  - 선택 profile을 최종 content validator에 전달하고 unknown evidence reference를 correction-once 대상으로 유지하되 미승인 ID는 끝까지 거부하도록 했다.
- Key decisions:
  - provider content 전체를 checkpoint에 저장하지 않는 기존 privacy와 모든 Career Artifact step non-reusable 계약은 유지했다.
- Issues encountered:
  - fact-check가 이미 유효한 evidence title/usage를 다시 표현해 deterministic failure가 발생했다.
- Validation:
  - 실제 `gpt-5.6-luna` Portfolio와 Resume가 PLAN→DRAFT→FACT_CHECK→Office render/validate→persist 8단계를 각각 완료했다.
- Next steps:
  - None.

## [2026-08-08] Session Summary (Career Artifact 고정 workflow)

- What was done:
  - LOAD→CONTEXT→PLAN→DRAFT→FACT_CHECK→RENDER→VALIDATE→PERSIST 순서와 restart-safe local step을 구현했다.
- Key decisions:
  - PLAN/DRAFT/FACT_CHECK만 같은 exact model을 한 번씩 호출하고 tool allowlist는 비우며 Office byte와 전체 content는 checkpoint에 저장하지 않는다.
- Issues encountered:
  - render/validate 재시작은 byte checkpoint 대신 deterministic input으로 안전하게 재실행하도록 구성했다.
- Validation:
  - step 순서, prompt/schema/version, model 일치, privacy와 correction 상한 contract test를 통과했다.
- Next steps:
  - 실제 유료 provider 평가는 별도 명시적 승인 없이는 수행하지 않는다.
