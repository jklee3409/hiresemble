# Cover Letter API package 안내

## 디렉터리 목적

P7 자기소개서·문항·답변 version·검증·수명주기의 HTTP 요청·응답, validation과 OpenAPI metadata를 소유한다.

## 주요 파일 및 하위 디렉터리

- `CoverLetterController`: 생성·목록·상세·편집·AI·version·verification·lifecycle API
- `CoverLetterRequests`: Idempotency·CAS·문항·TipTap·finalize request validation
- `CoverLetterDtos`: summary/detail/question/version/verification 공개 projection
- `CoverLetterApiMapper`: application model의 공개 DTO 변환
- [`progress.md`](progress.md): API 구현 상태

## 구성 요소 역할

Controller는 인증 사용자를 application service에 전달하고 성공 envelope 없이 실제 200/201/202/204를 반환한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)의 use case만 호출하고 persistence 구현을 직접 참조하지 않는다.

## 변경 시 주의사항

타 사용자 resource는 404로 숨기고 409를 자동 재시도하지 않는다. provider/model, prompt, storage key나 전체 내부 snapshot을 공개 DTO에 포함하지 않는다.

## 관련 규칙 및 문서

- [상위 Cover Letter 영역](../index.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
- [진행 상황](progress.md)
