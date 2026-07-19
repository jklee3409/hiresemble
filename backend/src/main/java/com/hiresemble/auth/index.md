# 인증 영역 안내

## 디렉터리 목적

P1의 사용자 가입, Session 인증과 현재 사용자 projection을 도메인 경계별로 구성한다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): 다섯 인증 HTTP endpoint와 공개 DTO
- [`application/`](application/index.md): 가입·로그인·로그아웃과 CSRF use case
- [`domain/`](domain/index.md): P1 사용자 역할·상태 값
- [`infrastructure/`](infrastructure/index.md): 사용자 credential·상태 JPA 영속성
- [`security/`](security/index.md): Session에 저장되는 인증 principal
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- HTTP 입력은 application use case로 전달하고 사용자 entity는 공개 응답에 직접 노출하지 않는다.

## 다른 디렉터리와의 의존 관계

- 상위 [`hiresemble/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 가입 시 기본 프로필 생성은 [`../profile/`](../profile/index.md)의 등록 경계를 호출하고, 프로필 CRUD를 인증 영역에 중복 구현하지 않는다.
- 계정 변경, 탈퇴와 Dashboard API를 이 영역에 선행 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
