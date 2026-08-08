# `docs/design` 디렉터리 안내

## 디렉터리 목적

이 디렉터리는 `docs/spec/`의 제품 계약을 실제 모듈, 데이터, API, 화면, workflow와 구현 순서로 연결한 파생 설계를 관리한다. 기준 명세를 대체하지 않으며 P0 결정 과정은 승인 기록으로, 실제 구현 상태는 코드와 진행 문서로 분리한다.

## 주요 파일 및 하위 디렉터리

| 경로                                                                                             | 역할                                                                                                                                                                         |
| ------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [`system-architecture.md`](system-architecture.md)                                               | 프로젝트 목적, MVP, 전체 architecture, 모듈·도메인 관계, DB/API/page 추적, 주요 workflow, 보안·비동기 설계와 명세 이슈를 통합한다.                                           |
| [`implementation-plan.md`](implementation-plan.md)                                               | 단계별 선행 의존성, 미배정 목표 vertical, 완료 조건, package 생성 시점, 테스트 gate와 개발 에이전트 파일 소유권을 정의한다.                                                  |
| [`p0-contract-decision-proposal.md`](p0-contract-decision-proposal.md)                           | D-01–D-18과 8개 제품 정책의 제안·대안·승인 근거를 보존하는 `APPROVED_DECISION_RECORD`다. 활성 계약은 `docs/spec/**`다.                                                       |
| [`post-p8-5-operations-contract-decision.md`](post-p8-5-operations-contract-decision.md)         | budget/quota/usage/billing 분리, 실패 UX, Backoffice, P9 전 단계와 거절 대안을 보존한다.                                                                                     |
| [`ui-ux-auto-job-analysis-redesign.md`](ui-ux-auto-job-analysis-redesign.md)                     | 2026-08-02 UI 감사, B2C 정보 구조·토큰, 공고 document view와 revision별 내구성 있는 자동 분석 구현 설계를 고정한다.                                                          |
| [`job-analysis-page-design-guide.html`](job-analysis-page-design-guide.html)                     | `/jobs/:jobId/analysis` 개편 구현 가이드다. 추가 토큰, 자체 아이콘 18종, API↔UI 매핑, 상태 분기, 데스크톱·모바일 시안, 차트 규격과 색각 검증 결과, 구현 체크리스트를 담는다. |
| [`cover-letter-openai-model-selection-report.md`](cover-letter-openai-model-selection-report.md) | 자기소개서 요청별 exact OpenAI model 선택의 공식 지원 근거, 안정성 경계와 생성 품질 개선 우선순위를 기록한다.                                                                |
| [`github-career-artifact-design.md`](github-career-artifact-design.md)                           | 공개 GitHub 경험 추출, canonical 중복 처리, 선택형 이력서 DOCX·포트폴리오 PPTX 생성의 목표 모듈·DB·API·페이지·보안·gate를 정의한다.                                          |
| [`progress.md`](progress.md)                                                                     | 설계 문서의 작성·검증 상태, P0 승인과 이후 구현 단계 상태를 추적한다.                                                                                                        |

현재 관리 대상 하위 디렉터리는 없다.

## 각 구성 요소의 역할

- 전체 시스템 설계는 다섯 기준 명세의 기능·DB·API·페이지·기술 연결과 충돌을 한 곳에서 추적한다.
- 구현 계획은 설계의 결정 게이트를 검증 가능한 수직 단계, 책임별 package와 역할별 파일 소유권으로 변환한다.
- P0 계약 결정 기록은 승인 전 충돌·권장안·대안과 최종 승인 근거를 보존하되 현재 구현 계약으로 사용하지 않는다.
- P8.5 이후 운영 결정 기록은 실제 호출 0인 구현 판정과 P8.5-V~P10-C 선행 관계를 보존하되 공개 목표는 활성 명세로 연결한다.
- UI/UX·자동 분석 재설계 메모는 구현 전 감사와 선택 근거를 보존하며, 실제 공개 상태·idempotency 계약은 활성 명세와 코드를 따른다.
- 공고 분석 페이지 디자인 가이드는 재설계 메모의 방향을 실제 구현 스펙으로 확정한 문서다. 화면 요소는 `JobAnalysisDetailDto`에 존재하는 필드로만 구성하며 새 공개 계약을 만들지 않는다.
- 자기소개서 모델 선택 보고서는 공식 OpenAI 문서와 현재 코드 구조를 교차 검토한 판단 및 품질 개선 우선순위를 보존하며 활성 계약은 `docs/spec/**`에 둔다.
- GitHub·Career Artifact 설계는 Gate 0–2의 GitHub vertical, Gate 3의 V28 Career Artifact Backend와 Gate 4의 feature-gated Frontend를 11개 WorkflowType, feature 활성 88 paths/118 operations·비활성 79 paths/107 operations에 연결한다. Gate 5 Private GitHub는 `PLANNED`다.
- 진행 문서는 설계가 실제 구현 완료를 의미하지 않음을 유지하고 문서 검증·후속 결정만 기록한다.

## 다른 디렉터리와의 의존 관계

- [`../spec/`](../spec/)이 제품 목표와 공개 계약의 원천이다.
- `backend/`와 `frontend/`는 계약이 승인된 뒤 이 설계의 모듈 경계와 구현 순서를 따른다.
- [`../../AGENTS.md`](../../AGENTS.md)와 [`../agent-rules/`](../agent-rules/)가 작업·위임·검증·추적 절차를 정의한다.
- 구현 상태는 각 코드 디렉터리의 `progress.md`가 원천이며 이 디렉터리는 구현 완료를 선언하지 않는다.

## 변경 시 주의사항

- 명세에 없는 공개 DTO, 상태, DB column, workflow 정책을 설계 문서에서 확정 사실로 만들지 않는다.
- 승인 전 명세 충돌 기록은 `문제/관련 명세/영향/권장 해결안`을 보존하고, 현재 연결은 `승인 반영`과 활성 명세 링크로 표시한다.
- 승인된 계약을 변경할 때는 먼저 `docs/spec/`의 영향 문서를 일관되게 갱신한 뒤 설계와 계획을 동기화한다.
- 구조·책임이 바뀌면 이 `index.md`를, 검증·결정 상태가 바뀌면 `progress.md`를 갱신한다.
- 같은 설명을 기준 명세와 이 디렉터리에 장문으로 복제하지 않고 상대 링크로 연결한다.

## 관련 규칙 및 문서

- [Codex 최상위 지침](../../AGENTS.md)
- [문서 영역 안내](../index.md)
- [기준 명세 안내](../spec/index.md)
- [공통 작업 절차](../agent-rules/workflow.md)
- [문서 추적 규칙](../agent-rules/documentation-tracking.md)
- [설계 진행 상황](progress.md)
