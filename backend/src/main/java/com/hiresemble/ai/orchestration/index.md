# AI orchestration package 안내

## 디렉터리 목적

registry가 정한 순서를 application port로 실행하는 bounded AgentOrchestrator를 구현한다.

## 주요 파일 및 하위 디렉터리

- `AgentOrchestrator`: context→cancel→hash→reuse→budget→route→gateway→validate→usage→atomic checkpoint+apply
- `StepCompletionTransaction`: 성공·재사용 checkpoint와 domain apply를 묶는 내부 transaction 경계
- [`progress.md`](progress.md): orchestration 상태

## 구성 요소 역할

고정 step sequence와 failure별 attempt cap을 실행하며 repository를 직접 사용하지 않는다. 공개·영속 `attempt/maxAttempts=3` hard cap 안에서 repairable semantic correction과 transport retry 상태를 별도로 계산하고, 마지막 non-null correction guidance는 중간 network·timeout 뒤에도 유지한다. model tier route는 전체 실제 호출 순서를 따라 승격하며 transport 오류로 초기화하지 않는다. executor별 deterministic reuse branch는 registry의 call cap을 유지하면서 provider routing만 생략할 수 있다. provider 호출은 transaction 밖에서 수행하고 성공·실패 응답의 가격 item별 usage를 validation 전에 정확히 한 번 기록하며 성공·재사용 완료 경계는 `SERIALIZABLE`로 commit한다. 검증된 Provider DTO가 서버 소유 내부 DTO로 바뀌어야 하는 workflow는 context-aware minimal·ephemeral mapping hook을 사용하고 기존 executor는 context-free 기본 동작을 유지한다. 실제 failed scope가 남은 terminal 결과는 contribution의 `TerminalPartialPolicy`로 판정하며 공용 경계는 업무별 safe code를 소유하지 않는다.

## 다른 디렉터리와의 의존 관계

AI 하위 contract와 [`../../agentrun/application/`](../../agentrun/application/index.md)의 port를 조정한다.

## 변경 시 주의사항

자유 loop를 만들지 않고 외부 호출 전후·apply 직전에 cancel을 확인한다. gateway 호출은 `AgentRunLeaseHeartbeatPort`로 감싸 호출 중에도 DB lease를 유지한다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [구현 계획](../../../../../../../../docs/design/implementation-plan.md)
