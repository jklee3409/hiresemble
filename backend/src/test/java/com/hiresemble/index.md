# Hiresemble 백엔드 테스트 안내

## 디렉터리 목적

P1 인증·공통 기반, P2 프로필, P3 Agent Run·AI runtime, P4 Document와 실제 cross-stack E2E를 기능별로 구성한다.

## 주요 파일 및 하위 디렉터리

- [`auth/`](auth/index.md): 인증 HTTP·OpenAPI 통합 테스트
- [`common/`](common/index.md): 오류·validation·idempotency 테스트
- [`migration/`](migration/index.md): Flyway 빈 DB·upgrade 테스트
- [`profile/`](profile/index.md): 프로필 도메인·HTTP·owner·동기화 통합 테스트
- [`agentrun/`](agentrun/index.md): 상태·claim·budget·retry·cancel·SSE 테스트
- [`ai/`](ai/index.md): registry·router·validator·Fake workflow 테스트
- [`document/`](document/index.md): 문서 API·parser·storage·embedding·outbox 테스트
- [`e2e/`](e2e/index.md): PostgreSQL·MinIO·Spring·Vue·Chromium 실제 pipeline
- [`support/`](support/index.md): 공유 PostgreSQL 통합 test 기반
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 요구사항별 테스트를 운영 package 경계와 대응시켜 P1·P2 회귀와 AC-13 공통 기반을 추적한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`com/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 실제 유료 AI·검색 API와 운영 datasource를 호출하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
