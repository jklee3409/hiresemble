# Agent Run domain 테스트 안내

## 디렉터리 목적

Run·Step 상태 machine의 허용·금지 전이와 안전한 projection을 exhaustive하게 검증한다.

## 주요 파일 및 하위 디렉터리

- `AgentRunStateMachineTest`
- `AgentStepStateMachineTest`
- [`progress.md`](progress.md)

## 구성 요소 역할

terminal 불변, attempt, stateVersion, retryable/cancellable과 safe error를 단위 테스트로 고정한다.

## 다른 디렉터리와의 의존 관계

운영 domain은 [`../../../../../../main/java/com/hiresemble/agentrun/domain/`](../../../../../../main/java/com/hiresemble/agentrun/domain/index.md)에 있다.

## 변경 시 주의사항

enum이나 전이를 바꿀 때 허용·금지 matrix를 함께 갱신한다.

## 관련 규칙 및 문서

- [상위 테스트](../index.md)
- [구현 계획](../../../../../../../../docs/design/implementation-plan.md)
