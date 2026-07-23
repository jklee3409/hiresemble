# Document infrastructure 안내

## 디렉터리 목적

Document JDBC 영속화, Tika·PDFBox·POI parser, S3-compatible storage, exact cosine와 deletion outbox worker를 구현한다.

## 주요 파일 및 하위 디렉터리

- [adapter/](adapter/index.md): 외부 시스템 adapter
- [config/](config/index.md): 해당 계층의 실행 설정
- [persistence/](persistence/index.md): JDBC/JPA 영속성 구현
- [worker/](worker/index.md): 비동기 작업 실행
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

모든 SQL에 owner와 active document 조건을 포함하고 Object 삭제 claim·lease·retry·DEAD 처리를 원자적으로 수행한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)의 port를 구현하며 schema는 [V5 migration](../../../../../resources/db/migration/V5__create_documents_evidence_and_storage_outbox.sql)에 의존한다.

## 변경 시 주의사항

HNSW를 생성하거나 mixed dimension·generation을 검색하지 않고 provider 예외·storage key를 외부로 전달하지 않는다.

## 관련 규칙 및 문서

- [Document 영역](../index.md)
- [Infrastructure 규칙](../../../../../../../../docs/agent-rules/infrastructure.md)
- [기술 명세](../../../../../../../../docs/spec/tech_stack.md)
