# Agent Run infrastructure package 안내

## 디렉터리 목적

Agent Run·Step·budget·usage JDBC persistence, bounded executor, claim·lease와 reconciliation을 구현한다.

## 주요 파일 및 하위 디렉터리

- `JdbcAgentRunRepository`, `JdbcAgentRunStateStore`, `JdbcAgentStepStore`
- `JdbcBudgetStore`, `JdbcUsageRecorder`, `AiPreferenceStore`
- `AgentRunDispatcher`, `AgentRunReconciler`, `ScheduledAgentRunLeaseHeartbeat`, `AgentRuntimeConfiguration`
- `AgentRunEventBus`: commit 이후 in-memory SSE fan-out
- [`progress.md`](progress.md): persistence·worker 검증 상태

## 구성 요소 역할

조건부 SQL로 단일 claim과 CAS를 보장하고 DB의 QUEUED/RUNNING row를 재시작·포화 상태에서도 복구한다.

## 다른 디렉터리와의 의존 관계

- [`../application/`](../application/index.md)의 port를 구현한다.
- schema는 [`../../../../../resources/db/migration/`](../../../../../resources/db/migration/index.md)을 따른다.

## 변경 시 주의사항

executor queue를 상태 원천으로 사용하지 않는다. claim은 task가 실제 실행될 때 수행하고 blocking gateway 호출 중에도 별도 scheduler가 lease를 갱신한다. stale Run은 INTERRUPTED로 닫으며 reserve 정산·release를 함께 처리한다.

## 관련 규칙 및 문서

- [Infrastructure 규칙](../../../../../../../../docs/agent-rules/infrastructure.md)
- [상위 영역](../index.md)
