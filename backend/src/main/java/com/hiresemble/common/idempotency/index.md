# Idempotency 기반 안내

## 디렉터리 목적

향후 비용·생성 mutation이 사용할 DB 기반 HMAC reservation과 원 응답 replay의 최소 기반을 제공한다.

## 주요 파일 및 하위 디렉터리

- [`IdempotencyService.java`](IdempotencyService.java): reservation·hash 비교·replay 조정
- [`IdempotencyRepository.java`](IdempotencyRepository.java): reservation·만료 reclaim·완료의 원자적 JDBC 접근
- [`IdempotencyRequestHasher.java`](IdempotencyRequestHasher.java): versioned HMAC-SHA-256
- [`IdempotencyProperties.java`](IdempotencyProperties.java): key version·secret map·24시간 TTL
- [`IdempotencyScope.java`](IdempotencyScope.java): 사용자·method·route·resource·key scope
- [`IdempotentResponse.java`](IdempotentResponse.java): status·DTO·replay 결과
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 검증·인증·소유권 뒤 호출되는 application service로서 raw request를 저장하지 않고 완료 응답만 재생한다.
- 만료된 COMPLETED와 run 미연결 IN_PROGRESS만 조건부 reclaim하고 linked IN_PROGRESS는 후속 reconciliation까지 보존한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`common/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- P1 auth endpoint에 적용하거나 production test endpoint·과도한 annotation 계층을 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
