# Progress

## Overview

P4 문서 공개 API 8개와 완전한 DTO·상태·오류 계약을 구현했다.

## [2026-07-19] Session Summary (문서 HTTP API 8개 고정)

- What was done:
  - upload·list·detail·text·manual-text·reparse·download-url·delete를 구현했다.
- Key decisions:
  - 공개 응답에서는 storage·checksum·parser·embedding·provider 내부 정보를 제외한다.
- Issues encountered:
  - None.
- Validation:
  - 생성 OpenAPI 43 operations/30 paths와 auth·CSRF·owner·413·415·429·503 계약이 통과했다.
- Next steps:
  - P5 이후 공개 API를 이 Controller에 추가하지 않는다.
