# Progress

## Overview

P4 parser·JDBC·S3-compatible storage·embedding query·deletion outbox adapter를 구현했다.

## [2026-07-19] Session Summary (Document infrastructure 구현)

- What was done:
  - 정상 PDF/DOCX/TXT, 위장·macro·암호화·손상·크기·timeout 방어와 MinIO upload/head/presign/delete를 구현했다.
  - `vector(1536)` exact cosine owner/generation/active-document query와 outbox lease·10회 retry를 구현했다.
- Key decisions:
  - 20 MiB와 parser resource limit을 configuration으로 고정하고 Object absent delete는 성공으로 처리한다.
- Issues encountered:
  - parser timeout은 테스트에서 `Duration.ZERO`와 최대 경계 fixture로 결정적으로 검증했다.
- Validation:
  - 실제 MinIO Testcontainer와 PostgreSQL integration, user·generation·deleted 격리, lease recovery·duplicate worker·DEAD가 통과했다.
- Next steps:
  - ANN index와 실제 embedding provider는 P4 범위 밖이다.
