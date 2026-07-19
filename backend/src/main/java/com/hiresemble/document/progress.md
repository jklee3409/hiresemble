# Progress

## Overview

P4 Document aggregate와 parsing·Object Storage·Agent Run·Fake AI evidence pipeline을 owner-scoped 수명주기로 구현했다.

## [2026-07-19] Session Summary (P4 Document aggregate와 근거 pipeline 구현)

- What was done:
  - 문서 API 8개, 두 상태 축, text·chunk·embedding 영속화, manual same-run resume, reparse 새 lineage와 삭제 outbox를 구현했다.
  - PDF·DOCX·TXT parser, MIME·macro·손상·크기 검증, normalization·masking·결정적 chunk와 MinIO 호환 storage를 연결했다.

- Key decisions:
  - storage key는 `users/{userId}/documents/{documentId}/content`, chunk policy는 `paragraph-page-v1`, download TTL은 5분으로 고정했다.
  - parse 성공 뒤 AI 실패에는 `PARSED`와 text·chunk를 보존하고 evidence 축과 Run만 안전 실패시킨다.

- Issues encountered:
  - 실제 E2E Fake usage가 price version만 기록해 V4 price pair 제약을 위반한 문제를 Fake price item 두 개를 seed하고 참조하도록 바로잡았다.
  - 최초 Validator가 Agent Run Document filter의 예약 404를 발견해 active owner resolver와 성공·격리·삭제 테스트를 한 차례 보정했다.

- Validation:
  - Backend `check` 30 suites/287 tests와 별도 실제 Browser E2E 4/4가 통과했다.
  - PostgreSQL 18+pgvector, MinIO, Spring, Vue, Fake embedding·extraction과 Chromium을 격리 환경에서 연결했다.
  - 최종 read-only Validator 판정은 `PASS`다.

- Next steps:
  - P4는 AC-03까지만 완료하며 P6의 전체 RAG와 실제 provider 연결은 포함하지 않는다.
