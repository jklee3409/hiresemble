# AI runtime 테스트 안내

## 디렉터리 목적

P3 registry, router, validator, disabled gateway와 PostgreSQL Fake 3-step orchestration을 검증한다.

## 주요 파일 및 하위 디렉터리

- [`workflow/`](workflow/index.md), [`model/`](model/index.md), [`validation/`](validation/index.md)
- [`infrastructure/`](infrastructure/index.md): disabled gateway
- [`orchestration/`](orchestration/index.md): Fake 3-step PostgreSQL integration
- [`progress.md`](progress.md): AI 검증 이력

## 구성 요소 역할

test-only fixture로 정상·실패·waiting·cancel·interruption·reuse를 검증하며 production executable이나 endpoint를 만들지 않는다.

## 다른 디렉터리와의 의존 관계

운영 계약은 [`../../../../../main/java/com/hiresemble/ai/`](../../../../../main/java/com/hiresemble/ai/index.md)에 있다.

## 변경 시 주의사항

실제 AI·검색 network를 호출하지 않고 prompt fixture는 [`../../../../resources/prompts/`](../../../../resources/prompts/index.md)에만 둔다.

## 관련 규칙 및 문서

- [Backend 테스트 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [상위 테스트 안내](../index.md)
