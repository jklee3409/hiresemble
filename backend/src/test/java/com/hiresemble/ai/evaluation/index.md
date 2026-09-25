# 실제 Provider opt-in 평가·검증 도구 안내

## 디렉터리 목적

자기소개서 생성 v5 결과와 같은 자료를 일반 채팅 모델에 직접 넣은 기준선 결과를 같은 케이스로 비교하고, 이미지 전용 공고 URL의 실제 판독·필드 추출을 제한된 유료 호출로 확인한다.

## 주요 파일 및 하위 디렉터리

- `CoverLetterEvalCases`: 가상 케이스 loader
- `CoverLetterQualityRubric`: 채움 비율·제한 초과·Markdown·상투 표현·근거 없는 수치·1인칭 서술 결정적 지표
- `CoverLetterEvalHarness`: v5 실행기를 orchestrator처럼(보정 재시도 1회) 실행하고 기준선 1회, 출처를 숨긴 채용 담당자 심사를 A/B 순서를 바꿔 2회 호출한다. 7개 기준·고정 점수 기준·memo 전달·reasoning `high`를 쓰고 누적 비용 상한을 강제한다.
- `CoverLetterEvalReport`: `report.json`(답변·두 심사·지표)과 `report.md`(순서 일관 승패·포화도·기준별 차이·경고) 작성
- `CoverLetterEvalHarnessTest`: fake gateway로 전체 흐름·블라인드 매핑·지표·비용 상한 검증(CI 포함)
- `CoverLetterQualityEvaluationTest`: 실제 OpenAI 호출 opt-in 평가(`coverLetterQualityEvaluation` 태스크 전용, `test`에서 제외)
- `JobPostingImageLiveVerificationTest`: 실제 page·image fetch 뒤 이미지 판독 1회·필드 추출 1회를 호출 전 계수로 최대 3회(기본 2회) 제한해 실행하는 opt-in 검증(`jobPostingLiveVerification` 태스크 전용, `test`에서 제외, report는 `build/reports/job-posting-live/`)
- [`progress.md`](progress.md): 변경 이력

## 구성 요소 역할

모든 호출은 `ChatGateway` 경계를 지나므로 CI는 fake, opt-in 태스크는 실제 gateway를 주입한다. 기준선·심사 strict schema는 평가 전용 prompt 정의로 등록한다.

## 다른 디렉터리와의 의존 관계

운영 워크플로는 [`../../../../../../main/java/com/hiresemble/ai/workflow/`](../../../../../../main/java/com/hiresemble/ai/workflow/index.md), 케이스는 [`../../../../../resources/cover-letter-eval/`](../../../../../resources/cover-letter-eval/index.md), 실행 절차는 [운영 문서](../../../../../../../../docs/operations/cover-letter-quality-evaluation.md)에 있다.

## 변경 시 주의사항

실제 유료 호출은 `COVER_LETTER_EVAL_ENABLED=true` 또는 `JOB_POSTING_LIVE_VERIFY_ENABLED=true`와 API key가 모두 있을 때만 일어난다. 공고 검증은 `JOB_POSTING_LIVE_URL`, `JOB_POSTING_LIVE_MAX_CALLS`(최대 3)를 받는다. 이 조건을 `test`나 `check`로 옮기지 않는다. 심사 입력에 어느 답변이 Hiresemble인지 드러내지 않는다.

## 관련 규칙 및 문서

- [상위 AI 테스트 안내](../index.md)
- [Backend 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
