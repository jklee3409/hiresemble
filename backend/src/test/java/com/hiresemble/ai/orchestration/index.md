# AI orchestration 테스트 안내

## 디렉터리 목적

Testcontainers PostgreSQL에서 test-only 3-step workflow의 전체 execution 경계를 검증한다.

## 주요 파일 및 하위 디렉터리

- `AgentOrchestratorIntegrationTest`
- [`progress.md`](progress.md)

## 구성 요소 역할

내부 WorkflowLauncher, 실제 checkpoint·usage·budget persistence와 Fake gateway/apply를 결합한다.

## 다른 디렉터리와의 의존 관계

[`../../../../../../main/java/com/hiresemble/ai/orchestration/`](../../../../../../main/java/com/hiresemble/ai/orchestration/index.md)을 검증한다.

## 변경 시 주의사항

fixture resource는 production table·endpoint 없이 test scope에만 둔다.

## 관련 규칙 및 문서

- [상위 AI 테스트](../index.md)
- [Fixture prompt](../../../../../resources/prompts/index.md)
