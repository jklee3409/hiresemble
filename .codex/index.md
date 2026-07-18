# `.codex` 디렉터리 안내

## 디렉터리 목적

이 디렉터리는 저장소를 trusted project로 연 Codex가 사용하는 프로젝트 범위 런타임 설정을 관리한다. 개발 지침 자체는 루트 `AGENTS.md`와 `docs/agent-rules/`가 담당한다.

## 주요 파일 및 하위 디렉터리

| 경로                         | 역할                                                    |
| ---------------------------- | ------------------------------------------------------- |
| [`config.toml`](config.toml) | 프로젝트 문서 탐색과 서브 에이전트 동시성·깊이 설정     |
| [`agents/`](agents/)         | 백엔드·AI 워크플로·프론트엔드·검증 커스텀 에이전트 TOML |
| [`progress.md`](progress.md) | Codex 설정 변경 및 검증 이력                            |

현재 명령 실행 정책용 `rules/*.rules`, 프로젝트 Skill, MCP, Hook과 영구 handoff 체계는 없다. 필요해질 때 공식 형식과 실제 반복 사용 근거를 확인한 뒤 별도 도입한다.

## 다른 디렉터리와의 의존 관계

- [`../AGENTS.md`](../AGENTS.md)가 자동 로드되는 최상위 프로젝트 지침이다.
- [`../docs/agent-rules/`](../docs/agent-rules/)는 작업 유형별 세부 규칙을 제공한다.
- 루트 Codex 스레드가 관리자 역할을 맡고 [`agents/`](agents/)의 전문 역할을 직접 생성·조율한다.
- Codex는 저장소를 신뢰한 경우에만 이 디렉터리의 프로젝트 설정을 읽는다.

## 변경 시 주의사항

- 모델, provider, 인증, telemetry, 개인 알림과 같은 사용자별 설정을 저장소 설정으로 강제하지 않는다.
- sandbox와 approval을 완화하는 설정을 편의상 추가하지 않는다.
- 별도 `manager.toml`을 만들지 않고 `agents.max_depth = 1`로 전문 에이전트의 재귀 위임을 막는다.
- `agents/*.toml`만 네이티브 역할 정의로 사용하며 같은 목적의 Agent Markdown을 만들지 않는다.
- `validator`의 read-only는 기본값이므로 부모 세션 권한 override 가능성을 고려해 검증 전후 diff도 확인한다.
- `.codex/rules`는 Markdown 코딩 지침 폴더가 아니라 명령 prefix 정책 기능이다.
- 설정 키는 공식 Codex 문서에서 확인한 뒤 추가하고 TOML 구문을 검증한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../AGENTS.md)
- [개발 작업 절차](../docs/agent-rules/workflow.md)
- [디렉터리 진행 상황](progress.md)
