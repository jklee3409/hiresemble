# Career Artifact feature 안내

## 디렉터리 목적

`career-artifacts`는 생성 자료 목록, 생성·재생성 입력, 구조화 미리보기, 버전 다운로드와 Agent Run 연결을 담당한다. 업로드 문서와는 별도 query/cache/lifecycle 경계를 유지한다.

## 주요 파일 및 하위 디렉터리

- `queryKeys.ts`, `queries.ts`: 사용자 범위 서버 상태와 mutation cache 연결
- `filters.ts`: 목록·wizard URL의 비민감 canonical 상태
- `drafts.ts`: 사용자별 `sessionStorage` draft와 pending idempotency key
- `presentation.ts`: 공개 enum을 사용자 문구로 변환
- Vue components: 자료 영역 전환, 제안, 생성 form, Run monitor, structured preview
- `*.test.ts`: flag·filter·draft·query·form·preview·keyboard·Run parity 회귀
- [`progress.md`](progress.md): 구현·검증 기록

## 구성 요소 역할

- strict API 응답만 server state로 받아 사용자별 Vue Query key에 저장한다.
- wizard의 개인정보·선택값·pending idempotency는 최대 24시간 `sessionStorage`에만 보관한다.
- current version projection만 preview하고 과거 version은 summary와 선택 download만 제공한다.

## 다른 디렉터리와의 의존 관계

- strict DTO·transport는 [`../../shared/api/`](../../shared/api/index.md), Run stream은 [`../agent-runs/`](../agent-runs/index.md)을 사용한다.
- route page는 [`../../pages/`](../../pages/index.md), flag와 router gate는 [`../../app/`](../../app/index.md)과 [`../../router/`](../../router/index.md)에 있다.

## 변경 시 주의사항

- exact model과 template을 임의로 추가하지 않고 raw download URL·renderer snapshot·storage metadata를 전역 상태나 로그에 남기지 않는다.
- 연결 오류와 Run business failure를 구분하고 새 Run 중에도 이전 성공 preview와 download를 유지한다.

## 관련 규칙 및 문서

- [Frontend 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [페이지 명세](../../../../docs/spec/page.md)
- [API 명세](../../../../docs/spec/api.md)
- [영역 진행 상황](progress.md)
