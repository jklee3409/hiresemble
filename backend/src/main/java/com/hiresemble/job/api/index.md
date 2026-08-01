# Job API package 안내

## 디렉터리 목적

P5~P6 Job·Job Analysis HTTP 요청·응답·OpenAPI metadata와 application 결과 mapping을 소유한다.

## 주요 파일 및 하위 디렉터리

- `JobController`: Job 공개 API 10개와 실제 HTTP status
- `JobRequests`, `JobAnalysisRequests`: 생성·수정·상태·분석 request validation
- `JobDtos`, `JobAnalysisDtos`: P5~P6 공개 생성·목록·상세·분석 DTO와 P7 cover letter 진입용 최소 projection
- `JobApiMapper`, `JobAnalysisApiMapper`: application projection의 공개 DTO 변환
- [`progress.md`](progress.md): API 구현 상태

## 구성 요소 역할

Controller는 인증 사용자와 HTTP parameter를 application service로 전달하고 Entity·내부 hash·provider 정보를 노출하지 않는다. `JobDetailDto.automaticAnalysis`는 고정 품질, 안전한 상태·Run ID·오류만 additive projection으로 반환한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)의 use case만 호출하고 영속성 구현을 직접 참조하지 않는다.

## 변경 시 주의사항

성공 envelope를 추가하지 않고 200/201/202/204와 공통 오류 DTO를 유지한다. Entity·snapshot hash·provider/model 실명을 노출하지 않는다.

## 관련 규칙 및 문서

- [상위 Job 영역](../index.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
- [진행 상황](progress.md)
