# AI context package 안내

## 디렉터리 목적

원문 대신 provenance reference와 hash만 담는 workflow context snapshot 계약을 정의한다.

## 주요 파일 및 하위 디렉터리

- `ContextBuilder`: resource/version/hash, upstream refs, truncation·verification·policy projection
- `JobPostingExtractionContextBuilder`: owner·Job version과 사용자 override의 안전한 snapshot
- `JobAnalysisContextBuilder`: owner·Job/profile/evidence/policy hash만 담은 분석 snapshot reference
- `CoverLetterGenerationContextBuilder`: 공고 분석·현재 질문/version·사용자 memo·VERIFIED 근거·선택 모델의 bounded 생성 snapshot
- `CoverLetterVerificationContextBuilder`: immutable answer version·작성 당시 provenance·현재 근거 상태의 검증 snapshot
- `WorkflowContextBuilder`: workflow별 context builder dispatch
- [`progress.md`](progress.md): 계약 상태

## 구성 요소 역할

workflow 실행 시점의 안전한 reference snapshot을 메모리에서 구성한다. P7 masked chunk candidate와 답변 content는 필요한 step의 메모리에만 두고 durable context에는 owner·version·hash·reference만 남긴다. 자기소개서 v4는 접수 시 검증한 model ID와 memo hash를 재시도에서도 동일하게 사용한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 snapshot을 소비하며 실제 profile/document query port는 후속 workflow가 제공한다.

## 변경 시 주의사항

사용자 본문·문서 원문·전체 prompt를 durable snapshot이나 로그에 넣지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [시스템 설계](../../../../../../../../docs/design/system-architecture.md)
