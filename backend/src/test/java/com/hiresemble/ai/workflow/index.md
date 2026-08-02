# AI workflow 테스트 안내

## 디렉터리 목적

canonical registry coverage와 Document·Job·Cover Letter metadata·contribution 불변식을 검증한다.

## 주요 파일 및 하위 디렉터리

- `WorkflowRegistryTest`
- [`document/`](document/index.md): P4 ingestion 성공·resume·partial failure 검증
- `JobPostingExtractionWorkflowContractTest`: P5 고정 순서·prompt/schema·privacy 계약
- `JobPostingExtractionOrchestratorIntegrationTest`: P5 성공·waiting·retry·cancel·reuse 통합 계약
- `JobAnalysisWorkflowTest`: P6 Provider DTO→내부 DTO mapping, 8단계 신규·재사용·점수·persist와 세부 safe reason
- `JobAnalysisWorkflowContractTest`: P6 공개 step, prompt/schema identity와 Provider 타입의 모델 소유 필드 경계
- `CoverLetterGenerationWorkflowTest`: P7 8단계·Provider TipTap mapping·partial success/retry·restart
- `CoverLetterVerificationWorkflowTest`: P7 6단계·issue·compensation
- `CoverLetterWorkflowContractTest`: 공개 step·structured output·privacy 계약
- [`progress.md`](progress.md)

## 구성 요소 역할

8개 type, duplicate key, weight와 executable sequence를 고정하고 P7 generation 8단계·verification 6단계 contribution을 검증한다.

## 다른 디렉터리와의 의존 관계

[`../../../../../../main/java/com/hiresemble/ai/workflow/`](../../../../../../main/java/com/hiresemble/ai/workflow/index.md)을 검증한다.

## 변경 시 주의사항

definition 존재를 executable handler 존재로 해석하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 테스트](../index.md)
