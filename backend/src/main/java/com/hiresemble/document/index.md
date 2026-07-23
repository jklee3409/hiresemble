# Document 영역 안내

## 디렉터리 목적

사용자 소유 문서의 업로드, 파싱, 원문·마스킹 텍스트, 결정적 청크, embedding, 근거 추출, 다운로드와 삭제 수명주기를 관리한다.

## 주요 파일 및 하위 디렉터리

- [api/](api/index.md): HTTP 전송 계층
- [application/](application/index.md): use case와 application 경계
- [domain/](domain/index.md): 도메인 모델과 규칙
- [infrastructure/](infrastructure/index.md): 영속성·외부 연동 구현
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- parse 상태와 evidence extraction 상태를 독립적으로 유지한다.
- 원본 preview와 AI 전송용 masked content를 분리하고 workflow에는 port만 공개한다.
- 삭제 transaction은 API 즉시 404와 outbox enqueue를 보장하며 Object 삭제는 worker가 수행한다.

## 다른 디렉터리와의 의존 관계

- Agent Run 수명주기는 [`../agentrun/`](../agentrun/index.md), 고정 ingestion workflow는 [`../ai/workflow/document/`](../ai/workflow/document/index.md)을 사용한다.
- document evidence command/query는 [`../profile/application/`](../profile/application/index.md)에서 제공한다.
- V5 불변식은 [`../../../../resources/db/migration/V5__create_documents_evidence_and_storage_outbox.sql`](../../../../resources/db/migration/V5__create_documents_evidence_and_storage_outbox.sql)에 정의한다.

## 변경 시 주의사항

- storage key, checksum, parser·embedding metadata와 provider 오류를 공개 DTO나 일반 로그에 노출하지 않는다.
- 기존 migration을 수정하지 않고 parser·chunk·embedding 정책 변경은 version 또는 source revision으로 구분한다.
- 실제 provider, HNSW, P5 이후 aggregate를 이 영역에 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [API 명세](../../../../../../../docs/spec/api.md)
- [영역 진행 상황](progress.md)
