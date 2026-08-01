# Job Application package 안내

## 디렉터리 목적

P5 Job 생성·조회·수정·상태·추출 적용·Scheduler와 P6 분석 접수·snapshot·RAG·persist transaction 경계를 조정한다.

## 주요 파일 및 하위 디렉터리

- `JobApplicationService`: owner-scoped 목록·상세 조회
- `JobCreationService`: canonical duplicate·idempotency·수동/비동기 생성
- `JobExtractionLaunchFactory`: 신규·retry 공고 추출의 canonical v3 input snapshot/hash 생성
- `JobPostingExtractionRetryContributor`: legacy terminal Run을 최신 workflow로 승격하고 predecessor unique·현재 Job·latest Run·QUEUED 계약 유지
- `JobMutationService`: version 수정과 soft delete
- `JobStatusService`: 사용자 상태 전이와 history transaction
- `JobExtractionMutationService`: workflow snapshot·성공/실패/수동 resume 적용
- `JobAnalysisApplicationService`: 분석 접수·reuse·owner-scoped snapshot/retrieval·immutable apply·OUTDATED projection
- `JobDeadlineScheduler`: batch 자동 마감
- [`model/`](model/index.md): application 결과 record
- [`port/`](port/index.md): fetch와 workflow query/mutation 경계

## 구성 요소 역할

외부 URL·모델 호출은 transaction 밖 workflow step에서 실행하고 Job·Run 생성, 상태·history, 추출/분석 apply는 각각 필요한 transaction으로 묶는다.

## 다른 디렉터리와의 의존 관계

[`../domain/`](../domain/index.md)의 정책과 [`../infrastructure/`](../infrastructure/index.md)의 store/adapter를 사용한다.

## 변경 시 주의사항

하나의 service에 모든 책임을 모으지 않고 owner·version 재검증을 domain apply 직전에 유지한다.

## 관련 규칙 및 문서

- [상위 Job 영역](../index.md)
- [진행 상황](progress.md)
