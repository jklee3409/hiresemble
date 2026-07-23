# Agent Run application package 안내

## 디렉터리 목적

Run 생성·조회·상태 전이·retry·cancel·resume와 AI workflow가 소비할 application port를 정의한다.

## 주요 파일 및 하위 디렉터리

- [command/](command/index.md): 상태 변경 입력
- [model/](model/index.md): 해당 계층의 값·결과 모델
- [port/](port/index.md): application port
- [query/](query/index.md): 조회 입력
- [service/](service/index.md): use case·transaction 조정
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

transaction과 owner·stateVersion·lineage 규칙을 조정하며 AI 구현 세부사항과 domain repository를 분리한다. P4 Document resource는 typed owner resolver와 compensation port로 연결한다.

## 다른 디렉터리와의 의존 관계

- [`../domain/`](../domain/index.md)의 상태 규칙을 사용한다.
- [`../infrastructure/`](../infrastructure/index.md)가 persistence port를 구현한다.
- [`../../ai/`](../../ai/index.md)가 workflow execution port를 소비한다.
- [`../../document/application/`](../../document/application/index.md)이 Document typed resource·cancel 안정 상태 경계를 제공한다.

## 변경 시 주의사항

reserve 실패 시 Run 생성 transaction이 남지 않아야 하며 retry는 predecessor를 수정하지 않는다. domain apply는 owner·version·input hash를 다시 검증해야 한다.

## 관련 규칙 및 문서

- [Backend 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [상위 영역](../index.md)
