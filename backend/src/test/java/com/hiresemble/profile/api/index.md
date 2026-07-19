# 프로필 API 통합 테스트 안내

## 디렉터리 목적

P2 프로필 25개 operation의 HTTP·Session·CSRF·owner·version·evidence 동기화를 검증한다.

## 주요 파일 및 하위 디렉터리

- [`ProfileIntegrationTest.java`](ProfileIntegrationTest.java): 정상 DTO·status, validation, pagination·sort, owner 404, 409와 direct evidence 흐름
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- MockMvc와 PostgreSQL을 함께 사용해 application transaction과 공개 오류 계약을 검증한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`profile/`](../index.md)의 테스트 책임을 구체화한다.
- API 계약은 [`../../../../../../../../docs/spec/api.md`](../../../../../../../../docs/spec/api.md)를 따른다.

## 변경 시 주의사항

- SQL message·rejected value가 오류 응답에 포함되지 않는지 유지한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [영역 진행 상황](progress.md)
