# 백엔드 테스트 지원 안내

## 디렉터리 목적

P1 Spring Boot 통합 테스트가 공유하는 PostgreSQL Testcontainer와 table cleanup을 제공한다.

## 주요 파일 및 하위 디렉터리

- [`PostgresIntegrationTest.java`](PostgresIntegrationTest.java): 동적 datasource·HMAC test 설정과 P1 table 정리
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 실제 Flyway/JPA/Session context를 재사용하면서 각 test의 데이터 격리를 보장한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`hiresemble/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 운영 자격 증명·DB URL을 사용하지 않고 test fixture secret만 동적 설정으로 주입한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
