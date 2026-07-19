# 프로필 애플리케이션 안내

## 디렉터리 목적

인증 사용자 기준 P2 프로필 use case, transaction, owner·version·document 경계를 조정한다.

## 주요 파일 및 하위 디렉터리

- [`ProfileApplicationService.java`](ProfileApplicationService.java): 프로필 CRUD·direct evidence use case
- [`ProfileRegistrationService.java`](ProfileRegistrationService.java): signup transaction의 빈 기본 프로필 등록
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- mutation마다 expected version을 확인하고 source와 evidence 변경을 하나의 transaction에서 수행한다.
- 다른 사용자·없는 ID·P2 non-null document ID를 구분하지 않는 404로 변환한다.

## 다른 디렉터리와의 의존 관계

- HTTP 입력은 [`../api/`](../api/index.md), 규칙은 [`../domain/`](../domain/index.md), SQL은 [`../infrastructure/`](../infrastructure/index.md)에 의존한다.
- signup은 [`../../auth/application/AuthService.java`](../../auth/application/AuthService.java)에서 등록 경계를 호출한다.

## 변경 시 주의사항

- mutation을 version 충돌 뒤 자동 재시도하지 않는다.
- profile 미완료를 authorization이나 route 차단 조건으로 사용하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
- [영역 진행 상황](progress.md)
