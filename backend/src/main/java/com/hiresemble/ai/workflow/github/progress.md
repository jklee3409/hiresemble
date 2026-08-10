# Progress

## Overview

Gate 1 public과 Gate 5 private access context를 함께 사용하는 `github-ingestion-v1` 10단계 workflow가 구현됐다.

## [2026-08-10] Session Summary (GitHub 한국어·핵심 후보 runtime 검증)

- What was done:
  - repository별 extraction 상한을 12개에서 3개로 줄이고 후보 제목과 본문에 한글이 모두 포함되지 않으면 structured correction 대상으로 거부하도록 했다.
- Key decisions:
  - prompt 지시만 신뢰하지 않고 workflow Java record 검증에서도 언어와 상한을 고정한다.
- Issues encountered:
  - 없음.
- Validation:
  - 한국어/상한 contract와 orchestrator integration을 포함한 Backend 전체 `check`가 통과했다.
- Next steps:
  - None.

## [2026-08-09] Session Summary (stateful validation 재사용 차단)

- What was done: refresh Run에서 `VALIDATE_GITHUB_SOURCE`가 checkpoint로 재사용되지 않고 `QUEUED -> RUNNING` 전이를 매번 수행하도록 했다.
- Key decisions: 순수 산출물 step의 재사용은 유지하고 상태 전이를 소유한 validation만 non-reusable로 고정했다.
- Issues encountered: 실제 첫 archive Run은 validation 재사용으로 finalize CAS가 실패했다.
- Validation: executor 계약 regression과 실제 후속 Run 10단계 성공을 확인했다.
- Next steps: None.

## [2026-08-09] Session Summary (Private access context workflow 연결)

- What was done: source access mode/connection을 workflow에서 해석해 기존 discovery·snapshot·canonical 단계에 전달했다.
- Key decisions: Run input/checkpoint에는 token·external installation ID를 복사하지 않고 실행 시 ACTIVE connection에서 해결한다.
- Issues encountered: 없음.
- Validation: private pipeline/security focused test와 Backend 전체 check가 통과했다.
- Next steps: webhook 기반 갱신은 deferred다.

## [2026-08-07] Session Summary (GitHub ingestion workflow 구현)

- What was done: discovery·wait/skip·snapshot·sanitize·extract·validate·embed·apply·finalize executor와 failure handler를 구현했다.
- Key decisions: repository 단위 bounded fan-out, tool-free strict extraction과 server-owned scope를 사용한다.
- Issues encountered: resume와 partial success에서 source 상태 및 성공 scope 전달을 보정했다.
- Validation: account/repository, partial, invalid ref, retry/cancel/SSE와 usage 테스트가 통과했다.
- Next steps: None.
