# Job Application Model package 안내

## 디렉터리 목적

Job use case가 API와 workflow에 반환하는 immutable 결과 record를 소유한다.

## 주요 파일 및 하위 디렉터리

- `JobApplicationResults`: 생성 accepted, page/detail, mutation과 workflow snapshot 결과
- `JobAnalysisModels`: 최소 profile/evidence snapshot, retrieval candidate, immutable summary/detail과 persist command
- [`progress.md`](progress.md): 결과 계약 상태

## 구성 요소 역할

Entity나 JDBC row를 외부 계층에 전달하지 않고 P5~P6에 필요한 immutable projection만 명시한다.

## 다른 디렉터리와의 의존 관계

상위 [`../`](../index.md) service가 생성하고 [`../../api/`](../../api/index.md)와 AI workflow port가 소비한다.

## 변경 시 주의사항

내부 hash·provider 응답·storage 정보를 공개 결과에 넣지 않는다.

## 관련 규칙 및 문서

- [상위 application package](../index.md)
- [진행 상황](progress.md)
