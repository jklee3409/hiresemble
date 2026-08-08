# AI runtime 영역 안내

## 디렉터리 목적

고정 workflow orchestration, 안전한 context·prompt·structured output 계약과 provider-independent gateway 경계를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`workflow/`](workflow/index.md): canonical workflow definition과 executable contribution 경계
- [`workflow/github/`](workflow/github/index.md): `GITHUB_INGESTION` 10단계와 failure compensation
- [`workflow/careerartifact/`](workflow/careerartifact/index.md): Resume·Portfolio 8단계와 Office/Object failure compensation
- [`orchestration/`](orchestration/index.md): bounded `AgentOrchestrator`
- [`context/`](context/index.md), [`model/`](model/index.md), [`prompt/`](prompt/index.md)
- [`validation/`](validation/index.md), [`budget/`](budget/index.md), [`execution/`](execution/index.md)
- [`port/`](port/index.md), [`infrastructure/`](infrastructure/index.md): gateway와 disabled adapter
- [`progress.md`](progress.md): P3 구현·검증 상태

## 구성 요소 역할

registry가 11개 WorkflowType의 고정 step 순서를 결정하고 orchestrator가 Agent Run application port를 통해 checkpoint·usage·apply를 조정한다. P4 `DOCUMENT_INGESTION` 신규 Run은 후보 embedding을 포함한 v2 9단계를 사용하고 durable v1 8단계 Run을 exact version으로 재개한다. P5~P8, `GITHUB_INGESTION`, Resume·Portfolio workflow도 각 application port에 연결되며 자유 agent loop와 production Fake workflow는 없다.

## 다른 디렉터리와의 의존 관계

- [`../agentrun/application/`](../agentrun/application/index.md), [`../document/application/`](../document/application/index.md), [`../githubsource/application/`](../githubsource/application/index.md), [`../careerartifact/application/`](../careerartifact/application/index.md), [`../job/application/`](../job/application/index.md), [`../coverletter/application/`](../coverletter/application/index.md)과 [`../interview/application/`](../interview/application/index.md)의 port만 소비하며 repository를 직접 참조하지 않는다.
- test-only Fake 3-step은 [`../../../../../test/java/com/hiresemble/ai/`](../../../../../test/java/com/hiresemble/ai/index.md)에만 있다.

## 변경 시 주의사항

전체 HTML·문서, 전체 prompt와 provider response를 저장·로그하지 않는다. P6은 서버 allowlist의 구조화 profile fact와 호환되는 `VERIFIED` evidence만 positive provenance로 사용하고, P7~P8은 `VERIFIED` 비학력 evidence 경계를 유지한다. source·createdBy·score·persist를 모델에 위임하지 않는다. 실제 Chat/Search provider 기본값은 `none`이며 Tavily는 명시적 설정과 key가 있을 때만 활성화한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [시스템 설계](../../../../../../../docs/design/system-architecture.md)
