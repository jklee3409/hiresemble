# P1·P2 Migration 테스트 안내

## 디렉터리 목적

Flyway V1·V2 보존과 V3 P2 schema의 빈 DB·upgrade 경로를 실제 PostgreSQL에서 검증한다.

## 주요 파일 및 하위 디렉터리

- [`P1MigrationTest.java`](P1MigrationTest.java): V1→V2, V1-only upgrade, constraint·index·V1 hash
- [`P2MigrationTest.java`](P2MigrationTest.java): 빈 DB·V1·V2 upgrade, V1·V2 hash, P2 DB 불변식과 transaction rollback
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- migration 적용 순서와 각 phase schema 범위가 계약을 넘지 않는지 독립 PostgreSQL DB에서 확인한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`hiresemble/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 적용 이력 V1·V2를 test 편의를 위해 수정하거나 H2로 대체하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
