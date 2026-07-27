# Job Domain 테스트 안내

## 디렉터리 목적

Job 상태 전이·timestamp와 canonical URL 동등성·비동등성을 단위 테스트한다.

## 주요 파일 및 하위 디렉터리

- `JobDomainTest`: 허용/금지 전이와 submittedAt·reopen 규칙
- `JobUrlCanonicalizerTest`: host·port·fragment·query·tracking·path·IDNA·escape 규칙
- [`progress.md`](progress.md): domain 테스트 상태

## 구성 요소 역할

DB와 네트워크 없이 순수 domain 정책을 고정한다.

## 다른 디렉터리와의 의존 관계

운영 domain은 [`../../../../../../main/java/com/hiresemble/job/domain/`](../../../../../../main/java/com/hiresemble/job/domain/index.md)에 있다.

## 변경 시 주의사항

동등 URL뿐 아니라 reserved escape처럼 달라야 하는 URL도 반드시 회귀 테스트한다.

## 관련 규칙 및 문서

- [상위 Job 테스트](../index.md)
- [진행 상황](progress.md)
