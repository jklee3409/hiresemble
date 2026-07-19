# 인증 테스트 영역 안내

## 디렉터리 목적

P1 인증 HTTP와 생성 OpenAPI 계약 테스트의 상위 경계를 관리한다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): MockMvc 인증 흐름과 OpenAPI path·schema 테스트
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Session·CSRF와 공개 계약을 실제 Spring context에서 검증한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`hiresemble/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- P1 밖 계정·프로필·Dashboard endpoint fixture를 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
