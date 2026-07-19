# Idempotency 통합 테스트 안내

## 디렉터리 목적

PostgreSQL에 저장되는 P1 idempotency reservation·hash·replay 계약을 검증한다.

## 주요 파일 및 하위 디렉터리

- [`IdempotencyIntegrationTest.java`](IdempotencyIntegrationTest.java): 동시성·replay·hash mismatch·재시작·민감 비저장
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 동일 scope/key 경쟁과 process 경계를 실제 DB row로 확인한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`common/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- canonical request의 password fixture가 DB·로그·결과에 남지 않는지 함께 검사한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
