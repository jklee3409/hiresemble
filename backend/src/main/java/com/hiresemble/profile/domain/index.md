# 프로필 도메인 안내

## 디렉터리 목적

P2 프로필 완료도, 구조화 resource 상태·날짜·GPA와 direct evidence 생성 규칙을 순수 Java 정책으로 표현한다.

## 주요 파일 및 하위 디렉터리

- [model/](model/index.md): 해당 계층의 값·결과 모델
- [policy/](policy/index.md): 도메인 불변식 정책
- [service/](service/index.md): use case·transaction 조정
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- `LEGAL_NAME`, `DESIRED_ROLE`, `DESIRED_INDUSTRY`, `DESIRED_LOCATION`, `PRIMARY_EDUCATION` 충족 여부를 각각 20%로 계산한다.
- source type별 direct evidence projection과 `VERIFIED` 재동기화 값을 생성한다.

## 다른 디렉터리와의 의존 관계

- [`../application/`](../application/index.md)이 정책을 transaction 안에서 호출한다.
- DB는 [`../../../../../resources/db/migration/V3__create_structured_profiles_and_direct_evidence.sql`](../../../../../resources/db/migration/V3__create_structured_profiles_and_direct_evidence.sql)로 동일 불변식을 방어한다.

## 변경 시 주의사항

- 배열은 trim·canonical 비교 뒤 중복을 거부하고 저장값도 trim한다.
- `SOURCE_DELETED` evidence는 편집·검토할 수 없으며 P2 source 삭제 시 새 tombstone을 만들지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [DB 명세](../../../../../../../../docs/spec/db.md)
- [영역 진행 상황](progress.md)
