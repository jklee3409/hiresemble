# Progress

## Overview

백엔드, AI 워크플로, 프론트엔드와 읽기 전용 검증을 위한 프로젝트 전용 Codex 커스텀 에이전트 정의를 관리한다.

## [2026-07-17] Session Summary (전문 Codex 커스텀 에이전트 기초 정의)

- What was done:
  - `backend`, `ai_workflow`, `frontend`, `validator` 역할 TOML과 디렉터리 안내 문서를 생성했다.
  - 루트 스레드를 관리자로 유지하고 역할별 선택 조건, 수정 경계, 금지 사항과 구조화 결과 형식을 정의했다.

- Key decisions:
  - 공통 운영 규칙은 루트 `AGENTS.md`에 두고 역할 TOML에는 전문 영역의 차이만 기록한다.
  - 모델과 권한은 부모 세션에서 상속하고 검증 역할만 `read-only` sandbox를 명시한다.

- Issues encountered:
  - `codex exec --ephemeral`에서 첫 subagent 생성 시 루트 thread ID를 찾지 못하는 오류가 발생했다. 일반 read-only exec에서는 `backend`가 정상 생성되어 Agent TOML 인식 오류와 구분했다.
  - 부모 세션의 실시간 sandbox·approval override가 역할 기본값보다 우선할 수 있으므로 validator의 read-only 설정만을 절대적인 강제로 간주할 수 없다.

- Validation:
  - Python `tomllib`로 네 TOML의 구문과 `name`, `description`, `developer_instructions`, validator의 `sandbox_mode = "read-only"`를 확인했다.
  - trusted 프로젝트의 strict config read-only Codex 실행에서 `backend`, `ai_workflow`, `frontend`, `validator`가 실제 custom agent로 응답함을 확인했다.

- Next steps:
  - 향후 위임 시 역할별 파일 소유권과 완료 조건을 먼저 확정하고 validator 실행 전후 diff를 확인한다.
  - ephemeral exec의 subagent thread 오류는 Codex CLI 후속 버전에서 재검증한다.
