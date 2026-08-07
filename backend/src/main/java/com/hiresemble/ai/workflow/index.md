# AI workflow package 안내

## 디렉터리 목적

canonical workflow metadata와 실제 실행 contribution·step executor 경계를 정의한다.

## 주요 파일 및 하위 디렉터리

- `WorkflowRegistry`: definition/contribution 검증
- `TerminalPartialPolicy`: 실제 failed scope가 남은 terminal 결과의 workflow별 성공·실패·safe error·retry 정책
- `CanonicalWorkflowDefinitions`: 9개 WorkflowType canonical definition과 Document·Cover Letter의 격리된 legacy version definition
- `WorkflowStepExecutor`: prepare·gateway·validation·apply contract
- `JobPostingExtractionWorkflow`: P5 URL fetch, DOM/image inspection·JPEG·PNG·WebP fetch, provider-visible reference binding을 거친 trusted `imageRef` text extraction, item/aggregate source compose, 사용자 override, 품질 검증과 domain apply까지의 9단계 v3 contribution
- `JobPostingExtractionFailureHandler`: 사용자 입력 필요와 기술 실패의 안전한 상태 반영
- `JobAnalysisWorkflow`: P6 owner-scoped snapshot·active embedding policy route 기반 verified RAG·결정론적 score·command-only persist 8단계 contribution과 block ID·원문·ordinal만 소유하는 Provider requirements·eligibility·match output→서버 내부 DTO mapping
- `JobRequirementNormalizationPolicy`: source requirement의 명확한 atomic 분할, section·required·support type·category·근무일 결정, 중복 제거와 source ordinal/text provenance를 소유하는 단일 canonical 정책
- `CoverLetterGenerationWorkflow`: P7 generation v1~v3 durable contribution과 active v4 8단계 bounded fan-out, 선택 모델·사용자 memo·framework-neutral section plan·exact excerpt provenance·명시적 truncation·한국어 출력·cross-answer duplication 검증·partial apply
- `CoverLetterWorkflowV3Policy`: 문항 유형–framework–section, issue compatibility, bounded text hash/count, relevance evidence selection과 generation/verification 공용 duplication policy
- `CoverLetterVerificationWorkflow`: P7 immutable answer verification v1~v3 durable contribution과 active v4 선택 모델·relevance evidence selection·exact claim grounding·한국어 fact/writing-quality·sibling truncation context를 포함한 6단계 contribution
- `CoverLetterVerificationFailureHandler`: verify 실패·취소 PENDING 보상
- [`document/`](document/index.md): P4 active `DOCUMENT_INGESTION` 9단계·legacy 8단계 contribution과 실패 보상
- [`github/`](github/index.md): Phase 1 `GITHUB_INGESTION` 10단계·same-run wait/resume·실패 보상
- [`progress.md`](progress.md): registry 상태

## 구성 요소 역할

step 순서, schema, bounded fan-out, tool allowlist, attempt 내부 call cap, retry class와 progress weight를 검증한다. 모든 executable contribution은 terminal partial policy를 명시하고 공용 Orchestrator는 workflow 전용 오류를 알지 않는다. Document, Job과 Cover Letter workflow는 각 application port를 통해 실제 aggregate에 연결한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 definition과 contribution을 소비한다.

## 변경 시 주의사항

duplicate step key와 weight 합 오류를 거부하고 handler가 없는 canonical workflow를 executable로 등록하지 않는다. `JOB_ANALYSIS` 8개, generation 8개, verification 6개 공개 step key를 변경하지 않고 모델이 domain source·finalization을 결정하지 않게 한다. Cover Letter 신규 접수는 v4 canonical definition을 사용하고 기존 v1~v3 Run은 exact non-canonical executable definition으로 재개한다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [구현 계획](../../../../../../../../docs/design/implementation-plan.md)
