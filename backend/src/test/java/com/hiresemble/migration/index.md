# P1~V27 Migration 테스트 안내

## 디렉터리 목적

Flyway 단계별 보존과 최신 V27 GitHub source·snapshot·provenance·typed Run 불변식을 실제 PostgreSQL에서 검증한다.

## 주요 파일 및 하위 디렉터리

- [`P1MigrationTest.java`](P1MigrationTest.java): V1→V2, V1-only upgrade, constraint·index·V1 hash
- [`P2MigrationTest.java`](P2MigrationTest.java): 빈 DB·V1·V2 upgrade, V1·V2 hash, P2 DB 불변식과 transaction rollback
- [`P3MigrationTest.java`](P3MigrationTest.java): 빈 DB·V1/V2/V3 upgrade, V1~V3 hash와 P3 constraint·범위
- [`P4MigrationTest.java`](P4MigrationTest.java): 빈 DB·V1/V2/V3/V4 upgrade, V1~V4 hash와 P4 constraint·범위
- [`P5MigrationTest.java`](P5MigrationTest.java): 빈 DB·V5 upgrade, V1~V5 hash와 P5 owner·상태·canonical constraint·범위 및 V21~V22 공고 반기 backfill·제약
- [`P6MigrationTest.java`](P6MigrationTest.java): P6 Job Analysis V7 migration·불변식
- [`FinalEducationMigrationTest.java`](FinalEducationMigrationTest.java): 빈 DB V11과 populated V10 학력 단계 backfill·최종 학력 재계산
- [`P8_5MigrationTest.java`](P8_5MigrationTest.java): V13 가격 item·불변성·provider call unique index와 V14 활성 embedding 정책
- [`P8_5UpgradeMigrationTest.java`](P8_5UpgradeMigrationTest.java): populated V12→V13 보존, V13→V14 정책 전환과 V1~V13 SHA-256 불변
- [`UserActivityMigrationTest.java`](UserActivityMigrationTest.java): 빈 DB V15와 populated V14→V15 대외활동·ACTIVITY evidence upgrade
- [`DashboardMigrationTest.java`](DashboardMigrationTest.java): 빈 DB와 populated V16→V17 가이드 seed·게시 제약·기존 데이터 보존
- [`GitHubSourceMigrationTest.java`](GitHubSourceMigrationTest.java): fresh V27 owner·shape·unique·resource parity·outbox·two-user 불변식
- [`GitHubSourceUpgradeMigrationTest.java`](GitHubSourceUpgradeMigrationTest.java): populated V26→V27 보존과 V26 SHA/checksum 고정
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- migration 적용 순서, 각 phase schema 범위와 V11 실제 데이터 보정이 계약을 넘지 않는지 독립 PostgreSQL DB에서 확인한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`hiresemble/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 적용 이력 V1~V27을 test 편의를 위해 수정하거나 H2로 대체하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
