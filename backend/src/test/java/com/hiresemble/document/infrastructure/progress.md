# Progress

## Overview

P4 parser·embedding policy·MinIO adapter 경계를 실제 dependency와 container로 검증한다.

## [2026-07-19] Session Summary (Parser·MinIO·embedding policy 검증)

- What was done:
  - 정상 PDF/DOCX/TXT와 empty·oversize·위장·macro·암호화·corrupt·timeout fixture를 추가했다.
  - 실제 MinIO private object, 5분 presign, delete와 `vector(1536)` policy mismatch를 검증했다.
- Key decisions:
  - OCR·HWP·PPTX는 지원하지 않고 DOCM과 embedded active content를 거부한다.
- Issues encountered:
  - 20 MiB 정확 경계는 유효 PDF prefix 뒤 padding fixture로 고정했다.
- Validation:
  - targeted test와 Backend 전체 `check`가 통과했다.
- Next steps:
  - 실제 provider adapter는 이 테스트 profile에 등록하지 않는다.
