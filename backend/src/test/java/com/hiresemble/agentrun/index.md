# Agent Run 테스트 안내

## 디렉터리 목적

P3 Agent Run domain, PostgreSQL runtime, API·SSE, budget, retry·cancel과 복구 계약을 검증한다.

## 주요 파일 및 하위 디렉터리

- [`domain/`](domain/index.md): 전체 상태 전이 단위 테스트
- `AgentRun*IntegrationTest`: claim·budget·retry·cancel·SSE PostgreSQL 테스트
- `AgentRunIntegrationSupport`: 격리 fixture
- [`progress.md`](progress.md): 검증 이력

## 구성 요소 역할

Testcontainers PostgreSQL에서 production JDBC와 migration을 사용하고 외부 provider를 호출하지 않는다.

## 다른 디렉터리와의 의존 관계

운영 구현은 [`../../../../../main/java/com/hiresemble/agentrun/`](../../../../../main/java/com/hiresemble/agentrun/index.md)에 있다.

## 변경 시 주의사항

운영 DB나 기존 개발 DB를 사용하지 않고 동시성·시간 테스트는 격리된 fixture와 주입 Clock을 사용한다.

## 관련 규칙 및 문서

- [Backend 테스트 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [상위 테스트 안내](../index.md)
