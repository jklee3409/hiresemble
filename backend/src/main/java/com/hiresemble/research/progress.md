# Progress

## Overview

P8 공개 조사 결과, coverage, source provenance와 retry가 구현되어 있다.

## [2026-07-31] Session Summary (P8 면접 공개 조사 영역 구현)

- What was done:
  - 조사 run·topic·source·coverage 조회와 resource-specific retry를 구현했다.
- Key decisions:
  - URL은 run 안에서 canonical dedupe하고 원문 대신 제한 metadata·snippet만 보존한다.
- Issues encountered:
  - nullable source filter SQL의 PostgreSQL 타입 추론을 명시적 cast로 보정했다.
- Validation:
  - 조사 API·migration·workflow 통합 테스트와 P8 actual의 SUFFICIENT/LIMITED/NONE/FAILED·retry 분기가 통과했다.
- Next steps:
  - 실제 Tavily 활성화와 운영 key 주입은 별도 운영 승인 범위다.
