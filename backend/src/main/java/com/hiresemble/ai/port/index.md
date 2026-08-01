# AI gateway port package 안내

## 디렉터리 목적

Chat, image text extraction, Embedding, Web Search를 provider-independent request·usage 계약으로 정의한다.

## 주요 파일 및 하위 디렉터리

- `ChatGateway`, `ImageTextExtractionGateway`, `EmbeddingGateway`, `WebSearchGateway`
- `AiGatewayResponse`, `AiUsage`, `AiPriceCatalogQueryPort`
- [`progress.md`](progress.md): port 상태

## 구성 요소 역할

timeout·allowlist·strict output type·run price version과 가격 item별 usage를 adapter 경계에 전달한다. 이미지 gateway는 Backend에서 검증·다운로드한 bounded bytes만 받고 외부 URL이나 data URL을 text-only Chat JSON에 넣지 않는다.

## 다른 디렉터리와의 의존 관계

[`../infrastructure/`](../infrastructure/index.md)가 local real/offline disabled 구현과 JDBC 가격 조회를 제공한다.

## 변경 시 주의사항

provider response는 메모리 검증 뒤 폐기하고 저장·로그하지 않는다. 유료 usage는 price version/item이 필요하다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [기술 명세](../../../../../../../../docs/spec/tech_stack.md)
