# AI infrastructure package 안내

## 디렉터리 목적

local OpenAI Chat·Embedding/Tavily Search adapter, 명시적 disabled adapter, 가격 조회와 fail-closed activation 검증을 제공하고 실행 가능한 workflow contribution을 runtime에 조립한다.

## 주요 파일 및 하위 디렉터리

- `SpringAiOpenAiChatGateway`, `SpringAiOpenAiEmbeddingGateway`: Spring AI 2.0 운영 adapter, 빈 tool option 비전송과 safe Provider rejection 진단
- `TavilyWebSearchGateway`: HTTPS·bounded response 검색 adapter
- `DisabledChatGateway`, `DisabledEmbeddingGateway`, `DisabledWebSearchGateway`: capability별 offline adapter
- `AiProviderActivationValidator`, `JdbcAiPriceCatalogRepository`: 설정·immutable 가격 gate
- `AiRuntimeConfiguration`: 고정 workflow contribution과 handler·registry 조립
- [`progress.md`](progress.md): adapter 상태

## 구성 요소 역할

local은 실제 Provider를 fail-closed로 활성화하고 local-offline/test는 capability별 disabled/Fake를 사용한다.

## 다른 디렉터리와의 의존 관계

[`../port/`](../port/index.md)의 세 gateway를 구현한다.

## 변경 시 주의사항

임의 fallback, key/raw prompt logging, provider retry와 무제한 response를 추가하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [기술 명세](../../../../../../../../docs/spec/tech_stack.md)
