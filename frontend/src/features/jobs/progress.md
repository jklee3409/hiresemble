# Progress

## Overview

P5 Job filter·mutation·stream·version conflict, 안전한 공고 문서 parser와 P6 자동 분석 journey·presentation·terminal invalidation이 구현됐다.

## [2026-08-02] Session Summary (공고 criterion 출처 사용자용 치환)

- What was done:
  - JSONPath·`untrustedJobPosting`·`descriptionText`를 포함한 sourceLocation을 `공고 본문`으로 표시하는 presentation helper를 추가했다.
- Key decisions:
  - 정상 한국어 sourceLocation은 그대로 표시하고 null·blank는 노출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 과거 내부 경로 fixture가 원문 없이 `공고 본문`으로 표시되는 page 테스트와 Frontend 전체 67 files/267 tests 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (분석 journey 단계 간격 균등화)

- What was done:
  - Desktop 4단계 grid를 동일 폭 column에서 내용 기반 column과 균등한 track 사이 여백으로 변경했다.
- Key decisions:
  - 긴 진행 문구 nowrap과 중간 2열·mobile 1열 layout은 그대로 유지했다.
- Issues encountered:
  - None.
- Validation:
  - Chromium에서 세 단계 사이 실제 bounding-box gap 편차 1px 이하와 desktop/mobile overflow 없음 확인.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 분석 고정 quality·실패 안내)

- What was done:
  - 분석 방식 label·selector를 제거하고 자동·재분석 요청을 `BALANCED`로 고정했다.
  - structured output, 데이터 부족, timeout, Provider, 취소와 기타 실패를 사용자 행동 중심 title·description으로 변환했다.
  - 진행 단계의 긴 문구를 한 줄로 유지하고 중간 viewport에서는 2열로 전환했다.
- Key decisions:
  - 공개 DTO enum은 향후 모델 선택 확장을 위해 유지하며 현재 presentation에서만 품질 모드를 숨긴다.
- Issues encountered:
  - None.
- Validation:
  - JobAnalysis unit와 Desktop·mobile Chromium overflow 회귀, Frontend 표준 check 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 문서 view·자동 분석 journey)

- What was done:
  - plain text를 heading·문단·목록·URL node로만 바꾸는 deterministic parser, 읽기 전용 document component와 사용자 중심 4단계 journey를 추가했다.
- Key decisions:
  - 원문을 변경하거나 `v-html`로 주입하지 않고 확신이 낮으면 일반 문단으로 fallback한다.
- Issues encountered:
  - 긴 본문 collapse fixture 길이를 실제 threshold보다 충분히 크게 조정했다.
- Validation:
  - parser·document component unit tests와 모바일 장문 Browser 캡처 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (v3 retry·상태 presentation 회귀)

- What was done:
  - v3 내부 변경 뒤 기존 Job 상태·retry·manual CTA presentation 계약을 검증했다.
- Key decisions:
  - 새 공개 enum/UI control은 추가하지 않는다.
- Issues encountered:
  - None.

## [2026-08-01] Session Summary (이미지형 공고 자동 처리 presentation)

- Validation:
  - Frontend 61 files/243 tests와 P5 Chromium 5/5 통과.
- Next steps:
  - None.

- What was done:
  - 자동 처리·manual fallback 문구와 Job extraction 상태 presentation을 보정했다.
- Key decisions:
  - OCR 선택 UI 없이 NEEDS_MANUAL_INPUT은 직접 입력, FAILED는 재시도와 직접 입력을 구분한다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 61 files/243 tests와 build 통과.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 Job Analysis query·presentation)

- What was done:
  - user-scoped latest/history query key, 분석 접수 mutation과 terminal Run 뒤 Job·analysis invalidation을 추가했다.
  - canonical Eligibility·Match·criterion·OUTDATED·quality label을 정의했다.
  - 분석 당시 근거와 현재 승인·삭제 상태를 구분하는 presentation helper를 추가했다.
- Key decisions:
  - `ECONOMY|BALANCED`만 노출하고 점수와 지원 가능 여부를 하나의 badge 의미로 합치지 않는다.
- Issues encountered:
  - reload 뒤 active analysis Run 확인 실패 시 중복 유료 요청 방지를 위해 command를 잠그는 상태를 추가했다.
  - 1차 validator의 historical evidence rendering finding을 보정했다.
- Validation:
  - query·stream·historical evidence page unit test, Frontend 169 tests와 Chromium P6 fixture가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (공고 분석 monitor 용어 정리)

- What was done:
  - Job Run monitor의 `AI 작업` 노출을 공고 분석·분석 상태 표현으로 바꿨다.
- Key decisions:
  - Job extraction과 Agent Run 상태 계약은 유지했다.
- Issues encountered:
  - None.
- Validation:
  - 관련 component tests와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (지원 상태·공고 불러오기 UX Writing 적용)

- What was done:
  - `업무 상태`를 `지원 상태`, extraction을 `공고 불러오기`로 표현하고 manual·failed·conflict guidance를 다음 행동 중심으로 바꿨다.
- Key decisions:
  - JobStatus와 JobExtractionStatus, 201/202·idempotency·retry·manual 입력 의미는 그대로 분리 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Job presentation/conflict/page test와 관련 fixture E2E가 통과했다.
- Next steps:
  - P6 분석 label·route는 계약 구현 전 추가하지 않는다.

## [2026-07-27] Session Summary (Job 상태·충돌 UI 개선)

- What was done:
  - Run monitor와 version conflict 비교·재적용 panel의 정보 계층, action priority와 responsive 표현을 개선했다.
- Key decisions:
  - 업무 상태와 extraction 상태를 별도 label로 유지하고 NEEDS_MANUAL_INPUT·FAILED action 의미를 바꾸지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Job component/page test와 전체 Frontend check가 통과했다.
- Next steps:
  - P6 analysis action과 DTO는 계약 구현 전 추가하지 않는다.

## [2026-07-27] Session Summary (P5 Jobs frontend feature 구현)

- What was done:
  - URL query canonicalization, 생성/수정/상태/retry/delete mutation과 Job Run monitor를 추가했다.
- Key decisions:
  - NEEDS_MANUAL_INPUT은 수동 입력만 강조하고 retry는 FAILED에만 제공한다.
- Issues encountered:
  - 초기 P6 분석 DTO 선행 구현을 validator 보정에서 제거하고 P5 projection을 strict null·false·빈 값으로 제한했다.
- Validation:
  - Frontend 32 files/122 tests와 P5 Browser E2E 5/5가 통과했다.
- Next steps:
  - P6에서 분석 feature를 별도 계약으로 추가한다.
