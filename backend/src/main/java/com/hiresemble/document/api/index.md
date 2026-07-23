# Document API 안내

## 디렉터리 목적

문서 업로드·목록·상세·text·manual text·reparse·download URL·삭제의 정확한 8개 HTTP operation과 공개 DTO를 제공한다.

## 주요 파일 및 하위 디렉터리

- [controller/](controller/index.md): HTTP endpoint
- [dto/](dto/index.md): 공개 request·response DTO
- [mapper/](mapper/index.md): 전송 DTO 변환
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

multipart와 Idempotency-Key를 검증하고 비동기 command는 202, 삭제는 204, owner 불일치는 404로 반환한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)의 use case만 호출하고 storage·parser repository를 직접 사용하지 않는다.

## 변경 시 주의사항

storage key, checksum, raw provider 오류, parser·embedding metadata를 공개하지 않고 P4 밖 endpoint를 추가하지 않는다.

## 관련 규칙 및 문서

- [Document 영역](../index.md)
- [응답·예외 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
