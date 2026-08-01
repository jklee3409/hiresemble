# AI workflow package 안내

## 디렉터리 목적

canonical workflow metadata와 실제 실행 contribution·step executor 경계를 정의한다.

## 주요 파일 및 하위 디렉터리

- `WorkflowRegistry`: definition/contribution 검증
- `TerminalPartialPolicy`: 실제 failed scope가 남은 terminal 결과의 workflow별 성공·실패·safe error·retry 정책
- `CanonicalWorkflowDefinitions`: 8개 WorkflowType canonical definition과 격리된 legacy version definition
- `WorkflowStepExecutor`: prepare·gateway·validation·apply contract
- `JobPostingExtractionWorkflow`: P5 URL fetch, DOM/image inspection·JPEG·PNG·WebP fetch, trusted `imageRef` text extraction, item/aggregate source compose, 사용자 override, 품질 검증과 domain apply까지의 9단계 v3 contribution
- `JobPostingExtractionFailureHandler`: 사용자 입력 필요와 기술 실패의 안전한 상태 반영
- `JobAnalysisWorkflow`: P6 owner-scoped snapshot·verified RAG·결정론적 score·command-only persist 8단계 contribution
- `CoverLetterGenerationWorkflow`: P7 generation 8단계 bounded fan-out·partial apply contribution과 Provider 전용 TipTap output→domain DTO mapping
- `CoverLetterVerificationWorkflow`: P7 immutable answer verification 6단계 contribution
- `CoverLetterVerificationFailureHandler`: verify 실패·취소 PENDING 보상
- [`document/`](document/index.md): P4 `DOCUMENT_INGESTION` 8단계 contribution과 실패 보상
- [`progress.md`](progress.md): registry 상태

## 구성 요소 역할

step 순서, schema, bounded fan-out, tool allowlist, attempt 내부 call cap, retry class와 progress weight를 검증한다. 모든 executable contribution은 terminal partial policy를 명시하고 공용 Orchestrator는 workflow 전용 오류를 알지 않는다. Document, Job과 Cover Letter workflow는 각 application port를 통해 실제 aggregate에 연결한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 definition과 contribution을 소비한다.

## 변경 시 주의사항

duplicate step key와 weight 합 오류를 거부하고 handler가 없는 canonical workflow를 executable로 등록하지 않는다. `JOB_ANALYSIS` 8개, generation 8개, verification 6개 공개 step key를 변경하지 않고 모델이 domain source·finalization을 결정하지 않게 한다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [구현 계획](../../../../../../../../docs/design/implementation-plan.md)
