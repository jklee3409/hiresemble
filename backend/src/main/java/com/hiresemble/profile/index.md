# 프로필·직접 근거 영역 안내

## 디렉터리 목적

P2 기본 프로필, 지원 자격 자기신고, 구조화 경력과 P4 Document에서 추출되는 canonical 경험을 사용자 소유 resource로 관리하고, 공고 분석·자기소개서·면접에 owner-scoped snapshot을 제공한다.

## 주요 파일 및 하위 디렉터리

- [api/](api/index.md): HTTP 전송 계층
- [application/](application/index.md): use case와 application 경계
- [domain/](domain/index.md): 도메인 모델과 규칙
- [infrastructure/](infrastructure/index.md): 영속성·외부 연동 구현
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 기본 프로필 완료 항목 다섯 개는 서버가 계산하며 미완료 상태는 route hard gate가 아니다.
- 학력은 단계·상태·날짜 순위로 최종 학력을 자동 계산하고, 나머지 구조화 source는 생성·수정·삭제 때 연결된 direct evidence와 같은 transaction에서 동기화된다.
- 근무 가능일·병역·해외여행·채용 결격 제한은 작은 1:1 자기신고 record로 관리하고 미입력 enum은 `UNSPECIFIED`로 유지한다.
- 대외활동은 문서와 독립된 `activities`가 원본이며 `useAsMaterial`이 켜진 항목만 ACTIVITY evidence를 `VERIFIED`로 제공한다.
- 자격증·어학·수상의 증빙 문서는 같은 사용자 active Document만 허용하고 document evidence는 PENDING으로 적용한다.
- 여러 문서에서 추출된 같은 경험은 semantic 정책으로 하나의 canonical `EXPERIENCE`에 출처를 연결하며 불확실한 관련·충돌 판정은 사용자 해결 대상으로 남긴다.
- 삭제 문서 evidence는 참조가 없으면 삭제하고 참조가 있으면 동일 ID의 `SOURCE_DELETED` read-only tombstone으로 전환한다.
- 모든 단건 조회와 mutation은 Session principal의 사용자 ID를 함께 사용한다.
- 공고 분석에는 canonical profile hash, 대표 학력·지원 자격 typed fact, `VERIFIED` evidence의 ID·version·hash·provenance를 제공하며 profile entity와 원문 전체를 노출하지 않는다.

## 다른 디렉터리와의 의존 관계

- 인증 사용자 ID는 [`../auth/`](../auth/index.md)의 Session principal에서 받는다.
- P6 공고 분석 query 경계는 [`../job/application/port/`](../job/application/port/index.md)가 정의하고 이 영역의 application service가 구현한다.
- P7 자기소개서는 기존 evidence query와 document 삭제 참조 경계를 통해 현재 상태와 historical provenance를 분리한다.
- 기본 불변식은 [`../../../../resources/db/migration/V3__create_structured_profiles_and_direct_evidence.sql`](../../../../resources/db/migration/V3__create_structured_profiles_and_direct_evidence.sql), document owner FK는 V5, 최종 학력은 V11, 지원 자격은 V19, canonical 경험·출처·embedding은 [`../../../../resources/db/migration/V26__create_canonical_experience_library.sql`](../../../../resources/db/migration/V26__create_canonical_experience_library.sql)에 의존한다.
- 공개 계약은 [`../../../../../../../docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`../../../../../../../docs/spec/db.md`](../../../../../../../docs/spec/db.md)를 따른다.

## 변경 시 주의사항

- non-null `evidenceDocumentId`와 evidence `documentId` filter는 같은 사용자 active 문서만 허용하며 타 사용자·삭제 문서는 404다.
- source는 soft delete하고 P2에서는 downstream provenance가 없으므로 연결 direct evidence를 삭제한다.
- direct evidence는 원본 구조화 resource와 함께 관리한다. 문서 evidence만 개별·최대 100개 검토 API로 상태를 바꾸며 `SOURCE_DELETED`는 terminal read-only다.
- version 충돌을 자동 재시도하지 않고 `RESOURCE_VERSION_CONFLICT` 409로 반환한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../docs/agent-rules/backend-response-exception.md)
- [API 명세](../../../../../../../docs/spec/api.md)
- [영역 진행 상황](progress.md)
