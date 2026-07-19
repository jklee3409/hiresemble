# 백엔드 공통 기반 안내

## 디렉터리 목적

P1·P2 여러 HTTP 기능이 공유하는 오류, OpenAPI, 보안, validation과 idempotency 기반을 관리한다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): 공통 오류 DTO·factory와 Controller OpenAPI 설정
- [`exception/`](exception/index.md): 오류 code·예외·전역 변환
- [`security/`](security/index.md): Request ID, Security 오류와 Session/CSRF 설정
- [`validation/`](validation/index.md): UTF-8 byte validation
- [`idempotency/`](idempotency/index.md): durable HMAC reservation·replay 기반
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 도메인별 정책을 흡수하지 않고 두 곳 이상에서 재사용되는 공통 책임만 제공한다.
- 공개 Controller의 공통 OpenAPI 정보와 Session·CSRF 보안 scheme를 한 곳에서 정의한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`hiresemble/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- BaseResponseDto와 미래 도메인 ErrorCode·registry·annotation을 선행 생성하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
