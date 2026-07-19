# Document application 안내

## 디렉터리 목적

문서 command/query transaction, workflow port, Object Storage port, evidence 적용과 삭제 outbox 수명주기를 조정한다.

## 주요 파일 및 하위 디렉터리

- 문서 생성·조회·manual text·reparse·삭제 service
- `DocumentWorkflowQueryPort`, `DocumentWorkflowCommandPort`: AI repository 비의존 경계와 Agent Run active owner resolution
- `ObjectStoragePort`, `DocumentSourceReadPort`: S3 호환 Object 경계
- text normalization·masking·chunk policy와 deletion outbox service
- [`progress.md`](progress.md): application 구현 이력

## 구성 요소 역할

Object 업로드와 짧은 DB transaction을 분리하고 commit 실패에는 보상 삭제 또는 orphan cleanup outbox를 남긴다.

## 다른 디렉터리와의 의존 관계

[`../domain/`](../domain/index.md)의 상태를 적용하고 [`../../ai/workflow/document/`](../../ai/workflow/document/index.md)에 제한된 port만 제공한다.

## 변경 시 주의사항

외부 Object·AI 호출을 DB transaction 안에서 수행하지 않고 terminal Run을 다시 열지 않는다.

## 관련 규칙 및 문서

- [Document 영역](../index.md)
- [Backend 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [구현 계획](../../../../../../../../docs/design/implementation-plan.md)
