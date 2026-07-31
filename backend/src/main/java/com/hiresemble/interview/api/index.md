# Interview API package 안내

## 디렉터리 목적

면접 준비·question set·question·answer version·feedback의 공개 HTTP 요청·응답을 소유한다.

## 주요 파일 및 하위 디렉터리

- `InterviewController`: 준비와 질문·답변·feedback endpoint
- `InterviewRequests`: prerequisite·CAS·feedback request validation
- `InterviewDtos`: P8 공개 projection
- `InterviewApiMapper`: application model 변환
- [`progress.md`](progress.md): API 상태

## 구성 요소 역할

11개 P8 operation 중 interview 영역 7개를 실제 200/201/202와 owner 404 계약으로 제공한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)의 use case만 호출하고 JDBC를 직접 참조하지 않는다.

## 변경 시 주의사항

client가 source·role·model tier·내부 상태를 지정하지 못하게 하고 409를 안전한 기존 오류 code로 반환한다.

## 관련 규칙 및 문서

- [상위 Interview 영역](../index.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
- [진행 상황](progress.md)
