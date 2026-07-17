# `.github/workflows` 디렉터리 안내

## 디렉터리 목적

이 디렉터리는 pull request와 기본 브랜치 변경을 검증하는 GitHub Actions workflow를 관리한다. 각 job은 저장소의 로컬 표준 명령을 깨끗한 Ubuntu runner에서 재현한다.

## 주요 파일 및 하위 디렉터리

| 경로                         | 역할                                                              |
| ---------------------------- | ----------------------------------------------------------------- |
| [`ci.yml`](ci.yml)           | 백엔드, 프론트엔드, Docker Compose 검증을 수행하는 CI workflow다. |
| [`progress.md`](progress.md) | workflow 구성과 원격 실행 확인 상태를 추적한다.                   |

현재 관리 대상 하위 디렉터리는 없다.

## 각 구성 요소의 역할

- `backend` job은 Temurin Java 21과 Gradle 환경을 준비하고 `backend`에서 `./gradlew check --no-daemon`을 실행한다.
- `frontend` job은 루트 `.nvmrc`의 Node.js를 준비하고 Corepack을 활성화한 뒤 `frontend`에서 frozen lockfile 설치와 `pnpm check`를 실행한다.
- `infrastructure` job은 루트에서 `docker compose config --quiet`를 실행해 Compose 구성을 검증한다.
- workflow는 `main` push와 pull request를 감시하며 같은 workflow/ref 조합의 이전 실행을 취소한다.

## 다른 디렉터리와의 의존 관계

- 백엔드 job은 [`../../backend/build.gradle.kts`](../../backend/build.gradle.kts), `gradlew`, 테스트와 Gradle task 정의에 의존한다.
- 프론트엔드 job은 [`../../frontend/package.json`](../../frontend/package.json), `pnpm-lock.yaml`, [`../../.nvmrc`](../../.nvmrc)에 의존한다.
- 인프라 job은 [`../../compose.yaml`](../../compose.yaml)과 그 환경 변수 기본값에 의존한다.
- 상위 [`.github/index.md`](../index.md)가 workflow와 Dependabot의 전체 관계를 설명한다.

## 변경 시 주의사항

- `working-directory`, 런타임 버전, package manager와 잠금 파일 모드는 로컬 개발 환경과 일치시킨다.
- job 이름은 branch protection의 status check 식별자로 사용될 수 있으므로 원격 설정을 확인하지 않고 임의로 바꾸지 않는다.
- workflow 권한과 secret 사용은 최소화하고, 사용자 데이터나 자격 증명을 로그에 출력하지 않는다.
- Action major version을 올릴 때 입력값과 runner 호환성을 검토한다.
- CI 전용 변경은 가능한 로컬 대응 명령을 실행하고, GitHub에서만 확인 가능한 부분은 [`progress.md`](progress.md)에 미검증으로 기록한다.

## 관련 규칙 및 문서

- [Codex 최상위 지침](../../AGENTS.md)
- [상위 GitHub 자동화 안내](../index.md)
- [인프라·환경 변수·CI 규칙](../../docs/agent-rules/infrastructure.md)
- [공통 작업 절차](../../docs/agent-rules/workflow.md)
- [workflow 진행 상황](progress.md)
