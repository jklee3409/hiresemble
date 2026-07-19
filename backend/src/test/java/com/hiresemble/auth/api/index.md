# 인증 API 테스트 안내

## 디렉터리 목적

P1 다섯 인증 endpoint 회귀와 P1·P2 OpenAPI의 실제 Spring 통합 계약을 검증한다.

## 주요 파일 및 하위 디렉터리

- [`AuthIntegrationTest.java`](AuthIntegrationTest.java): 가입·로그인·CSRF·Session·오류·Request ID 통합
- [`OpenApiContractTest.java`](OpenApiContractTest.java): 정확히 30개 operation, DTO·enum·nullability, 금지 경로 부재와 Swagger UI 접근
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- MockMvc와 PostgreSQL을 함께 사용해 Cookie·Session DB·응답 JSON 및 실제 생성 OpenAPI와 Swagger UI 진입 경로를 검증한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`auth/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 민감 fixture 값을 응답 assertion이나 production log에 남기지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
