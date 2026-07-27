# Job API package 안내

## 디렉터리 목적

P5 Job HTTP 요청·응답·OpenAPI metadata와 application 결과 mapping을 소유한다.

## 주요 파일 및 하위 디렉터리

- `JobController`: Job 공개 API 7개와 실제 HTTP status
- `JobRequests`: 생성·수정·상태 변경 request validation
- `JobDtos`: P5 공개 생성·목록·상세·mutation DTO
- `JobApiMapper`: application projection의 공개 DTO 변환
- [`progress.md`](progress.md): API 구현 상태

## 구성 요소 역할

Controller는 인증 사용자와 HTTP parameter를 application service로 전달하고 Entity·내부 hash·provider 정보를 노출하지 않는다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)의 use case만 호출하고 영속성 구현을 직접 참조하지 않는다.

## 변경 시 주의사항

성공 envelope를 추가하지 않고 201/202/204와 공통 오류 DTO를 유지한다. P6 analysis endpoint는 이 package에 없다.

## 관련 규칙 및 문서

- [상위 Job 영역](../index.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
- [진행 상황](progress.md)
