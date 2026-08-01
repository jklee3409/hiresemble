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

registry가 고정 step 순서를 결정하고 orchestrator가 Agent Run application port를 통해 checkpoint·usage·apply를 조정한다. 외부 gateway 호출 뒤 성공·재사용 checkpoint와 domain apply는 하나의 짧은 transaction으로 완료된다. P4 `DOCUMENT_INGESTION`, P5 `JOB_POSTING_EXTRACTION`, P6 `JOB_ANALYSIS`, P7 `COVER_LETTER_GENERATION|VERIFICATION`, P8 `INTERVIEW_PREPARATION|INTERVIEW_ANSWER_FEEDBACK`은 각 application port에 연결되며 자유 agent loop와 production Fake workflow는 없다. `JOB_POSTING_EXTRACTION` v2는 DOM inspection, bounded image fetch, 별도 image text gateway, source compose와 apply 전 품질 검증을 고정 step으로 관찰한다.

## 다른 디렉터리와의 의존 관계

- [`../agentrun/application/`](../agentrun/application/index.md), [`../document/application/`](../document/application/index.md), [`../job/application/`](../job/application/index.md), [`../coverletter/application/`](../coverletter/application/index.md)과 [`../interview/application/`](../interview/application/index.md)의 port만 소비하며 repository를 직접 참조하지 않는다.
- test-only Fake 3-step은 [`../../../../../test/java/com/hiresemble/ai/`](../../../../../test/java/com/hiresemble/ai/index.md)에만 있다.

## 변경 시 주의사항

전체 HTML·문서, 전체 prompt와 provider response를 저장·로그하지 않는다. P6~P8은 VERIFIED 비학력 evidence만 positive provenance로 사용하고 source·createdBy·score·persist를 모델에 위임하지 않는다. 실제 Chat/Search provider 기본값은 `none`이며 Tavily는 명시적 설정과 key가 있을 때만 활성화한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [시스템 설계](../../../../../../../docs/design/system-architecture.md)
