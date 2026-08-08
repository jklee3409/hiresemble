# GitHub Source Feature

공개 GitHub source의 owner-scoped query key, 등록·저장소 선택·새로고침·삭제 mutation,
idempotency identity와 화면 표시 규칙을 관리한다. Agent Run stream은 공용
`features/agent-runs` 기반을 재사용한다.

- [`queryKeys.ts`](queryKeys.ts): source 목록·상세·repository cache 경계
- [`queries.ts`](queries.ts): Vue Query read/mutation과 관련 cache 갱신
- [`idempotency.ts`](idempotency.ts): pending 사용자 작업별 `Idempotency-Key` 수명주기
- [`presentation.ts`](presentation.ts): URL·외부 링크 검증, 상태·rate limit 사용자 표현
- [`GitHubRunMonitor.vue`](GitHubRunMonitor.vue): focused source 한 건의 Run 진행 표시
- [`progress.md`](progress.md): 구현·검증 기록
