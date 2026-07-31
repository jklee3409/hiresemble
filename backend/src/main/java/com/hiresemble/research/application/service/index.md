# Research application service 안내

## 디렉터리 목적

조사 조회, source filter·sort와 resource-specific retry use case를 구현한다.

## 주요 파일 및 하위 디렉터리

- `ResearchApplicationService`: run·source owner 조회
- `ResearchRetryApplicationService`: retry option·idempotency 조정
- [`progress.md`](progress.md): service 상태

## 구성 요소 역할

Controller와 Agent Run 공통 retry 경계 사이에서 research 상태·옵션을 검증한다.

## 다른 디렉터리와의 의존 관계

[`../../infrastructure/`](../../infrastructure/index.md)의 store와 [`../../../agentrun/`](../../../agentrun/index.md)의 retry transaction을 사용한다.

## 변경 시 주의사항

deleted predecessor는 domain resource가 남아 있어도 retry에서 404다.

## 관련 규칙 및 문서

- [상위 application package](../index.md)
- [진행 상황](progress.md)
