# Progress

## Overview

백엔드, AI 워크플로, 프론트엔드와 읽기 전용 검증을 위한 프로젝트 전용 Codex 커스텀 에이전트 정의를 관리한다.

## [2026-07-18] Session Summary (Agent 보호 경로와 런타임 식별 마커 보완)

- What was done:
  - `backend`, `ai_workflow`, `frontend`에 `.codex`, instruction·추적 문서, 다른 역할 설정, 환경·인증 파일의 직접 수정 금지를 추가했다.
  - 공유 DTO·DB migration·API 계약은 루트 관리자가 소유권과 순서를 확정하고, 겹치는 할당은 `BLOCKED`로 반환하도록 명시했다.
  - 네 역할에 서로 다른 런타임 식별 마커를 추가하고 Validator에 파일·Patch·자동 수정·하위 Agent 생성 금지를 명시했다.

- Key decisions:
  - 마커는 각 `developer_instructions`에만 두고 루트 지침이나 안내 문서에는 복사하지 않는다.
  - 파일명보다 `name`이 식별자이므로 `ai-workflow.toml`과 `ai_workflow` 매핑은 유지한다.

- Issues encountered:
  - fresh Spawn은 네 요청 역할 이름으로 생성됐지만 어느 역할도 자신의 마커를 반환하지 못했다. 자기 선언만으로 실제 미인식이라 단정하지 않고 `NOT_VERIFIED`로 유지한다.
  - Validator 부모 Sandbox는 read-only로 확인됐지만 Validator custom profile 마커 자체는 확인되지 않았다.

- Validation:
  - Agent TOML 4개의 문법, 필수 필드, 중복 없는 이름과 마커, 보호 경로 및 Validator `sandbox_mode=read-only`를 정적으로 확인했다.
  - 구현 역할 3개와 Validator 1개의 총 4회 Spawn에서 파일 수정·하위 생성·재시도가 없었고, 스모크 전후 diff hash가 동일했다.

- Next steps:
  - 런타임이 활성 custom profile 메타데이터를 제공하면 TOML을 직접 읽지 않는 역할별 마커 검증을 다시 수행한다.

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
