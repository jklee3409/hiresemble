# 인증 영속성 안내

## 디렉터리 목적

P1 users credential·상태의 JPA mapping 및 repository를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`UserEntity.java`](UserEntity.java): 사용자 credential·상태 영속 entity
- [`UserRepository.java`](UserRepository.java): 정규화 이메일 조회와 저장
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Flyway V2 users schema와 일치하는 mapping만 제공하고 API projection은 auth API 계층에서 만든다.
- `user_profiles`의 소유권과 등록은 [`../../profile/infrastructure/ProfileStore.java`](../../profile/infrastructure/ProfileStore.java)와 profile application 경계에 있다.

## 다른 디렉터리와의 의존 관계

- 상위 [`auth/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- Entity를 Controller 응답으로 반환하지 않고 적용된 migration을 entity에 맞춰 수정하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [공통 작업 절차](../../../../../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [응답·예외 처리 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [영역 진행 상황](progress.md)
