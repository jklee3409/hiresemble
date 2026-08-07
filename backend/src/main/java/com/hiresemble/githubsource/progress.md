# Progress

## Overview

Phase 1 Gate 1 public GitHub Source Backend가 구현됐다. Frontend와 private repository는 범위 밖이다.

## [2026-08-07] Session Summary (GitHub Source vertical 구현)

- What was done:
  - URL/source lifecycle, command/query, REST API, JDBC, public GitHub gateway, bounded snapshot·sanitizer와 deletion outbox를 구현했다.
- Key decisions:
  - production 기본 비활성, public-only, no credential/clone/code execution, 별도 snapshot lifecycle을 적용했다.
- Issues encountered:
  - account discovery와 repository direct 흐름을 하나의 source aggregate와 workflow에서 안전하게 분기했다.
- Validation:
  - domain·gateway·canonical·workflow·API·migration 집중 테스트가 통과했다.
- Next steps:
  - Gate 2 Frontend가 이 모듈의 7개 operation을 소비한다.
