# Progress

## Overview

P7 자기소개서 공개 API 18개의 validation·HTTP status·DTO·OpenAPI 계약이 구현됐다.

## [2026-08-06] Session Summary (AI model catalog API)

- What was done: `GET /api/v1/cover-letters/ai-models`를 추가하고 generate·verify request를 `model` 기반으로 변경했다.
- Key decisions: dropdown metadata는 서버가 제공하고 미지원 model은 `AI_MODEL_NOT_SUPPORTED`로 거부한다.
- Issues encountered: None.
- Validation: OpenAPI 70 paths·95 operations 계약과 Backend 전체 `check` 통과.
- Next steps: catalog 응답은 장기 cache하지 않아 서버 allowlist 변경을 즉시 반영한다.

## [2026-07-30] Session Summary (P7 자기소개서 API)

- What was done:
  - 생성·목록·상세·제목, 문항 CRUD/order, generation, 답변 version save/restore, verify/list, finalize/archive/unarchive endpoint를 추가했다.
- Key decisions:
  - 생성·generation·verify는 Idempotency-Key와 실제 201/202를 사용하고 owner/CAS/archive/finalize 충돌은 공통 404/409 계약으로 반환한다.
- Issues encountered:
  - 없음.
- Validation:
  - API 통합 테스트와 생성 OpenAPI 51 paths/70 operations, Backend 전체 377 tests가 통과했다.
- Next steps:
  - 독립 validator 판정을 반영한다.
