# `.codex/agents` 디렉터리 안내

## 디렉터리 목적

이 디렉터리는 trusted project에서 Codex가 전문 개발 서브 에이전트로 로드하는 프로젝트 범위 TOML 정의를 관리한다. 루트 Codex 스레드가 관리자이자 오케스트레이터이며, 이 디렉터리에는 별도 관리자 역할을 두지 않는다.

## 주요 파일

| 파일                                   | 책임                                                |
| -------------------------------------- | --------------------------------------------------- |
| [`backend.toml`](backend.toml)         | 일반 Spring API·보안·DB·Storage·문서 처리 구현      |
| [`ai-workflow.toml`](ai-workflow.toml) | Spring AI·RAG·모델 정책·통제형 런타임 워크플로 구현 |
| [`frontend.toml`](frontend.toml)       | Vue UI·상태·API 연동·SSE·프론트엔드 검증            |
| [`validator.toml`](validator.toml)     | 변경 없는 독립 검증과 Finding 반환                  |
| [`progress.md`](progress.md)           | 커스텀 에이전트 정의와 로드 검증 이력               |

`index.md`와 `progress.md`는 디렉터리 추적 문서일 뿐 커스텀 에이전트 정의가 아니다. Codex가 네이티브 역할로 인식하는 파일은 위 네 TOML이다.

## 역할 경계

- 일반 Spring 계약과 AI 워크플로가 같은 DTO·DB·Spring 파일에 영향을 주면 루트 관리자가 계약과 파일 소유권을 먼저 정하고 순차 위임한다.
- 읽기 중심 분석은 병렬화할 수 있지만 쓰기 작업은 서로 겹치지 않는 파일 범위로만 위임한다.
- 서브 에이전트는 다른 서브 에이전트를 만들거나 추적 문서를 직접 갱신하지 않는다.
- `validator`는 `sandbox_mode = "read-only"`이며 수정 제안만 반환한다.

## 변경 시 주의사항

- 모든 역할의 공통 규칙은 [`../../AGENTS.md`](../../AGENTS.md)에 두고 TOML에는 선택 조건과 역할 차이만 기록한다.
- 프로젝트 모델, provider, 인증, approval 정책을 역할 파일에서 강제하지 않는다.
- 역할 지침용 `.md`, 임시 handoff 디렉터리, 불필요한 Skill·Hook·MCP·`.rules`를 추가하지 않는다.
- 네 역할 파일은 `name`, `description`, `developer_instructions`를 유지하고 현재 Codex 버전에서 strict config와 실제 로드를 검증한다.

## 관련 문서

- [프로젝트 Codex 설정 안내](../index.md)
- [최상위 작업 지침](../../AGENTS.md)
- [문서 추적 규칙](../../docs/agent-rules/documentation-tracking.md)
- [진행 상황](progress.md)
