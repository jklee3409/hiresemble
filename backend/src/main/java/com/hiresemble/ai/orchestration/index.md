# AI orchestration package 안내

## 디렉터리 목적

registry가 정한 순서를 application port로 실행하는 bounded AgentOrchestrator를 구현한다.

## 주요 파일 및 하위 디렉터리

- `AgentOrchestrator`: context→cancel→hash→reuse→budget→route→gateway→validate→usage→checkpoint→apply
- [`progress.md`](progress.md): orchestration 상태

## 구성 요소 역할

최대 3 attempt와 고정 step sequence를 실행하며 repository를 직접 사용하지 않는다.

## 다른 디렉터리와의 의존 관계

AI 하위 contract와 [`../../agentrun/application/`](../../agentrun/application/index.md)의 port를 조정한다.

## 변경 시 주의사항

자유 loop를 만들지 않고 외부 호출 전후·apply 직전에 cancel을 확인한다. gateway 호출은 `AgentRunLeaseHeartbeatPort`로 감싸 호출 중에도 DB lease를 유지한다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [구현 계획](../../../../../../../../docs/design/implementation-plan.md)
