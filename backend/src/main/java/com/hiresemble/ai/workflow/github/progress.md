# Progress

## Overview

Gate 1 public과 Gate 5 private access context를 함께 사용하는 `github-ingestion-v1` 10단계 workflow가 구현됐다.

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
