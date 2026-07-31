# Interview 테스트 영역 안내

## 디렉터리 목적

P8 migration, 공개 API, owner·idempotency·retry·CAS·history delete 계약을 PostgreSQL 통합 환경에서 검증한다.

## 주요 파일 및 하위 디렉터리

- `InterviewMigrationIntegrationTest`: 빈 DB V1→V12와 DB 불변식
- `InterviewMigrationUpgradeTest`: populated V11→V12와 V1~V11 digest
- `InterviewApiIntegrationTest`: 준비·답변·feedback·retry·cancel·history delete API
- [`progress.md`](progress.md): P8 통합 테스트 상태

## 구성 요소 역할

운영 DB와 실제 provider 없이 Testcontainers PostgreSQL과 MockMvc로 owner 복합 FK·HTTP 계약을 검증한다.

## 다른 디렉터리와의 의존 관계

운영 코드는 [`../../../../../main/java/com/hiresemble/interview/`](../../../../../main/java/com/hiresemble/interview/index.md)와 [`../../../../../main/java/com/hiresemble/research/`](../../../../../main/java/com/hiresemble/research/index.md)에 있다.

## 변경 시 주의사항

fixture terminal 시각은 container/JVM clock skew에서도 `queued_at` 이후가 되도록 설정하고 실제 네트워크를 호출하지 않는다.

## 관련 규칙 및 문서

- [Backend 테스트 영역](../index.md)
- [진행 상황](progress.md)
