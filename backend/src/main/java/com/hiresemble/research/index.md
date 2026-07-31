# Research 영역 안내

## 디렉터리 목적

P8 면접 준비의 공개 정보 조사 run, topic, source, coverage와 resource-specific retry 수명주기를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): 조사 run·source·retry HTTP 계약
- [`application/`](application/index.md): owner-scoped 조회와 retry use case
- [`domain/`](domain/index.md): 조사 품질·상태·주제·출처·coverage enum
- [`infrastructure/`](infrastructure/index.md): PostgreSQL 조사 저장소
- [`progress.md`](progress.md): P8 조사 구현·검증 이력

## 구성 요소 역할

조사 결과는 원문 전체가 아닌 제한된 metadata와 snippet만 저장하고, topic-source N:M과 primary topic을 authoritative provenance로 유지한다.

## 다른 디렉터리와의 의존 관계

- [`../interview/`](../interview/index.md)가 question set과 조사 run을 1:1로 연결한다.
- [`../ai/`](../ai/index.md)이 공개 검색 계획·분류·coverage를 실행한다.
- [`../agentrun/`](../agentrun/index.md)이 retry lineage와 typed resource link를 보존한다.

## 변경 시 주의사항

타 사용자 resource는 404로 숨기고, provider rank는 정렬에만 사용한다. `LIMITED|NONE`은 성공이며 모든 provider 호출 실패만 `FAILED`다.

## 관련 규칙 및 문서

- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [API 명세](../../../../../../../docs/spec/api.md)
- [DB 명세](../../../../../../../docs/spec/db.md)
- [영역 진행 상황](progress.md)
