# Jobs Frontend feature 안내

## 디렉터리 목적

P5 Job URL filter·mutation과 P6 Analysis query·presentation·terminal invalidation, Agent Run monitor와 409 version conflict 재적용을 소유한다.

## 주요 파일 및 하위 디렉터리

- `filters.ts`: URL query parse·canonicalization과 sort allowlist
- `queries.ts`: user-scoped Job/Analysis list·latest·command query와 cleanup
- `validation.ts`: 생성·편집 form Zod validation
- `conflict.ts`, `JobVersionConflictPanel.vue`: 409 비교·재적용
- `JobRunMonitor.vue`: 기존 Agent Run stream 재사용
- `presentation.ts`: 업무·추출 상태 label
- `analysisPresentation.ts`: Eligibility·criterion·match·OUTDATED·analysis quality label
- `*.test.ts`, `testFixtures.ts`: feature 계약 검증
- [`progress.md`](progress.md): P5 feature 상태

## 구성 요소 역할

URL query를 목록 filter의 원천으로 사용하고 terminal·WAITING_USER Run event에서 Job, latest analysis·history와 Agent Run query를 invalidate한다.

## 다른 디렉터리와의 의존 관계

- API·DTO는 [`../../shared/api/`](../../shared/api/index.md)에 있다.
- Page는 [`../../pages/`](../../pages/index.md), route는 [`../../router/`](../../router/index.md)에 있다.
- stream은 [`../agent-runs/`](../agent-runs/index.md)을 재사용한다.

## 변경 시 주의사항

업무 상태·추출 상태·Eligibility·OUTDATED를 합치지 않고 P7/P8 action·DTO를 선행 추가하지 않는다.

## 관련 규칙 및 문서

- [상위 feature 안내](../index.md)
- [페이지 명세](../../../../docs/spec/page.md)
- [진행 상황](progress.md)
