# Agent Run DTO package 안내

## 디렉터리 목적

com.hiresemble.agentrun.api.dto package는 외부 request·response와 event의 타입 안전한 전송 계약을 소유한다.

## 주요 파일 및 하위 디렉터리

| 파일 | 역할 |
| ---- | ---- |
| [AgentRunDetailDto.java](AgentRunDetailDto.java) | DTO 책임 구현 |
| [AgentRunPageDto.java](AgentRunPageDto.java) | DTO 책임 구현 |
| [AgentRunSummaryDto.java](AgentRunSummaryDto.java) | DTO 책임 구현 |
| [AgentStepDto.java](AgentStepDto.java) | DTO 책임 구현 |
| [CancelAgentRunRequest.java](CancelAgentRunRequest.java) | DTO 책임 구현 |
| [HeartbeatEventDto.java](HeartbeatEventDto.java) | DTO 책임 구현 |
| [PartialResultDto.java](PartialResultDto.java) | DTO 책임 구현 |
| [ProgressEventDto.java](ProgressEventDto.java) | DTO 책임 구현 |
| [RequiredUserActionDto.java](RequiredUserActionDto.java) | DTO 책임 구현 |
| [ResourceRefDto.java](ResourceRefDto.java) | DTO 책임 구현 |
| [RunAcceptedDto.java](RunAcceptedDto.java) | DTO 책임 구현 |
| [SafeErrorDto.java](SafeErrorDto.java) | DTO 책임 구현 |
| [SnapshotEventDto.java](SnapshotEventDto.java) | DTO 책임 구현 |
| [StepEventDto.java](StepEventDto.java) | DTO 책임 구현 |
| [TerminalEventDto.java](TerminalEventDto.java) | DTO 책임 구현 |
| [WaitingUserEventDto.java](WaitingUserEventDto.java) | DTO 책임 구현 |
| [progress.md](progress.md) | 이 package의 이동·검증 이력 |

## 구성 요소 역할

- 외부 request·response와 event의 타입 안전한 전송 계약을 소유한다.
- 상위 계층의 책임을 더 구체적인 탐색 단위로 드러내며 새 동작이나 계약을 정의하지 않는다.

## 다른 디렉터리와의 의존 관계

- [상위 package](../index.md)의 계층 경계와 인접 계층의 공개 타입을 사용한다.

## 변경 시 주의사항

- 실제 책임과 파일이 없는 빈 package를 선행 생성하지 않는다.
- package 이동 시 접근 제한자를 넓히지 않고 경로, package 선언, import와 필요한 FQCN을 함께 검증한다.
- API·DB·workflow 동작 변경은 별도 계약 작업으로 분리한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../../docs/agent-rules/backend-development.md)
- [문서 추적 규칙](../../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [상위 package 안내](../index.md)
- [진행 상황](progress.md)
