# AI infrastructure 테스트 안내

## 디렉터리 목적

local/offline Bean matrix, OpenAI Chat·Embedding request/usage·빈 tool option·safe rejection, Tavily bounded HTTP와 Codex opt-in live gate를 검증한다.

## 주요 파일 및 하위 디렉터리

- `DisabledAiGatewaysTest`
- `SpringAiOpenAiGatewayTest`
- `TavilyWebSearchGatewayTest`
- `LocalProviderBeanMatrixIntegrationTest`, `LocalOfflineBeanMatrixIntegrationTest`
- `CodexRealProviderTest` (일반 check 제외)
- [`progress.md`](progress.md)

## 구성 요소 역할

capability별 단일 Bean, hidden retry 0, strict output, vector shape, 가격 row와 network-disabled 기본 회귀를 고정한다. 실제 Spring OpenAI SDK 요청 객체를 capture해 runtime registry의 schema·strict flag·store/tool 설정과 schema rejection 분류를 확인한다.

## 다른 디렉터리와의 의존 관계

[`../../../../../../main/java/com/hiresemble/ai/infrastructure/`](../../../../../../main/java/com/hiresemble/ai/infrastructure/index.md)을 검증한다.

## 변경 시 주의사항

network mock조차 필요 없는 disabled 경계를 유지한다.

## 관련 규칙 및 문서

- [상위 AI 테스트](../index.md)
