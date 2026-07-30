# Progress

## Overview

P7 상태·source·verification enum과 서버 authoritative TipTap canonicalization·글자 수 정책이 구현됐다.

## [2026-07-30] Session Summary (P7 상태·TipTap domain)

- What was done:
  - DRAFT/FINALIZED/ARCHIVED, answer source, verification status/issue와 제한된 TipTap JSON 검증·canonical plain text를 추가했다.
- Key decisions:
  - 글자 수는 markup·zero-width를 제외한 Unicode code point로 계산하고 공백·줄바꿈은 포함한다.
- Issues encountered:
  - 없음.
- Validation:
  - 허용/금지 node·mark, CRLF/NBSP/NFC, emoji·list·hardBreak와 maxLength 단위 테스트가 통과했다.
- Next steps:
  - None.
