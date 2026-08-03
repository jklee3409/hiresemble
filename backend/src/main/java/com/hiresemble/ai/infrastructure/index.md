# AI infrastructure package 안내

## 디렉터리 목적

local OpenAI Chat·Embedding/Tavily Search adapter, 명시적 disabled adapter, 가격 조회와 fail-closed activation 검증을 제공하고 실행 가능한 workflow contribution을 runtime에 조립한다.

## 주요 파일 및 하위 디렉터리

- `SpringAiOpenAiChatGateway`, `SpringAiOpenAiEmbeddingGateway`: Spring AI 2.0 운영 adapter, 중앙에서 검증된 strict schema 전송, 빈 tool option 비전송, request wall-clock deadline과 safe Provider rejection·finish reason 진단
- `SpringAiOpenAiImageTextExtractionGateway`: 안전한 local reference text와 검증된 JPEG·PNG·WebP byte-backed media 하나를 같은 Provider-visible message에 결합하는 OpenAI image text adapter, strict schema·retry 0·store false·chat token usage 적용
- `OpenAiChatFailureSupport`: text/image Chat adapter가 공유하는 safe status·finish reason·refusal·usage 보존 경계
- `TavilyWebSearchGateway`: HTTPS·bounded response 검색 adapter
- `DisabledChatGateway`, `DisabledImageTextExtractionGateway`, `DisabledEmbeddingGateway`, `DisabledWebSearchGateway`: capability별 offline adapter
- `AiProviderActivationValidator`, `JdbcAiPriceCatalogRepository`: 설정·immutable 가격 gate
- `AiRuntimeConfiguration`: 고정 workflow contribution과 handler·registry 조립
- [`progress.md`](progress.md): adapter 상태

## 구성 요소 역할

local은 실제 Provider를 fail-closed로 활성화하고 local-offline/test는 capability별 disabled/Fake를 사용한다. Chat request는 Provider option timeout과 별도로 bounded virtual-thread deadline을 적용하고 timeout 시 worker를 interrupt·종료한다. Chat rejection 진단은 status·구조화 code/param/request ID와 schema name/version/hash만 허용하며 raw body·prompt·schema 원문은 기록하지 않는다. generation finish reason은 `length/content_filter/incomplete` safe 분류로만 정규화하고 발생 usage를 함께 보존한다.

## 다른 디렉터리와의 의존 관계

[`../port/`](../port/index.md)의 세 gateway를 구현한다.

## 변경 시 주의사항

임의 fallback, key/raw prompt logging, provider retry와 무제한 response를 추가하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [기술 명세](../../../../../../../../docs/spec/tech_stack.md)
