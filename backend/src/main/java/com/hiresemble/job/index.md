# Job 영역 안내

## 디렉터리 목적

P5 채용 공고의 owner-scoped API, 생성·조회·상태·추출 적용·Scheduler 도메인과 영속성·안전한 URL fetch 경계를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): 공개 Job HTTP 계약과 DTO mapping
- [`application/`](application/index.md): 생성·조회·수정·상태·추출·자동 마감 use case
- [`domain/`](domain/index.md): 두 상태 축, 상태 전이와 URL canonicalization
- [`infrastructure/`](infrastructure/index.md): JDBC store, 설정과 SSRF-safe fetch adapter
- [`progress.md`](progress.md): P5 구현·검증 이력

## 구성 요소 역할

업무 상태와 추출 상태를 분리하고 사용자 소유권·낙관적 잠금·soft delete를 모든 use case에 적용한다.

## 다른 디렉터리와의 의존 관계

- [`../agentrun/`](../agentrun/index.md)과 typed Job resource·Run 수명주기를 연결한다.
- [`../ai/`](../ai/index.md)은 Job application port를 통해 고정 추출 workflow를 실행한다.
- 공개 계약은 [`../../../../../../../docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`../../../../../../../docs/spec/db.md`](../../../../../../../docs/spec/db.md)를 따른다.

## 변경 시 주의사항

P6 분석·RAG table, DTO와 endpoint를 선행 추가하지 않는다. 외부 URL 호출은 DB transaction 밖에서 수행하고 원문·전체 HTML을 로그에 남기지 않는다.

## 관련 규칙 및 문서

- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [영역 진행 상황](progress.md)
