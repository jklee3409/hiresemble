# GitHub App local UI UAT runbook

- actual_external_uat: `USER_MANUAL_UI_VALIDATION_PENDING`

## 목적과 안전 경계

이 문서는 local Hiresemble에서 사용자가 직접 GitHub App을 생성하고 private repository 경험을 Resume·Portfolio까지 연결한 뒤 권한과 계정을 정리하는 절차다. Webhook은 Phase 5 범위가 아니므로 **비활성**으로 둔다. PAT 입력은 지원하지 않으며 App private key, client secret, OAuth code, state, installation token, OpenAI key를 문서·issue·screenshot·채팅·로그에 붙이지 않는다.

자동화 검증은 Fake/WireMock/Testcontainers/MinIO/Playwright만 사용한다. 아래 실제 GitHub/OpenAI UAT는 사용자 소유 credential과 비용 한도를 확인한 뒤 사용자가 수행한다.

## GitHub App 등록값

| 항목 | local 값 또는 선택 |
| --- | --- |
| GitHub App name | 사용자 계정에서 유일한 임시 이름 |
| Homepage URL | `http://localhost:5173` |
| Setup URL | `http://localhost:8080/api/v1/github-app-connections/setup/callback` |
| Callback URL | `http://localhost:8080/api/v1/github-app-connections/oauth/callback` |
| Webhook | Active 해제, URL·secret 미입력 |
| Repository permissions | Metadata `Read-only`, Contents `Read-only`; 나머지는 `No access` |
| Organization/account permissions | 모두 `No access` |
| 설치 대상 | personal 또는 테스트 organization, `Only select repositories` 권장 |

GitHub가 Metadata read를 기본 권한으로 표시하더라도 Contents 외 권한을 추가하지 않는다. `All repositories` 설치도 backend token이 Hiresemble에서 고른 repository ID로 다시 축소하지만, 수동 UAT에는 `Only select repositories`가 더 명확하다.

## local 환경 변수

실제 값은 추적되지 않는 local shell 또는 secret manager에만 둔다. 저장소의 `.env.example`에는 아래 이름과 빈 placeholder만 유지한다.

```text
GITHUB_INGESTION_ENABLED=true
GITHUB_PRIVATE_ENABLED=true
GITHUB_APP_ID=<numeric app id>
GITHUB_APP_SLUG=<app slug>
GITHUB_APP_CLIENT_ID=<oauth client id>
GITHUB_APP_CLIENT_SECRET=<secret supplied outside Git>
GITHUB_APP_PRIVATE_KEY=<PEM supplied outside Git; escaped newline supported>
GITHUB_APP_STATE_SECRET=<at least 32 random characters>
GITHUB_APP_BACKEND_BASE_URL=http://localhost:8080
GITHUB_APP_FRONTEND_BASE_URL=http://localhost:5173
CAREER_ARTIFACT_ENABLED=true
VITE_GITHUB_SOURCE_ENABLED=true
VITE_GITHUB_PRIVATE_ENABLED=true
VITE_CAREER_ARTIFACT_ENABLED=true
```

Resume·Portfolio를 실제 모델로 생성하려면 [`ai-provider-activation.md`](ai-provider-activation.md)의 별도 OpenAI 활성화·비용 제한 절차를 따른다. Provider credential이 없으면 GitHub 연결과 경험 승인까지만 확인하고 생성 단계는 offline 자동화 결과로 대체한다.

## 24단계 UI 검증

모든 단계에서 실패 로그는 request ID, safe error code, 상태, timestamp만 확인한다. private repository 이름·URL, source 원문, credential, callback query를 로그나 issue에 복사하지 않는다.

