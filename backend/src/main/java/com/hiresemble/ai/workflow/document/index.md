# Document ingestion workflow 안내

## 디렉터리 목적

P4 `DOCUMENT_INGESTION`의 고정 8단계 실행과 실패 시 Document 안정 상태 보상을 정의한다.

## 주요 파일 및 하위 디렉터리

- `DocumentIngestionWorkflow`: parse·mask·chunk·embedding·structured evidence apply의 고정 contribution
- `DocumentIngestionFailureHandler`: WAITING_USER·partial failure·cancel 안정 상태 보상
- [`progress.md`](progress.md): workflow 구현 이력

## 구성 요소 역할

Backend Document query/command port만 사용하고 masked chunk만 Embedding·Chat gateway로 전달한다.

## 다른 디렉터리와의 의존 관계

- Document port는 [`../../../document/application/`](../../../document/application/index.md)이 제공한다.
- 실행·checkpoint·usage는 [`../../orchestration/`](../../orchestration/index.md)이 조정한다.

## 변경 시 주의사항

자유 loop, repository 직접 접근, raw text provider 전달, production Fake component를 추가하지 않는다.

## 관련 규칙 및 문서

- [AI workflow 영역](../index.md)
- [구현 계획](../../../../../../../../../docs/design/implementation-plan.md)
- [기능 명세](../../../../../../../../../docs/spec/functional.md)
