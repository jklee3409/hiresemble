# Cover Letter 테스트 영역 안내

## 디렉터리 목적

P7 자기소개서 API·application·DB migration·수명주기·Agent Run 연결과 TipTap domain 계약을 검증한다.

## 주요 파일 및 하위 디렉터리

- `CoverLetterApiIntegrationTest`: 공개 endpoint·validation·owner·Idempotency·CAS
- `CoverLetterApplicationIntegrationTest`: 문항·version·provenance·archive와 AI apply
- `CoverLetterFinalizationIntegrationTest`: fresh verification·warning acknowledgement·finalize
- `CoverLetterMigrationIntegrationTest`, `CoverLetterMigrationUpgradeTest`: V8 빈 DB·upgrade·제약·fingerprint
- [`domain/`](domain/index.md): TipTap canonicalization 단위 검증
- [`progress.md`](progress.md): P7 테스트 상태

## 구성 요소 역할

실제 외부 provider 없이 PostgreSQL Testcontainers와 application/API 경계를 검증한다.

## 다른 디렉터리와의 의존 관계

운영 코드는 [`../../../../../main/java/com/hiresemble/coverletter/`](../../../../../main/java/com/hiresemble/coverletter/index.md)에 있다.

## 변경 시 주의사항

H2·SQLite로 PostgreSQL partial unique·trigger·복합 FK 계약을 대체하지 않는다.

## 관련 규칙 및 문서

- [Backend 테스트 영역](../index.md)
- [진행 상황](progress.md)
