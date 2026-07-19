# 백엔드 Java 테스트 안내

## 디렉터리 목적

Gradle test Java package namespace의 상위 경계를 관리한다.

## 주요 파일 및 하위 디렉터리

- [`com/`](com/index.md): 프로젝트 test package namespace
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- JUnit source를 production source와 같은 package hierarchy로 정리한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`test/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 생성된 test report와 container 데이터는 이 source tree에 저장하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../AGENTS.md)
- [공통 작업 절차](../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
