# AI validation package 안내

## 디렉터리 목적

AI structured output의 parsing부터 domain command까지 검증 순서를 강제한다.

## 주요 파일 및 하위 디렉터리

- `StructuredOutputValidator`: JSON→schema→record→workflow→domain validation
- `StructuredOutputValidationException`: 값 없는 phase·stable reason·repair disposition 경계
- `StrictStructuredOutputSchemaGenerator`: Spring AI runtime schema를 생성하고 Java nullable 계약을 명시적 `null` union으로 반영
- `OpenAiStrictSchemaCompatibilityValidator`: 모든 중첩 object·required·`additionalProperties`와 OpenAI strict subset 한도를 요청 전에 재귀 검증
- `StrictStructuredOutputSchemaRegistry`: 검증된 schema 원문과 deterministic contract name·version·SHA-256 fingerprint를 runtime에 고정
- `ProviderNullable`: Provider output record의 필수 nullable property 표시
- [`progress.md`](progress.md): 검증 상태

## 구성 요소 역할

Provider에 보내기 전 schema 호환성을 검사하고 응답은 JSON parse, schema shape, Java binding, record policy, workflow context, domain command 순서로 검증한다. parse/schema/binding은 deterministic, 명시적으로 repairable인 record/workflow 의미 오류만 1회 correction 대상이며 domain 거절은 별도 실패 의미를 유지한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 gateway 응답 직후 호출한다.

## 변경 시 주의사항

provider 원문과 validation 내부 예외를 safe error로 반환하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [응답·예외 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
