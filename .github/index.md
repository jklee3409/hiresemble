# `.github` 디렉터리 안내

## 디렉터리 목적

이 디렉터리는 GitHub에서 실행되는 지속적 통합(CI)과 의존성 업데이트 자동화 설정을 관리한다. 로컬에서 사용하는 백엔드·프론트엔드·Compose 검증 명령을 원격 저장소의 변경 검증 흐름과 연결한다.

## 주요 파일 및 하위 디렉터리

| 경로                               | 역할                                                                                                                                      |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| [`dependabot.yml`](dependabot.yml) | Gradle, npm, Docker, GitHub Actions 의존성을 매주 확인하고 영역별 라벨을 붙인 업데이트 PR을 생성한다. 생태계별 동시 오픈 PR은 최대 5개다. |
| [`workflows/`](workflows/)         | GitHub Actions workflow를 관리한다. 현재 CI workflow 하나가 있다.                                                                         |
| [`progress.md`](progress.md)       | GitHub 자동화 설정의 현재 상태, 결정, 검증 이력을 추적한다.                                                                               |

## 각 구성 요소의 역할

- Dependabot은 `backend/`의 Gradle, `frontend/`의 npm, 루트의 Docker와 GitHub Actions 버전을 각각 독립적으로 추적한다.
- [`workflows/ci.yml`](workflows/ci.yml)은 백엔드, 프론트엔드, Docker Compose의 세 검증 job을 병렬 실행할 수 있도록 정의한다.
- 이 디렉터리의 추적 문서는 자동화 설정 자체와 원격 실행 확인 여부를 구분해 기록한다.

## 다른 디렉터리와의 의존 관계

- 백엔드 CI와 Dependabot은 [`../backend/build.gradle.kts`](../backend/build.gradle.kts), Gradle Wrapper와 백엔드 검증 task에 의존한다.
- 프론트엔드 CI와 Dependabot은 [`../frontend/package.json`](../frontend/package.json), `pnpm-lock.yaml`, 루트 [`.nvmrc`](../.nvmrc)에 의존한다.
- 인프라 CI와 Docker 의존성 추적은 [`../compose.yaml`](../compose.yaml)을 기준으로 한다.
- 작업 절차와 최소 권한·비밀값 규칙은 [`../docs/agent-rules/infrastructure.md`](../docs/agent-rules/infrastructure.md)를 따른다.

## 변경 시 주의사항

- workflow 권한은 기본 `contents: read`를 유지하고 필요한 권한만 명시적으로 추가한다.
- Action 또는 의존성 버전 변경은 changelog, 런타임 호환성, 잠금 파일 영향을 PR에서 확인한다.
- CI 명령은 각 모듈의 로컬 표준 검증 명령과 일치시킨다. 로컬에서 재현할 수 없는 검증은 이유와 원격 확인 방법을 `progress.md`에 기록한다.
- secret을 workflow나 로그에 직접 노출하지 않고 fork PR에서 secret이 없을 때도 안전하게 동작하도록 한다.
- 하위 workflow의 구조나 검증 책임이 바뀌면 [`workflows/index.md`](workflows/index.md)와 양쪽 `progress.md`를 함께 갱신한다.

## 관련 규칙 및 문서

- [Codex 최상위 지침](../AGENTS.md)
- [루트 구조 안내](../index.md)
- [공통 작업 절차](../docs/agent-rules/workflow.md)
- [인프라·환경 변수·CI 규칙](../docs/agent-rules/infrastructure.md)
- [GitHub 자동화 진행 상황](progress.md)
- [CI workflow 안내](workflows/index.md)
