# Progress

## Overview

Chat·Embedding·Search gateway, multi-usage와 immutable price query 계약이 구현됐다.

## [2026-08-02] Session Summary (request-scoped OpenAI 품질 옵션)

- What was done:
  - `ChatRequest`에 선택적 reasoning effort와 verbosity 계약을 추가하고 기존 생성자 호환성을 유지했다.
- Key decisions:
  - 허용 값만 port 경계에서 검증하고 null은 기존 Provider 기본값을 의미한다.
- Issues encountered:
  - None.
- Validation:
  - 기존 호출부 compile과 gateway·workflow 집중 테스트가 통과했다.
- Next steps:
  - None.

## [2026-08-01] Session Summary (ImageTextExtractionGateway 경계)

- What was done:
  - bounded image media, model/prompt/schema/price context와 `AiGatewayResponse` usage를 잇는 별도 capability port를 추가했다.
- Key decisions:
  - 외부 URL/data URL을 text JSON에 넣지 않고 byte array는 defensive copy한다.
- Issues encountered:
  - None.
- Validation:
  - provider unit·workflow integration·전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (price-versioned multi-usage Provider port)

- What was done:
  - request에 run price version·strict output type·max output token을 추가하고 response를 `List<AiUsage>`로 분해했다.
  - exact provider/product/unit 조회와 CEILING cost를 소유하는 `AiPriceCatalogQueryPort`를 추가했다.
- Key decisions:
  - 하나의 usage row는 하나의 price item만 참조한다.
- Issues encountered:
  - 기존 Fake fixture는 secondary constructor로 호환성을 유지했다.
- Validation:
  - 전체 420 tests와 adapter unit test가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 bounded search request)

- What was done:
  - BASIC/ADVANCED query·result 한도와 검색 결과 metadata를 표현하도록 WebSearchGateway request/result를 확장했다.
- Key decisions:
  - provider rank는 내부 정렬에만 사용하고 공개 DTO·checkpoint에는 노출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - query/result 상한과 usage count, 실제 provider 0회 테스트가 통과했다.
- Next steps:
  - None.

## [2026-07-19] Session Summary (provider-independent gateway port 구현)

- What was done:
  - 세 gateway request와 CHAT·EMBEDDING·SEARCH usage projection을 정의했다.

- Key decisions:
  - Fake/cache hit도 0 cost usage를 허용하고 실제 가격 없는 유료 usage를 거부한다.

- Issues encountered:
  - None.

- Validation:
  - Chat Fake zero-cost usage와 disabled 세 gateway 테스트가 통과했다.

- Next steps:
  - Embedding·Search 실제 adapter 검증은 해당 phase에서 수행한다.
