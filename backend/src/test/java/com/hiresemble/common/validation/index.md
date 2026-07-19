# Validation 테스트 안내

## 디렉터리 목적

Signup·login password의 UTF-8 byte 길이 Bean Validation 경계를 검증한다.

## 주요 파일 및 하위 디렉터리

- [`Utf8ByteLengthValidatorTest.java`](Utf8ByteLengthValidatorTest.java): 다중 byte 문자열의 최소·최대 경계
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Unicode 문자 수와 UTF-8 byte 수 차이에서 생기는 BCrypt truncation 회귀를 방지한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`common/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 평문 password를 실패 메시지나 snapshot에 기록하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
