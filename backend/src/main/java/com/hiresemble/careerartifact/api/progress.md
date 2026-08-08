# Progress

## Overview

Career Artifact 공개 API는 `hiresemble.career-artifact.enabled=true`일 때만 등록된다.

## [2026-08-08] Session Summary (Career Artifact API)

- What was done:
  - readiness·model catalog·create/list/detail/version·regenerate·archive/unarchive·download URL·delete endpoint와 DTO mapping을 구현했다.
- Key decisions:
  - create/regenerate만 idempotency key를 요구하며 다른 mutation은 optimistic version을 사용한다.
- Issues encountered:
  - None.
- Validation:
  - feature on/off OpenAPI와 owner·lifecycle·idempotency 통합 테스트를 통과했다.
- Next steps:
  - Gate 4 전용 화면은 별도 범위로 유지한다.
