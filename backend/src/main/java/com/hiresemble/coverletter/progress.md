# Progress

## Overview

P7 자기소개서 생성·문항 관리·immutable 답변 version·근거 provenance·검증·최종화·보관과 AI application port가 final-source actual 및 최종 validator `PASS`로 완료됐다.

## [2026-08-06] Session Summary (자기소개서 AI 비용 전역 정책 통합)

- What was done: 생성·검증별 고정 비용 properties와 환경 변수를 제거하고 exact model별 호출 비용을 공통 price catalog estimator가 예약하도록 연결했다.
- Key decisions: 사용자 model 선택은 유지하되 model 가격에 따른 별도 분야 상한은 두지 않고 전역 일일 USD 10만 적용한다.
- Issues encountered: None.
- Validation: 메인·테스트 소스 컴파일 통과.
- Next steps: model별 실제 품질·원가 관측은 budget과 분리된 backoffice 집계로 제공한다.

## [2026-08-06] Session Summary (자기소개서 모델 직접 선택과 memo 반영)

- What was done: 품질 모드 공개 계약을 제거하고 model catalog·요청별 exact model·memo-aware v4 generation/verification을 구현했다.
- Key decisions: 신규 요청은 서버 catalog의 모델만 허용하고 선택값을 Run input에 고정하며 기존 Run 호환성은 유지한다.
- Issues encountered: None.
- Validation: cover letter workflow·P7 E2E와 Backend 전체 `check` 578 tests 통과.
- Next steps: 운영 provider smoke test와 품질 golden set 평가를 수행한다.

## [2026-08-05] Session Summary (플래티어 단일 문항 AI 답변 실제 검증)

- What was done:
  - 실제 플래티어 공고 분석 context와 유사 백엔드 경력 VERIFIED 근거를 사용해 단일 자기소개서 문항 생성을 HTTP 사용자 흐름으로 검증했다.
- Key decisions:
  - 실제 사용자의 password·Session을 읽지 않고 `example.com` 테스트 계정과 owner-scoped 복제 context를 사용했다.
- Issues encountered:
  - 성공 전 provider timeout과 strict output 실패가 있었고, 임시 계정은 불변 provenance 정책으로 물리 삭제할 수 없었다.
- Validation:
  - 최종 Run `SUCCEEDED`, progress 100%, answered question 1, `AI_GENERATED` 752자, `PLAN_QUESTIONS` v5 attempt 1 성공.
- Next steps:
  - 실제 사용자의 기존 terminal 실패 Run을 retry한다.

## [2026-08-05] Session Summary (자기소개서 v3 provenance·검증 정합성)

- What was done:
  - 신규 Agent Run을 generation/verification v3로 접수하고 USER_EDITED exact excerpt provenance 계승과 v3 검증 persistence를 연결했다.
- Key decisions:
  - 공개 API·DB schema·V22 migration은 유지하며 v1/v2 retry는 저장된 exact version으로 실행한다.
- Issues encountered:
  - P7은 selector 모호성 수정 후 최종 미재실행 상태다.
- Validation:
  - application 통합 테스트와 Backend 전체 check 통과, P7 최종 통과 미검증.
- Next steps:
  - P7 추가 1회가 필요하다.

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
