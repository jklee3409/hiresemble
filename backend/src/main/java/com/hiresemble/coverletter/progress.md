# Progress

## Overview

P7 자기소개서 생성·문항 관리·immutable 답변 version·근거 provenance·검증·최종화·보관과 AI application port가 final-source actual 및 최종 validator `PASS`로 완료됐다.

## [2026-08-05] Session Summary (자기소개서 작성 품질 v2 application 연결)

- What was done:
  - 신규 생성·검증 launch version을 v2로 전환하고 explicit verification snapshot에 owner-scoped sibling current answer summary를 포함했다.
  - v2 verification snapshot hash에 sibling answer identity·character count·본문 hash를 포함해 변경 시 stale Context를 거부한다. durable v1은 기존 hash 계산을 그대로 유지한다.
- Key decisions:
  - current answer가 있으면 Writer에 실제 bounded plain text와 version ID를 전달해 `AI_REVISED` 의미를 맞춘다.
  - 공개 API/DB/Frontend와 immutable version/CAS/finalize 계약은 변경하지 않는다.
- Issues encountered:
  - 정보 부족 사용자 조치와 resume UI가 완결되어 있지 않아 새 WAITING_USER 상태는 추가하지 않았다.
- Validation:
  - Cover Letter application/integration 회귀와 Backend 전체 check가 통과했다.
- Next steps:
  - 근거 보완 route와 retry/resume UX를 별도 계약으로 정의한다.

## [2026-07-30] Session Summary (P7 coverletter 최종 판정)

- What was done:
  - 최종 read-only validator가 domain lifecycle, owner scope, immutable answer/verification, provenance와 AI port 경계를 재검증했다.
- Key decisions:
  - P7 공개 계약과 V8을 완료 기준선으로 고정하고 P8 기능은 이 package에 선행 추가하지 않는다.
- Issues encountered:
  - 새 finding 없음.
- Validation:
  - Validator `PASS`, Backend 54 suites/380 tests와 P7 actual wrapper·DB assertions PASS.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 verification 공개 계약 보정)

- What was done:
  - `VerificationDto.suggestions` OpenAPI array/item 제약을 persistence command와 같은 최대 20개·항목 1~1000자로 고정했다.
- Key decisions:
  - 공개 문서가 런타임 검증보다 느슨하지 않도록 DTO annotation과 생성 OpenAPI 회귀 assertion을 함께 유지한다.
- Issues encountered:
  - 1차 validator가 런타임 command와 OpenAPI 표현의 불일치를 MAJOR로 식별했다.
- Validation:
  - OpenAPI contract test와 Backend 전체 54 suites/380 tests, P7 actual wrapper·DB assertion이 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 기다린다.

## [2026-07-30] Session Summary (P7 자기소개서 Backend 수직 기능)

- What was done:
  - active cardinality, 문항 CRUD·전체 정렬, TipTap canonicalization, 명시적 저장·복원, immutable 검증, WARNING acknowledgement, 최종화·보관·복구를 구현했다.
  - 생성·검증 Run 접수와 cover letter·answer version resource link, owner-scoped generation/verification snapshot 및 apply port를 연결했다.
- Key decisions:
  - server가 source·글자 수·verification freshness를 결정하고, 현재 답변 교체와 FINALIZED→DRAFT를 원자적으로 적용한다.
  - 과거 provenance는 근거가 거절되거나 원본이 삭제돼도 유지하되 새 생성·검증 context에서는 제외한다.
- Issues encountered:
  - 실제 Browser E2E에서 숫자 input의 Vue number 값이 문자열 전용 parser에 들어가는 오류를 발견해 문자열·숫자 입력을 모두 처리하도록 보정했다.
  - verification workflow의 provenance claim text는 ephemeral handoff로만 유지하고 durable checkpoint에는 hash·ID·count만 저장하도록 통합 감사에서 보강했다.
- Validation:
  - Backend `check` 54 suites/377 tests, P7 actual Chromium 1/1과 wrapper DB assertions, 최종 source P6 회귀 Chromium 2/2가 통과했다.
- Next steps:
  - 독립 validator 판정 후 P7 완료 상태를 확정한다.
