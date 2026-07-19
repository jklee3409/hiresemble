# 공통 기반 테스트 안내

## 디렉터리 목적

P1 ErrorCode, UTF-8 validation과 durable idempotency 테스트를 책임별로 구성한다.

## 주요 파일 및 하위 디렉터리

- [`exception/`](exception/index.md): ErrorCode 계약 테스트
- [`validation/`](validation/index.md): 비밀번호 byte 경계 테스트
- [`idempotency/`](idempotency/index.md): DB reservation·replay 통합 테스트
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 공통 기반의 순수 규칙과 DB 동시성 경계를 분리해 검증한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`hiresemble/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- test 편의를 위해 production registry·endpoint를 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
