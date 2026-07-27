# Progress

## Overview

P3 provenance-only ContextBuilder와 Document·Job owner/version snapshot이 구현됐다.

## [2026-07-27] Session Summary (Job Posting Extraction context 추가)

- What was done:
  - Job owner·resource version과 사용자 override를 검증하는 context builder와 workflow dispatch를 추가했다.
- Key decisions:
  - 전체 공고 본문·HTML은 durable context에 저장하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - owner/version 재검증과 context hash 통합 테스트가 통과했다.
- Next steps:
  - P6 context는 승인 근거 reference와 새 analysis snapshot을 별도 추가한다.

## [2026-07-19] Session Summary (Document ingestion context 추가)

- What was done:
  - typed Document owner·resource와 source revision을 검증하는 context builder를 추가했다.
- Key decisions:
  - raw document text는 context snapshot에 저장하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 타 사용자 resource 404와 typed owner resolution이 통과했다.
- Next steps:
  - P5 resource context는 해당 aggregate가 생긴 뒤 추가한다.

## [2026-07-19] Session Summary (안전한 context snapshot 계약 구현)

- What was done:
  - user scope, resource/version/hash, upstream output, truncation, verification와 model policy projection을 정의했다.

- Key decisions:
  - profile repository를 직접 횡단하지 않고 후속 domain query port를 기다린다.

- Issues encountered:
  - None.

- Validation:
  - Fake 3-step의 input hash·reuse·quality 분리 테스트가 통과했다.

- Next steps:
  - 각 workflow phase에서 승인 evidence query port를 구현한다.
