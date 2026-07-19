# Agent Run API package 안내

## 디렉터리 목적

인증 사용자에게 Agent Run 목록·상세·SSE·retry·cancel의 안전한 HTTP projection을 제공한다.

## 주요 파일 및 하위 디렉터리

- `AgentRunController`: 정확한 5개 공개 operation
- `AgentRunSseService`: snapshot-first subscriber와 heartbeat·terminal cleanup
- `AgentRun*Dto`, event DTO: 공개 허용 field
- [`progress.md`](progress.md): API 구현 상태

## 구성 요소 역할

application snapshot을 DTO로 변환하고 owner 404, filter allowlist, idempotency header와 stateVersion CAS를 HTTP 계약으로 고정한다.

## 다른 디렉터리와의 의존 관계

- [`../application/`](../application/index.md)의 query·retry·cancel port를 호출한다.
- 공통 오류 형식은 [`../../common/`](../../common/index.md)을 사용한다.

## 변경 시 주의사항

claim token, worker, lease, hash, prompt, provider/model ID와 raw JSON을 공개하지 않는다. SSE event ID는 stateVersion이며 durable replay log를 만들지 않는다.

## 관련 규칙 및 문서

- [응답·예외 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
- [상위 영역](../index.md)
