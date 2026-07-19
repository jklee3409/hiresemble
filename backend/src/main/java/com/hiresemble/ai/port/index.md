# AI gateway port package 안내

## 디렉터리 목적

Chat, Embedding, Web Search를 provider-independent request·usage 계약으로 정의한다.

## 주요 파일 및 하위 디렉터리

- `ChatGateway`, `EmbeddingGateway`, `WebSearchGateway`
- `AiGatewayResponse`, `AiUsage`
- [`progress.md`](progress.md): port 상태

## 구성 요소 역할

timeout·allowlist·단위 usage와 immutable price reference를 adapter 경계에 전달한다.

## 다른 디렉터리와의 의존 관계

[`../infrastructure/`](../infrastructure/index.md)가 production disabled 구현을 제공한다.

## 변경 시 주의사항

provider response는 메모리 검증 뒤 폐기하고 저장·로그하지 않는다. 유료 usage는 price version/item이 필요하다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [기술 명세](../../../../../../../../docs/spec/tech_stack.md)
