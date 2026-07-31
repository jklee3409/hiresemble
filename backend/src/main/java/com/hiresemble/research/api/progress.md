# Progress

## Overview

P8 조사 공개 API 4개 중 research 영역 4개 계약을 제공한다.

## [2026-07-31] Session Summary (조사 run·source·retry API)

- What was done:
  - run 상세, source 목록, retry 요청과 안전한 DTO mapping을 추가했다.
- Key decisions:
  - allowlist 밖 filter·sort는 400, owner 불일치는 404로 고정했다.
- Issues encountered:
  - None.
- Validation:
  - MockMvc status·validation·idempotency·404·retry 통합 검증이 통과했다.
- Next steps:
  - None.
