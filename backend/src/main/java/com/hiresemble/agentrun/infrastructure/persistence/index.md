# Agent Run Persistence package 안내

## 디렉터리 목적

com.hiresemble.agentrun.infrastructure.persistence package는 JDBC/JPA 기반 저장·조회 구현과 mapping을 소유한다.

## 주요 파일 및 하위 디렉터리

| 파일                                                       | 역할                        |
| ---------------------------------------------------------- | --------------------------- |
| [AgentRunJdbcMapper.java](AgentRunJdbcMapper.java)         | Persistence 책임 구현       |
| [AiPreferenceStore.java](AiPreferenceStore.java)           | Persistence 책임 구현       |
| [JdbcAgentRunRepository.java](JdbcAgentRunRepository.java) | Persistence 책임 구현       |
| [JdbcAgentRunStateStore.java](JdbcAgentRunStateStore.java) | Persistence 책임 구현       |
| [JdbcAgentStepStore.java](JdbcAgentStepStore.java)         | Persistence 책임 구현       |
| [JdbcBudgetStore.java](JdbcBudgetStore.java)               | Persistence 책임 구현       |
| [JdbcUsageRecorder.java](JdbcUsageRecorder.java)           | Persistence 책임 구현       |
| [progress.md](progress.md)                                 | 이 package의 이동·검증 이력 |

## 구성 요소 역할

- JDBC/JPA 기반 저장·조회 구현과 mapping을 소유한다.
- `JdbcAgentRunRepository`는 Document·Job·Cover Letter·Answer Version typed resource의 owner-scoped 목록·상세·SSE 조회와 retry seed를 해석하고, terminal history를 audit·lineage 보존 soft delete한다.
- `JdbcUsageRecorder`는 provider call/price item identity별 usage를 저장하고 실제 비용을 Run에 정확히 한 번 누적한다.
- `JdbcBudgetStore`는 모든 사용자와 AI 기능이 공유하는 일일 전역 ledger를 잠그고 호출별 reserve·settle·release를 원자 적용한다.

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
