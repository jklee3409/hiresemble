# 인증 API 안내

## 디렉터리 목적

P1 인증 Controller와 request·response DTO의 공개 HTTP 계약을 소유한다.

## 주요 파일 및 하위 디렉터리

- [controller/](controller/index.md): HTTP endpoint
- [dto/](dto/index.md): 공개 request·response DTO
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Controller는 HTTP status와 DTO 변환만 담당하고 인증·transaction 규칙은 application 계층에 위임한다.
- Swagger operation metadata는 실제 다섯 endpoint의 status·schema·Session/CSRF requirement만 설명하고 request DTO는 validation을 통과하는 가짜 example만 제공한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`auth/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 성공 envelope나 미래 endpoint stub을 만들지 않고 Entity와 비밀번호를 응답에 노출하지 않는다.
- Swagger UI에서 signup/login 성공 후에는 회전된 Session과 응답의 새 CSRF token을 사용하도록 설명과 OpenAPI contract test를 함께 유지한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
