# Progress

## Overview

GitHub Source application 구현 상태를 추적한다.

## [2026-08-07] Session Summary (GitHub source use case와 workflow port)

- What was done: source 등록/query, atomic repository selection, same-run resume, refresh/delete와 workflow query/command port를 구현했다.
- Key decisions: selection은 source version과 발견 repository owner를 재검증하고 전체 집합을 원자 교체한다.
- Issues encountered: same SHA refresh를 새 Run·AI 비용 없이 응답하는 선행 조회 경계를 분리했다.
- Validation: API·workflow·two-user·idempotency 통합 테스트가 통과했다.
- Next steps: None.
