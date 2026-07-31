# Interview application package 안내

## 디렉터리 목적

면접 준비 prerequisite, 질문·답변·feedback transaction과 AI workflow 경계를 조정한다.

## 주요 파일 및 하위 디렉터리

- [`model/`](model/index.md): 면접 application record
- [`port/`](port/index.md): workflow query·command boundary
- [`service/`](service/index.md): 공개 use case와 retry contributor
- [`progress.md`](progress.md): application 상태

## 구성 요소 역할

Controller와 AI runtime이 persistence를 직접 공유하지 않도록 owner-scoped query와 atomic command를 분리한다.

## 다른 디렉터리와의 의존 관계

[`../infrastructure/`](../infrastructure/index.md), [`../../research/`](../../research/index.md), [`../../agentrun/`](../../agentrun/index.md)에 의존한다.

## 변경 시 주의사항

외부 호출 중 transaction을 열지 않고 성공 checkpoint와 domain apply만 짧게 원자 commit한다.

## 관련 규칙 및 문서

- [상위 Interview 영역](../index.md)
- [Backend 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [진행 상황](progress.md)
