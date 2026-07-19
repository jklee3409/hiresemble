# AI runtime 영역 안내

## 디렉터리 목적

고정 workflow orchestration, 안전한 context·prompt·structured output 계약과 provider-independent gateway 경계를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`workflow/`](workflow/index.md): canonical workflow definition과 executable contribution 경계
- [`orchestration/`](orchestration/index.md): bounded `AgentOrchestrator`
- [`context/`](context/index.md), [`model/`](model/index.md), [`prompt/`](prompt/index.md)
- [`validation/`](validation/index.md), [`budget/`](budget/index.md), [`execution/`](execution/index.md)
- [`port/`](port/index.md), [`infrastructure/`](infrastructure/index.md): gateway와 disabled adapter
- [`progress.md`](progress.md): P3 구현·검증 상태

## 구성 요소 역할

registry가 고정 step 순서를 결정하고 orchestrator가 Agent Run application port를 통해 checkpoint·usage·apply를 조정한다. 자유 agent loop와 production Fake workflow는 없다.

## 다른 디렉터리와의 의존 관계

- [`../agentrun/application/`](../agentrun/application/index.md)의 port만 소비하며 repository를 직접 참조하지 않는다.
- test-only Fake 3-step은 [`../../../../../test/java/com/hiresemble/ai/`](../../../../../test/java/com/hiresemble/ai/index.md)에만 있다.

## 변경 시 주의사항

사용자 원문, 전체 prompt와 provider response를 저장·로그하지 않는다. 실제 provider adapter와 executable contribution은 해당 domain phase에서 가격·policy·heartbeat 계약과 함께 추가한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [시스템 설계](../../../../../../../docs/design/system-architecture.md)
