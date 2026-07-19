# Agent Run domain package 안내

## 디렉터리 목적

Agent Run·Step 상태, 공개 품질 의도, 내부 model tier와 안전한 오류·부분 결과 값을 정의한다.

## 주요 파일 및 하위 디렉터리

- `AgentRun`, `AgentStep`: 상태 전이와 불변식
- `AgentRunStatus`, `AgentStepStatus`: canonical enum
- `WorkflowType`, `AiQualityMode`, `ModelTier`, `UsageType`: runtime 분류
- 안전한 `SafeError`, `RequiredUserAction`, `PartialResult`, `ResourceReference`
- [`progress.md`](progress.md): domain 검증 상태

## 구성 요소 역할

HTTP나 persistence 세부사항 없이 허용 전이, terminal 불변, retryable/cancellable projection과 attempt 한도를 고정한다.

## 다른 디렉터리와의 의존 관계

상위 [`../application/`](../application/index.md)과 [`../api/`](../api/index.md)가 이 값을 소비한다.

## 변경 시 주의사항

Run attempt와 Step attempt를 혼동하지 않고 terminal 상태를 다시 열지 않는다. safe 값에는 내부 예외·원문·prompt·provider response를 넣지 않는다.

## 관련 규칙 및 문서

- [시스템 설계](../../../../../../../../docs/design/system-architecture.md)
- [상위 영역](../index.md)
