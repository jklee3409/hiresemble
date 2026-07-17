# `.codex` 디렉터리 안내

## 디렉터리 목적

이 디렉터리는 저장소를 trusted project로 연 Codex가 사용하는 프로젝트 범위 런타임 설정을 관리한다. 개발 지침 자체는 루트 `AGENTS.md`와 `docs/agent-rules/`가 담당한다.

## 주요 파일 및 하위 디렉터리

| 파일                         | 역할                                |
| ---------------------------- | ----------------------------------- |
| [`config.toml`](config.toml) | 프로젝트 문서 탐색 범위와 용량 설정 |
| [`progress.md`](progress.md) | Codex 설정 변경 및 검증 이력        |

현재 명령 실행 정책용 `rules/*.rules`는 없다. 필요해질 때 공식 Codex rules 문법과 팀의 승인 정책을 확인한 뒤 별도 도입한다.

## 다른 디렉터리와의 의존 관계

- [`../AGENTS.md`](../AGENTS.md)가 자동 로드되는 최상위 프로젝트 지침이다.
- [`../docs/agent-rules/`](../docs/agent-rules/)는 작업 유형별 세부 규칙을 제공한다.
- Codex는 저장소를 신뢰한 경우에만 이 디렉터리의 프로젝트 설정을 읽는다.

## 변경 시 주의사항

- 모델, provider, 인증, telemetry, 개인 알림과 같은 사용자별 설정을 저장소 설정으로 강제하지 않는다.
- sandbox와 approval을 완화하는 설정을 편의상 추가하지 않는다.
- `.codex/rules`는 Markdown 코딩 지침 폴더가 아니라 명령 prefix 정책 기능이다.
- 설정 키는 공식 Codex 문서에서 확인한 뒤 추가하고 TOML 구문을 검증한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../AGENTS.md)
- [개발 작업 절차](../docs/agent-rules/workflow.md)
- [디렉터리 진행 상황](progress.md)
