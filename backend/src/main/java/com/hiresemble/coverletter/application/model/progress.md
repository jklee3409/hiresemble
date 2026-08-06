# Progress

## Overview

P7 API·generation·verification에 필요한 최소 immutable application record가 구현됐다.

## [2026-08-06] Session Summary (선택 모델과 memo application model)

- What was done: generation question에 memo, generation·verification snapshot에 model을 추가하고 durable legacy constructor를 보존했다.
- Key decisions: model은 nullable legacy와 non-null v4 계약을 context builder에서 version별 검증한다.
- Issues encountered: None.
- Validation: compile·workflow·Backend 전체 테스트 통과.
- Next steps: 공개 DTO에는 필요한 catalog metadata 외 내부 snapshot을 노출하지 않는다.

## [2026-08-05] Session Summary (Cover Letter v3 model compatibility 확인)

- What was done:
  - 공개 application model은 변경하지 않고 v3 strict DTO와 truncation/selection metadata를 AI workflow 내부 record로 격리했다.
- Key decisions:
  - DB/API용 `VerifiedClaim`, `EvidenceUse`, `VerificationSnapshot` 의미는 유지하고 unsupported claim은 workflow persistence 전에 제거한다.
- Issues encountered:
  - None.
- Validation:
  - application·workflow·strict schema 테스트와 Backend check 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (Verification sibling answer snapshot)

- What was done:
  - `SiblingAnswerSummary`와 verification snapshot의 sibling current answer 목록을 추가했다.
- Key decisions:
  - owner-scoped current answer만 포함하고 Provider 전달 시 workflow가 질문·본문 길이를 다시 제한한다.
- Issues encountered:
  - None.
- Validation:
  - application/integration 및 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 immutable application model)

- What was done:
  - 자기소개서 detail·version·verification projection과 generation/verification snapshot·apply command를 추가했다.
- Key decisions:
  - historical evidence 작성 당시 정보와 현재 상태를 분리하고 AI 내부·storage 정보를 모델 경계에서 제외한다.
- Issues encountered:
  - 없음.
- Validation:
  - API schema·workflow structured output·Backend 전체 check가 통과했다.
- Next steps:
  - None.
