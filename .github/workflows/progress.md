# Progress

## Overview

- `ci.yml` 하나가 `main` push와 pull request를 대상으로 등록되어 있다.
- Backend job은 Java 21에서 Gradle `check`, Frontend job은 `.nvmrc`와 frozen pnpm lockfile을 사용한 `pnpm check`, Docker Compose job은 `docker compose config --quiet`를 실행한다.
- P4 Browser E2E job은 Java 21·Node·Chromium과 Testcontainers로 실제 Document pipeline을 격리 실행한다.
- 네 job은 서로 독립적이며 workflow 전체 권한은 `contents: read`다.
- 실제 GitHub-hosted runner 실행 상태는 아직 확인되지 않았다.

## [2026-07-19] Session Summary (P4 실제 Browser E2E CI 추가)

- What was done:
  - frozen Frontend dependency와 Chromium을 준비하고 `p4BrowserE2eTest`를 실행하는 독립 job을 추가했다.
- Key decisions:
  - API key·운영 인프라 없이 Testcontainers PostgreSQL·MinIO와 test-scope Fake AI만 사용한다.
- Issues encountered:
  - None.
- Validation:
  - 동일 Gradle task가 Windows 로컬 격리 환경에서 Playwright 4/4로 통과했다. GitHub-hosted runner 실행은 commit·push 금지로 미실행이다.
- Next steps:
  - 첫 push/PR에서 Ubuntu Docker·Chromium 설치와 20분 timeout을 확인한다.

## [2026-07-17] Session Summary (모듈별 CI 워크플로 구성)

- What was done:
  - 당시 구현 상태:
    - `ci.yml` 하나가 `main` push와 pull request를 대상으로 등록되어 있다.
    - Backend job은 Java 21에서 Gradle `check`, Frontend job은 `.nvmrc`와 frozen pnpm lockfile을 사용한 `pnpm check`, Docker Compose job은 `docker compose config --quiet`를 실행한다.
    - 세 job은 서로 독립적이며 workflow 전체 권한은 `contents: read`다.
    - 실제 GitHub-hosted runner 실행 상태는 아직 확인되지 않았다.
  - 완료된 작업:
    - 백엔드, 프론트엔드, 인프라 검증을 한 workflow 안의 세 job으로 구성했다.
    - 중복 실행 비용을 줄이도록 workflow/ref 기준 concurrency와 진행 중 실행 취소를 설정했다.
    - 작업 목적에 따라 `index.md`, `progress.md`를 생성해 CI 파일의 책임, 의존성, 검증 경계를 문서화했다.
  - 당시 진행 중인 작업:
    - 현재 workflow 파일 자체의 변경 작업은 없다.

- Key decisions:
  - 모듈별 실패 원인을 분리하고 병렬 실행할 수 있도록 세 job 구조를 유지한다.
  - 프론트엔드는 재현 가능한 설치를 위해 `pnpm install --frozen-lockfile`을 사용한다.
  - CI는 실제 유료 AI·검색 API나 운영 인프라에 연결하지 않고 로컬 대응 검증만 실행한다.

- Issues encountered:
  - 로컬 검증은 GitHub runner image, Action 동작, 원격 권한과 branch protection 설정까지 보장하지 않는다.
  - 현재 원격 workflow URL이나 성공 실행 ID가 없어 실행 이력을 연결할 수 없다.

- Validation:
  - Python/PyYAML로 `ci.yml`을 파싱하고 Backend, Frontend, Infrastructure 3개 job, `main` push·pull request trigger와 `contents: read`를 확인했다. 결과: 성공.
  - PowerShell 검사로 이 디렉터리 문서를 포함한 신규 6개 문서의 필수 섹션·수정일과 상대 링크를 확인했다. 결과: 성공.
  - `corepack pnpm --dir frontend exec prettier --check ...` 최초 실행은 포맷 차이로 실패했다. `prettier --write` 적용 후 재검사 결과 모두 통과했다.
  - GitHub-hosted runner 실행은 이 작업에서 수행하지 않았다.

- Next steps:
  - 초기 GitHub push 또는 PR에서 Backend, Frontend, Docker Compose job 결과를 확인해야 한다.
  - 향후 테스트 영역이 추가되면 현재 모듈 `check`에 포함할지 별도 job으로 분리할지 실행 시간과 실패 격리를 기준으로 결정해야 한다.
  - branch protection을 도입할 경우 현재 job 이름과 필수 check 설정을 함께 관리해야 한다.
