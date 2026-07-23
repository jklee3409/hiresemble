# Document 테스트 안내

## 디렉터리 목적

P4 문서 API·수명주기·owner·idempotency와 application·infrastructure 경계를 PostgreSQL 통합 테스트로 검증한다.

## 주요 파일 및 하위 디렉터리

- [application/](application/index.md): use case와 application 경계
- [infrastructure/](infrastructure/index.md): 영속성·외부 연동 구현
- [DocumentIntegrationTest.java](DocumentIntegrationTest.java): 현재 package에 직접 남는 구현
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

운영 DB·Object Storage·유료 provider를 사용하지 않고 격리 fixture로 정상·오류·동시성 경계를 검증한다.

## 다른 디렉터리와의 의존 관계

운영 구현은 [`../../../../../main/java/com/hiresemble/document/`](../../../../../main/java/com/hiresemble/document/index.md), 공유 PostgreSQL은 [`../support/`](../support/index.md)을 사용한다.

## 변경 시 주의사항

실제 provider 호출이나 기존 개발 DB mutation을 추가하지 않는다.

## 관련 규칙 및 문서

- [Backend 테스트 영역](../index.md)
- [Document 구현](../../../../../main/java/com/hiresemble/document/index.md)
- [영역 진행 상황](progress.md)
