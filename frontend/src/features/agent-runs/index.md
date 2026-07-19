# Agent Run Frontend feature 안내

## 디렉터리 목적

Agent Run 목록 filter, Vue Query, 안전한 상태 표현, Progress Drawer와 Document resource SSE 복구 상태 machine을 소유한다.

## 주요 파일 및 하위 디렉터리

- `filters.ts`: repeatable URL query parse·canonicalization
- `queries.ts`: user-scoped list/detail/mutation query
- `stream.ts`: snapshot-first SSE와 reconnect·polling
- `AgentRunDetailPanel.vue`: 공개 detail projection
- `AgentRunProgressDrawer.vue`: 최근 조회된 active Run
- `*.test.ts`, `testFixtures.ts`: contract·UI·stream 검증
- [`progress.md`](progress.md): P3 구현 상태

## 구성 요소 역할

Backend DTO와 stateVersion을 그대로 소비하며 연결 상태와 Run business 상태를 분리한다. P4 Document stream은 terminal·WAITING_USER에서 관련 REST query만 invalidate한다.

## 다른 디렉터리와의 의존 관계

- API·DTO는 [`../../shared/api/`](../../shared/api/index.md)에 있다.
- lazy page는 [`../../pages/`](../../pages/index.md), route는 [`../../router/`](../../router/index.md)에 있다.
- logout·401·사용자 전환 cleanup은 [`../../shared/session/`](../../shared/session/index.md)을 사용한다.

## 변경 시 주의사항

provider/model 실명, prompt, hash, raw JSON, claim·lease를 표시하지 않는다. SSE 단절만으로 Run을 FAILED로 바꾸지 않는다.

## 관련 규칙 및 문서

- [Frontend 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [페이지 명세](../../../../docs/spec/page.md)
- [상위 feature 안내](../index.md)
