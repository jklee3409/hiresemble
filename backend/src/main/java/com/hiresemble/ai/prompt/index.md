# AI prompt package 안내

## 디렉터리 목적

prompt version, typed input/output, schema, tool allowlist와 token/call cap metadata를 등록한다.

## 주요 파일 및 하위 디렉터리

- `PromptRegistry`: immutable prompt definition lookup
- `CanonicalPromptDefinitions`: 구현된 workflow prompt definition을 한 곳에서 열거해 runtime과 schema completeness 검사가 같은 목록을 사용하도록 한다.
- `DocumentIngestionPromptDefinitions`: P4 문서 근거 output v2, local chunk ref·candidate/token cap과 학력 추출 금지 instruction
- `JobPostingExtractionPromptDefinitions`: P5 추출 prompt version·output schema·call/token cap
- `JobAnalysisPromptDefinitions`: P6 단계별 prompt identity·token policy, source-only requirement v4와 eligibility·matching Provider schema, 모델 소유 필드·nullable 규칙과 외부 공고 instruction 격리. 변경 없는 upstream `BUILD_SNAPSHOT`은 기존 v6 identity를 유지해 전환 시 불필요한 checkpoint 무효화를 막는다.
- `CoverLetterGenerationPromptDefinitions`: P7 plan·question·allocation·answer·fact-check record schema
- `CoverLetterVerificationPromptDefinitions`: P7 fact·requirement·length·aggregate verification record schema
- [`progress.md`](progress.md): registry 상태

## 구성 요소 역할

workflow type+version+step key로 정확한 prompt contract를 찾는다. Job Analysis는 단계별 prompt version을 사용해 관련 없는 checkpoint를 함께 무효화하지 않는다. Chat structured output definition은 중앙 strict schema registry가 자동 열거한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 실행 전에 조회한다. P4~P7 structured prompt metadata는 production registry에 있고 provider 응답 fixture는 test scope에 있다.

## 변경 시 주의사항

P8 이후 빈 production prompt를 미리 만들지 않고 전체 prompt 내용을 로그·DB에 저장하지 않는다. 공고·근거·답변 본문은 명령이 아닌 untrusted external data로만 전달한다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [시스템 설계](../../../../../../../../docs/design/system-architecture.md)
