# 인프라·환경 변수·CI 규칙

## 적용 범위

루트 `compose.yaml`, `.env.example`, `.gitignore`, `.github/`, backend 설정과 DB migration 변경에 적용한다. 변경 전 [`workflow.md`](workflow.md)와 영향 디렉터리 문서를 읽는다.

## Docker Compose

- 개발 상태 인프라는 PostgreSQL/pgvector, MinIO, 선택적 Mailpit으로 제한한다.
- Spring Boot와 Vue는 기본 Compose service에 넣지 않고 로컬 프로세스로 실행한다.
- image tag를 `latest`로 두지 않고 검증된 version을 고정한다.
- port는 loopback에 bind하고 환경 변수 override를 제공한다.
- DB/Object Storage는 named volume을 사용하고 healthcheck를 유지한다.
- 초기화 container는 반복 실행해도 안전해야 하며 성공 후 종료되는 상태를 오류로 취급하지 않는다.
- service 추가·변경 후 `docker compose config --quiet`와 dependency/health 흐름을 확인한다.

## 환경 변수와 비밀값

- `.env`와 `.env.*` 실제 값은 Git에서 제외하고 `.env.example`만 추적한다.
- 예시에는 로컬 개발용 비밀이 아닌 값과 설명 가능한 기본값만 둔다.
- 새 변수는 root/backend/frontend consumer, Compose interpolation, README와 관련 index/progress를 함께 확인한다.
- 실제 API key, session secret, 사용자 데이터, production endpoint를 문서나 log에 넣지 않는다.
- 유료 AI/Search provider는 명시적으로 활성화해야 하며 기본 local boot/test는 호출하지 않아야 한다.

## PostgreSQL과 Flyway

- PostgreSQL major와 pgvector version은 `docs/spec/tech_stack.md`와 맞춘다.
- application schema는 Flyway만 변경하고 `ddl-auto=validate`를 유지한다.
- 이미 적용된 migration을 수정·rename·순서 변경하지 않는다.
- 새 migration은 forward-only와 rollback/복구 전략, lock 시간, 기존 데이터 영향을 검토한다.
- schema 변경은 빈 DB 적용과 기존 DB upgrade를 모두 test한다.
- extension과 session table 같은 framework schema도 ownership을 문서화한다.

## Git과 생성물

- `.gitignore`에는 비밀값, IDE, dependency, build/test output, cache/temp, local infra data를 포함한다.
- Wrapper, lock file, safe example config, CI, migration은 추적한다.
- 새 도구를 도입하면 생성 파일 목록을 확인해 필요한 ignore만 추가한다. 넓은 pattern으로 source를 숨기지 않는다.
- destructive cleanup이나 volume 삭제는 사용자의 명시적 의도가 있을 때만 실행한다.

## GitHub Actions와 Dependabot

- CI는 최소 backend check, frontend check, Compose config validation을 유지한다.
- action과 dependency version은 자동 갱신하되 PR에서 changelog/compatibility를 검토한다.
- workflow 권한은 최소 `contents: read`에서 시작하고 필요 권한만 추가한다.
- secret을 log로 출력하지 않고 fork PR에서 secret 부재를 정상적으로 처리한다.
- 로컬에서 대응 명령을 실행할 수 없는 CI 전용 변경은 그 사실과 검증 방법을 기록한다.

## 검증

```powershell
docker compose config --quiet
docker compose ps
git check-ignore .env
```

서비스 구성이 바뀐 경우 health 상태와 backend 실제 연결까지 확인한다. 데이터를 삭제하는 `docker compose down --volumes`는 검증 명령에 포함하지 않는다.

## 관련 문서

- [기술 스택 명세](../spec/tech_stack.md)
- [DB 명세](../spec/db.md)
- [루트 구조 안내](../../index.md)
- [GitHub 설정 안내](../../.github/index.md)