1. **GitHub App 생성** — 진입: GitHub `Settings → Developer settings → GitHub Apps → New GitHub App`; 클릭: `New GitHub App`; 기대: 임시 App 편집 화면; 실패 확인: GitHub 계정의 App 생성 권한만 확인한다.
2. **Homepage URL 설정** — 진입: App 등록 화면; 입력 label: `Homepage URL`; 기대: `http://localhost:5173` 저장; 실패 확인: scheme·port 오타를 확인하고 secret은 기록하지 않는다.
3. **Setup URL 설정** — 진입: 같은 화면; 입력 label: `Setup URL`; 기대: 위 backend setup callback 고정 URL; 실패 확인: `/api/v1/github-app-connections/setup/callback` 철자를 확인한다.
4. **OAuth callback 설정** — 진입: 같은 화면; 입력 label: `Callback URL`; 기대: 위 OAuth callback 고정 URL; 실패 확인: backend port `8080`과 callback path만 확인한다.
5. **Metadata read 설정** — 진입: `Permissions & events → Repository permissions`; label: `Metadata`; 기대: `Read-only`; 실패 확인: capability의 required permission은 `metadata:read`이며 write 권한은 허용하지 않는다.
6. **Contents read 설정** — 같은 화면; label: `Contents`; 기대: `Read-only`; 실패 확인: `contents:read` 외 repository permission이 `No access`인지 확인한다.
7. **Repository selection 준비** — 진입: App 설치 대상 설정; 클릭: `Only select repositories`; 기대: UAT private repository 한 개만 선택; 실패 확인: organization owner의 설치 권한을 확인한다.
8. **Webhook 비활성 확인** — 진입: App `General`; label: `Active` under Webhook; 기대: 체크 해제, webhook URL·secret 없음; 실패 확인: Phase 5에는 webhook endpoint가 없음을 확인한다.
9. **환경 변수 주입** — 진입: local shell/IDE run configuration; label: 위 `GITHUB_APP_*`; 기대: 추적되지 않는 process environment에만 존재; 실패 확인: `git status`와 safe capability만 보고 실제 값을 출력하지 않는다.
10. **Backend private flag 활성화** — 진입: backend run configuration; label: `GITHUB_INGESTION_ENABLED`, `GITHUB_PRIVATE_ENABLED`; 기대: 둘 다 `true`, credential 누락 시 startup fail-fast; 실패 확인: generic typed configuration error만 확인한다.
11. **Frontend private flag 활성화** — 진입: frontend run environment; label: `VITE_GITHUB_SOURCE_ENABLED`, `VITE_GITHUB_PRIVATE_ENABLED`, `VITE_CAREER_ARTIFACT_ENABLED`; 기대: 모두 `true`; 실패 확인: Vite 재시작 후 build-time flag를 확인한다.
12. **PostgreSQL·MinIO 기동** — 진입: 저장소 root terminal; 명령: `docker compose up -d postgres minio minio-init`; 기대: `docker compose ps`에서 healthy/completed; 실패 확인: `docker compose logs postgres minio minio-init`의 상태만 보고 credential을 issue에 붙이지 않는다.
13. **Backend·Frontend 실행** — 진입: `backend/`, `frontend/`; 명령: `./gradlew bootRun`(Windows `./gradlew.bat bootRun`), `corepack pnpm dev`; 기대: `http://localhost:8080/actuator/health`, `http://localhost:5173`; 실패 확인: health와 startup validation을 확인한다.
14. **GitHub App 연결** — 진입 URL: `http://localhost:5173/profile/github`; 클릭: `GitHub App 연결`, GitHub의 `Install`/`Authorize`; 기대: canonical `/integrations`, `ACTIVE`; 실패 확인: `/api/v1/github-app-connections/capability`의 enabled/configured와 safe callback result만 확인한다.
15. **Private repository 선택** — 진입: `/integrations`; label: `ACTIVE GitHub App 연결`, `GitHub 계정 또는 private 저장소 URL`, `Private 저장소 불러오기`; 기대: `Private` badge와 1~10개 선택 UI; 실패 확인: GitHub installation의 repository selection과 `권한 다시 확인` 결과를 확인한다.
16. **GitHub 경험 추출** — 같은 화면; 클릭: repository checkbox, `선택 저장하고 분석 시작`; 기대: Agent Run `QUEUED/RUNNING → 완료`, source `READY` 또는 안전한 `PARTIAL`; 실패 확인: Agent Run safe error와 retry 가능 여부만 확인한다.
17. **Canonical 경험 승인** — 진입 URL: `http://localhost:5173/profile/experiences`; 클릭: 해당 경험 `활용 승인`; 기대: 중복 없이 `활용 승인`, GitHub provenance에는 private 원문 URL이 과다 노출되지 않음; 실패 확인: 경험의 match/review 상태만 확인한다.
18. **Resume 생성·DOCX 다운로드** — 진입 URL: `/career-artifacts/new?type=RESUME`; 클릭: 경험, exact model, `파일 생성 요청`, `Word(.docx) 다운로드`; 기대: 선택 model의 immutable version과 DOCX; 실패 확인: Agent Run·render safe error와 object-storage health를 확인한다.
19. **Portfolio 생성·preview·PPTX 다운로드** — 진입 URL: `/career-artifacts/new?type=PORTFOLIO`; 클릭: 경험, exact model, `파일 생성 요청`, slide preview, `PowerPoint(.pptx) 다운로드`; 기대: 6~12장 preview와 PPTX; 실패 확인: portfolio renderer 제한과 safe error만 확인한다.
20. **연결 refresh** — 진입 URL: `/integrations`; 클릭: `권한 다시 확인`; 기대: last checked 갱신, repository selection/permission 재검증; 실패 확인: `SUSPENDED`/`REVOKED`/permission safe code를 확인한다.
21. **연결 해제** — 같은 화면; 클릭: `연결 해제`, `연결 해제 및 uninstall`; 기대: 즉시 token mint 중단, `DISCONNECTING`, remote uninstall과 private snapshot 삭제 접수, 승인 경험·기존 artifact version 보존; 실패 확인: revocation/snapshot outbox 상태와 safe error code만 확인한다.
22. **회원 탈퇴** — 진입 URL: `http://localhost:5173/settings/account`; 클릭: `회원 탈퇴`, 현재 비밀번호, 두 번째 확인, `계정 영구 삭제`; 기대: `202`, 모든 session/client state 종료, 로그인 화면 접수 안내, 24시간 purge 목표; 실패 확인: deletion request의 safe status·purgeBy만 확인하고 request ID를 공개 영수증처럼 공유하지 않는다.
23. **GitHub uninstall 상태 확인** — 진입: GitHub `Settings → Applications → Installed GitHub Apps`; 기대: 대상 installation 없음; 실패 확인: local connection이 `DISCONNECTING`이면 durable revocation retry 상태를 운영 DB/health에서 안전하게 확인하며 임의 재연결하지 않는다.
24. **테스트 정리** — 진입: GitHub App settings와 local shell; 클릭: 남은 임시 App `Delete GitHub App`; 조치: local process 종료, shell credential unset, 필요 시 `docker compose down`; 기대: 실제 secret·callback query·download ticket 잔존 없음; 실패 확인: `git status --short`와 secret scan을 실행하고 실제 값을 출력하지 않는다.

## 안전한 장애 확인

- Capability가 unavailable이면 public GitHub 흐름은 유지하고 backend의 `GITHUB_PRIVATE_ENABLED`와 필수 변수 **존재 여부만** 확인한다.
- Callback 결과가 expired/cancelled/permission이면 `/integrations`에서 다시 시작한다. callback URL의 `state`나 `code`를 복사하지 않는다.
- `SUSPENDED`, `REVOKED`, `DISCONNECTING`에서는 새 token이 발급되지 않는다. 이미 성공한 snapshot과 승인 canonical experience, immutable Career Artifact version을 수동 삭제하지 않는다.
- Disconnect/account purge가 `DEAD`이면 user physical purge가 정지된 것이 정상이다. 원문 upstream body 대신 safe error code와 attempt/lease metadata로 운영 추적한다.
- 실제 GitHub 상태 변경은 webhook이 아니라 연결 refresh 또는 다음 token 발급 실패에서 감지한다. Webhook 추가는 별도 승인 작업이다.
