# Research application package 안내

## 디렉터리 목적

조사 조회·source pagination과 predecessor 단일 successor retry를 조정한다.

## 주요 파일 및 하위 디렉터리

- [`model/`](model/index.md): application row·view model
- [`service/`](service/index.md): 조회·retry use case
- [`progress.md`](progress.md): application 상태

## 구성 요소 역할

owner visibility, terminal/retryable 상태, 재요청 옵션 호환성과 공통 Agent Run retry claim을 transaction 경계에서 조정한다.

## 다른 디렉터리와의 의존 관계

[`../infrastructure/`](../infrastructure/index.md)와 [`../../agentrun/`](../../agentrun/index.md)을 사용한다.

## 변경 시 주의사항

retry는 기존 결과를 수정하지 않고 새 research/question set/run lineage를 정확히 하나 만든다.

## 관련 규칙 및 문서

- [상위 Research 영역](../index.md)
- [Backend 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [진행 상황](progress.md)
