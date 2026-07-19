# 프로필 영속성 안내

## 디렉터리 목적

P2 기본·구조화 프로필과 direct evidence를 PostgreSQL에서 owner-scoped로 조회·변경한다.

## 주요 파일 및 하위 디렉터리

- [`ProfileStore.java`](ProfileStore.java): JDBC mapping, pagination·sort, version mutation과 soft delete
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 모든 resource SQL에 `user_id`를 포함하고 일반 구조화 조회에서 `deleted_at IS NULL`을 강제한다.
- version 조건부 update/delete 결과와 DB 불변식 충돌을 application이 안전한 오류로 바꿀 수 있게 구분한다.

## 다른 디렉터리와의 의존 관계

- transaction은 [`../application/`](../application/index.md)이 소유한다.
- table·trigger·constraint는 [`../../../../../resources/db/migration/V3__create_structured_profiles_and_direct_evidence.sql`](../../../../../resources/db/migration/V3__create_structured_profiles_and_direct_evidence.sql)에 의존한다.

## 변경 시 주의사항

- 단건 ID만으로 resource를 조회하지 않는다.
- SQL·constraint 이름과 DB message를 HTTP 응답에 전달하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [인프라 규칙](../../../../../../../../docs/agent-rules/infrastructure.md)
- [영역 진행 상황](progress.md)
