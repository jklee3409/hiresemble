# Job Application Port package 안내

## 디렉터리 목적

Job application과 외부 URL fetch·AI workflow 사이의 최소 typed 경계를 소유한다.

## 주요 파일 및 하위 디렉터리

- `JobPageFetchGateway`: HTML 계열 페이지 fetch와 안전한 분류 결과
- `JobPageFetchException`: safe code와 retryable 분류
- `JobWorkflowQueryPort`: owner·version·override snapshot 조회
- `JobWorkflowCommandPort`: 추출 성공·사용자 입력 필요·기술 실패 apply
- `JobAnalysisQueryPort`: owner·version/hash snapshot, reusable result와 VERIFIED evidence 검색
- `JobAnalysisCommandPort`: immutable 분석 persist·reuse Run link apply
- `JobAnalysisEmbeddingQueryPort`: active embedding policy와 exact cosine 검색
- [`progress.md`](progress.md): port 계약 상태

## 구성 요소 역할

AI package가 Job repository나 Controller를 직접 참조하지 않도록 query와 mutation을 분리한다.

## 다른 디렉터리와의 의존 관계

[`../../../ai/`](../../../ai/index.md)이 workflow에서 소비하고 [`../../infrastructure/`](../../infrastructure/index.md)이 fetch gateway를 구현한다.

## 변경 시 주의사항

WebSearch/Tavily extract를 Job URL fetch 경계로 사용하지 않는다. AI workflow에 JPA/JDBC entity나 provider/model/storage 내부값을 노출하지 않는다.

## 관련 규칙 및 문서

- [상위 application package](../index.md)
- [진행 상황](progress.md)
