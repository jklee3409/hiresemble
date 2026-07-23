# 프로필 API 안내

## 디렉터리 목적

P2 프로필·직접 근거의 HTTP status, validation, DTO와 OpenAPI operation 경계를 소유한다.

## 주요 파일 및 하위 디렉터리

- [dto/](dto/index.md): 공개 request·response DTO
- [ProfileController.java](ProfileController.java): 프로필 공개 HTTP endpoint
- [ProfileDtoMapper.java](ProfileDtoMapper.java): Controller와 package-private로 협력하는 DTO mapper
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Controller는 Session 사용자 ID와 HTTP 입력을 application service에 전달하고 status·DTO만 변환한다.
- 생성은 201, 조회·수정은 200, 삭제는 204를 사용한다.

## 다른 디렉터리와의 의존 관계

- use case는 [`../application/`](../application/index.md)에 위임한다.
- 오류 DTO·OpenAPI 공통 설정은 [`../../common/`](../../common/index.md)에 의존한다.
- 공개 계약은 [`../../../../../../../../docs/spec/api.md`](../../../../../../../../docs/spec/api.md)를 따른다.

## 변경 시 주의사항

- 성공 envelope와 직접 evidence create/delete endpoint를 추가하지 않는다.
- rejected value, SQL message와 내부 예외를 응답에 노출하지 않는다.
- `ProfileController`와 package-private `ProfileDtoMapper`는 접근 제한자 변경 없이 협력해야 하므로 이 package에 함께 남긴다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
