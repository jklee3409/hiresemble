# Document ingestion workflow 테스트 안내

## 디렉터리 목적

P4 고정 ingestion workflow의 성공, WAITING_USER resume와 AI partial failure를 검증한다.

## 주요 파일 및 하위 디렉터리

- workflow contract·orchestrator integration test
- [`progress.md`](progress.md): P4 workflow 테스트 이력

## 구성 요소 역할

Fake embedding·Chat을 사용해 8단계 순서, masked-only gateway, PENDING evidence와 안정 상태 보상을 고정한다.

## 다른 디렉터리와의 의존 관계

운영 contribution은 [`../../../../../../../main/java/com/hiresemble/ai/workflow/document/`](../../../../../../../main/java/com/hiresemble/ai/workflow/document/index.md)을 검증한다.

## 변경 시 주의사항

Fake를 production component로 등록하거나 외부 network를 호출하지 않는다.

## 관련 규칙 및 문서

- [AI 테스트](../../index.md)
- [진행 상황](progress.md)
