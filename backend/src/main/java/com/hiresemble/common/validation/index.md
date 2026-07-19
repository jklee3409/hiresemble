# 공통 Validation 안내

## 디렉터리 목적

Java 문자열 길이와 다른 UTF-8 byte 기반 credential 상한을 Bean Validation으로 제공한다.

## 주요 파일 및 하위 디렉터리

- [`Utf8ByteLength.java`](Utf8ByteLength.java): record component용 validation annotation
- [`Utf8ByteLengthValidator.java`](Utf8ByteLengthValidator.java): UTF-8 byte 수 경계 검사
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- signup과 login password가 BCrypt 72-byte 계약을 넘지 않도록 Controller 진입에서 검증한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`common/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 비밀번호 값이나 byte 변환 결과를 오류 응답·로그에 포함하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
