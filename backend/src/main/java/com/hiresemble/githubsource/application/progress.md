# Progress

## Overview

GitHub Source와 App connection application 구현 상태를 추적한다.

## [2026-08-10] Session Summary (GitHub 후보 provenance 상한 정렬)

- What was done:
  - model output 뒤 source-unit·수치 근거를 확인하는 provenance validator도 repository별 최대 3개만 수용하도록 workflow 상한과 맞췄다.
- Key decisions:
  - 초과 후보는 기존 `LIMIT_EXCEEDED` reason count로 집계해 부분 성공 통계를 유지한다.
- Issues encountered:
  - 중단된 변경에서는 prompt/workflow만 3개였고 provenance validator는 12개를 허용하는 불일치가 남아 있었다.
- Validation:
  - 13개 입력에서 3개 수용·10개 제한 초과를 검증했고 Backend 전체 `check`가 통과했다.
- Next steps:
  - None.

## [2026-08-09] Session Summary (실패 source refresh와 공개 archive orchestration)

- What was done: `FAILED` source를 새 Run으로 refresh하고 저장된 direct repository metadata와 archive commit을 재사용하도록 조정했다.
- Key decisions: 실패 상태는 commit 동일 여부와 무관하게 새 Run을 만들며 initial registration과 private 접근의 기존 검증은 유지한다.
- Issues encountered: 실패 source의 동일 commit 최적화가 복구 Run 생성을 막았다.
- Validation: API regression과 실제 대상 source 복구가 통과했다.
- Next steps: None.

## [2026-08-09] Session Summary (Connection·private source application)

- What was done: state/PKCE lifecycle, owner installation 검증, private source parity, refresh/disconnect orchestration을 구현했다.
- Key decisions: 검증 성공 뒤에만 ACTIVE를 저장하고 disconnect 접수 즉시 token mint를 막는다.
- Issues encountered: setup installation ID spoof와 callback replay/session mismatch를 transaction CAS로 차단했다.
- Validation: connection/pipeline integration과 Backend 전체 check가 통과했다.
- Next steps: manual external installation 검증 대기다.

## [2026-08-07] Session Summary (GitHub source use case와 workflow port)

- What was done: source 등록/query, atomic repository selection, same-run resume, refresh/delete와 workflow query/command port를 구현했다.
- Key decisions: selection은 source version과 발견 repository owner를 재검증하고 전체 집합을 원자 교체한다.
- Issues encountered: same SHA refresh를 새 Run·AI 비용 없이 응답하는 선행 조회 경계를 분리했다.
- Validation: API·workflow·two-user·idempotency 통합 테스트가 통과했다.
- Next steps: None.
