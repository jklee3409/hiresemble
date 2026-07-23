# 프로필 애플리케이션 안내

## 디렉터리 목적

인증 사용자 기준 P2 프로필 use case와 P4 document evidence command/query, transaction, owner·version 경계를 조정한다.

## 주요 파일 및 하위 디렉터리

- [config/](config/index.md): 해당 계층의 실행 설정
- [port/](port/index.md): application port
- [service/](service/index.md): use case·transaction 조정
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- mutation마다 expected version을 확인하고 source와 evidence 변경을 하나의 transaction에서 수행한다.
- 다른 사용자·없는·삭제 Document를 구분하지 않는 404로 변환한다.
- 성공한 document candidate는 PENDING으로 적용하고 `SOURCE_DELETED` evidence는 수정하지 않는다.

## 다른 디렉터리와의 의존 관계

- HTTP 입력은 [`../api/`](../api/index.md), 규칙은 [`../domain/`](../domain/index.md), SQL은 [`../infrastructure/`](../infrastructure/index.md)에 의존한다.
- signup은 [`../../auth/application/service/AuthService.java`](../../auth/application/service/AuthService.java)에서 등록 경계를 호출한다.

## 변경 시 주의사항

- mutation을 version 충돌 뒤 자동 재시도하지 않는다.
- profile 미완료를 authorization이나 route 차단 조건으로 사용하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
- [영역 진행 상황](progress.md)
