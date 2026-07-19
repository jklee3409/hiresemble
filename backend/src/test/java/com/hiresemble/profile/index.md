# 프로필 테스트 안내

## 디렉터리 목적

P2 프로필·direct evidence의 도메인 규칙과 실제 Spring HTTP·PostgreSQL 통합 계약을 검증한다.

## 주요 파일 및 하위 디렉터리

- [`domain/`](domain/index.md): 완료도·날짜·GPA·evidence 정책 단위 테스트
- [`api/`](api/index.md): 프로필 HTTP·owner·version·동기화 통합 테스트
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 운영 package 경계와 대응되는 테스트로 AC-02와 P1 회귀를 함께 고정한다.

## 다른 디렉터리와의 의존 관계

- 공유 PostgreSQL fixture는 [`../support/`](../support/index.md)를 사용한다.
- 공개 계약은 [`../../../../../../../docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`../../../../../../../docs/spec/db.md`](../../../../../../../docs/spec/db.md)를 따른다.

## 변경 시 주의사항

- 실제 외부 provider와 운영 DB를 사용하지 않는다.
- owner 실패는 타 사용자·없는 ID 모두 404로 assertion한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [영역 진행 상황](progress.md)
