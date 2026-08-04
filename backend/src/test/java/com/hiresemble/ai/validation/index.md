# AI validation 테스트 안내

## 디렉터리 목적

structured output 검증 순서와 retry 분류를 검증한다.

## 주요 파일 및 하위 디렉터리

- `StructuredOutputValidatorTest`
- `OpenAiStrictSchemaCompatibilityValidatorTest`: 기존 bare object 회귀, 전체 등록 output 자동 열거, registry completeness, nullable·fingerprint 안정성
- `JobAnalysisStrictStructuredOutputContractTest`: 실제 registry의 P6 Provider schema에서 서버 소유 section/location 부재와 block ID·원문·ordinal non-null 계약
- [`progress.md`](progress.md)

## 구성 요소 역할

schema부터 domain command까지 정확한 호출 순서를 고정하고 새 Chat output definition이 중앙 registry/호환성 검사에서 누락되면 실패시킨다.

## 다른 디렉터리와의 의존 관계

[`../../../../../../main/java/com/hiresemble/ai/validation/`](../../../../../../main/java/com/hiresemble/ai/validation/index.md)을 검증한다.

## 변경 시 주의사항

검증 실패 message에 fixture 원문을 노출하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 테스트](../index.md)
