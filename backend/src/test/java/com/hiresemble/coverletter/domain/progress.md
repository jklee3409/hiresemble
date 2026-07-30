# Progress

## Overview

P7 TipTap content와 글자 수 domain 단위 테스트가 구현됐다.

## [2026-07-30] Session Summary (TipTap canonicalization 검증)

- What was done:
  - raw HTML/미허용 schema 거부와 CRLF·NBSP·NFC·zero-width·emoji·list/hardBreak 변환을 검증했다.
- Key decisions:
  - Unicode code point, 공백·줄바꿈 포함 규칙을 명시적 assertion으로 고정한다.
- Issues encountered:
  - 없음.
- Validation:
  - `TipTapCanonicalizerTest`와 Backend 전체 check가 통과했다.
- Next steps:
  - None.
