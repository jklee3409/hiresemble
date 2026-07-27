# Job 테스트 영역 안내

## 디렉터리 목적

P5 Job API·application·DB·Scheduler·Agent Run·idempotency 통합 계약과 domain/infrastructure 단위 계약을 검증한다.

## 주요 파일 및 하위 디렉터리

- `JobIntegrationTest`: PostgreSQL 기반 API·owner·version·retry·Scheduler 통합 검증
- [`domain/`](domain/index.md): 상태 전이와 canonicalization
- [`infrastructure/`](infrastructure/index.md): SSRF-safe fetch adapter
- [`progress.md`](progress.md): Job 테스트 상태

## 구성 요소 역할

실제 외부 네트워크와 유료 AI 없이 Fake fetch·Chat과 Testcontainers PostgreSQL을 사용한다.

## 다른 디렉터리와의 의존 관계

운영 코드는 [`../../../../../main/java/com/hiresemble/job/`](../../../../../main/java/com/hiresemble/job/index.md)에 있다.

## 변경 시 주의사항

운영 DB나 외부 웹사이트에 의존하지 않고 race·owner isolation을 DB 조건으로 검증한다.

## 관련 규칙 및 문서

- [Backend 테스트 영역](../index.md)
- [진행 상황](progress.md)
