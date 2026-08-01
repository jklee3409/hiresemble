# Document ingestion workflow 안내

## 디렉터리 목적

P4 `DOCUMENT_INGESTION`의 고정 8단계 실행과 실패 시 Document 안정 상태 보상을 정의한다.

## 주요 파일 및 하위 디렉터리

- `DocumentIngestionWorkflow`: parse·mask·chunk·embedding·structured evidence apply의 고정 contribution
- `DocumentEvidenceOutputPolicy`: prompt·record·workflow가 공유하는 candidate/ref/field/warning/token 의미 정책과 trusted local-ref mapper
- `DocumentIngestionFailureHandler`: WAITING_USER·partial failure·cancel 안정 상태 보상
- [`progress.md`](progress.md): workflow 구현 이력

## 구성 요소 역할

Backend Document query/command port만 사용하고 masked chunk만 Embedding·Chat gateway로 전달한다. Chat payload는 실제 chunk UUID 대신 `C1` local ref를 사용하고 Provider output은 model-owned semantic field만 받는다. 서버 mapper가 같은 document revision의 trusted chunk UUID·revision과 빈 metadata를 주입하며 warning은 기존 JSONB/public metadata projection으로 유지한다. candidate apply는 candidate/applied/rejected count와 stable rejection reason count를 reference-only checkpoint에 남긴다. 거절 candidate는 `failedScopeKeys`나 성공 evidence reference가 아니며 일부 또는 전체가 정상 거절돼도 apply·finalize가 완료되면 문서와 Run은 성공한다.

## 다른 디렉터리와의 의존 관계

- Document port는 [`../../../document/application/`](../../../document/application/index.md)이 제공한다.
- 실행·checkpoint·usage는 [`../../orchestration/`](../../orchestration/index.md)이 조정한다.

## 변경 시 주의사항

자유 loop, repository 직접 접근, raw text provider 전달, production Fake component를 추가하지 않는다.

## 관련 규칙 및 문서

- [AI workflow 영역](../index.md)
- [구현 계획](../../../../../../../../../docs/design/implementation-plan.md)
- [기능 명세](../../../../../../../../../docs/spec/functional.md)
