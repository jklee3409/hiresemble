# Document 테스트 안내

## 디렉터리 목적

P4 문서 API·수명주기·owner·idempotency와 application·infrastructure 경계를 PostgreSQL 통합 테스트로 검증한다.

## 주요 파일 및 하위 디렉터리

- `DocumentIntegrationTest`: 문서 HTTP·Run·evidence·delete 통합 계약
- [`application/`](application/index.md): text pipeline과 deletion outbox
- [`infrastructure/`](infrastructure/index.md): parser·embedding policy·실제 MinIO
- [`progress.md`](progress.md): P4 테스트 이력

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
