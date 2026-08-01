# Progress

## Overview

Backend P1~P8 OpenAPI와 자동 분석 projection에 일치하는 TypeScript DTO, Axios·CSRF와 typed 오류 처리를 소유한다.

## [2026-08-02] Session Summary (공고 자동 분석 typed projection)

- What was done:
  - `automaticAnalysis` state·BALANCED quality·run ID·safe error를 Job detail Zod·TypeScript 계약과 fixtures에 추가했다.
- Key decisions:
  - 기존 `AiQualityMode`와 분석 request는 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Job contract/API tests와 Frontend typecheck 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (대외활동·소재 batch·파일명 typed 계약)

- What was done:
  - Activity DTO/API, ACTIVITY evidence, PENDING/batch review와 document originalFilename 계약을 Zod·typed client에 추가했다.
- Key decisions:
  - Frontend가 AI 추출 경험을 활동으로 필터링해 재해석하지 않고 전용 API만 사용한다.
- Issues encountered:
  - None.
- Validation:
  - shared API tests와 Frontend 전체 typecheck 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 typed API·Zod 계약)

- What was done:
  - P8 11개 operation의 DTO/enum/Zod, filter mapping, Idempotency-Key·CSRF mutation client를 추가했다.
- Key decisions:
  - provider rank·internal tier/status는 공개 type에 넣지 않고 모든 상한·nullability를 OpenAPI와 맞춘다.
- Issues encountered:
  - 과거 Job contract fixture의 `interviewQuestionSetId` 비활성 기대를 현재 projection으로 갱신했다.
- Validation:
  - Interview API/contract tests와 auth user 전환 cache key 회귀가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (최종 학력 transport 계약)

- What was done:
  - Education request·response type에 `EducationLevel`을 추가하고 request `isPrimary`를 제거했다.
- Key decisions:
  - response의 `isPrimary`는 read-only server projection으로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - profile API contract test, typecheck와 전체 Frontend check가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (Agent Run history delete client)

- What was done:
  - 개별 DELETE와 `{agentRunIds}` bulk-delete POST client를 추가했다.
- Key decisions:
  - 기존 CSRF interceptor와 void 204 transport 계약을 재사용한다.
- Issues encountered:
  - None.
- Validation:
  - Agent Run API client test와 Frontend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (닉네임 변경 request·client)

- What was done:
  - `DisplayNameUpdateRequest`와 CSRF bootstrap 뒤 `/account/display-name`을 호출하는 typed PATCH client를 추가했다.
- Key decisions:
  - 성공 응답은 공통 envelope 없이 Backend의 직접 `CurrentUserDto`를 사용한다.
- Issues encountered:
  - None.
- Validation:
  - Auth API 소비 store test와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 verification suggestion 경계 정합)

- What was done:
  - `VerificationDto.suggestions` Zod schema를 최대 20개·항목 1~1000자로 활성 API 계약과 일치시켰다.
- Key decisions:
  - 20개·1000자는 허용하고 21개·1001자는 server response parsing 단계에서 거부한다.
- Issues encountered:
  - 1차 validator에서 기존 client가 100개·2000자를 허용해 Backend 공개 계약보다 느슨한 점을 확인했다.
- Validation:
  - 20/21개·1000/1001자 contract tests와 전체 Frontend 53 files/211 tests가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Cover Letter typed contract·client)

- What was done:
  - canonical 상태·source·verification·issue, summary/detail/question/version DTO의 strict Zod schema와 공개 API 17개 client를 추가했다.
- Key decisions:
  - client는 source/createdBy를 save request에 보내지 않고 Idempotency-Key·cover/question/current version CAS를 명시한다.
- Issues encountered:
  - 없음.
- Validation:
  - contract/client invalid response·header/body tests와 Frontend 전체 211 tests가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 Job Analysis typed contract·client)

- What was done:
  - canonical Eligibility·OutdatedReason·FitCriterionCategory·MatchLevel과 summary/detail/request schema, 분석 3개 client를 추가했다.
- Key decisions:
  - Backend strict DTO와 `ECONOMY|BALANCED` allowlist를 그대로 사용하고 provider/model/hash는 공개 type에 포함하지 않는다.
  - 분석 당시 provenance는 서버 불변식이 보장하므로 detail parser는 이후 변경된 canonical evidence 상태를 수용하고 UI가 현재 상태를 별도로 설명한다.
- Issues encountered:
  - 1차 validator가 `REJECTED`·`SOURCE_DELETED` historical ref를 invalid server response로 거부하는 문제를 확인해 과도한 refinement를 제거했다.
- Validation:
  - historical evidence contract/client request·invalid response tests와 Frontend 전체 169 tests가 통과했다.
- Next steps:
  - P7 계약은 Backend OpenAPI 구현 뒤 추가한다.

## [2026-07-28] Session Summary (Agent Run 오류 사용자 문구 정리)

- What was done:
  - invalid Agent Run server response의 사용자 메시지를 분석 기록 표현으로 변경했다.
- Key decisions:
  - schema, error code와 transport 계약은 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - API client tests와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (P5 Job typed contract·client 구현)

- What was done:
  - Job enum·DTO strict Zod schema와 공개 API 7개 typed client·idempotency header를 추가했다.
- Key decisions:
  - P5 projection은 `null`, `false`, 빈 tuple과 `0`만 허용하고 P6 분석 DTO를 정의하지 않는다.
- Issues encountered:
  - 초기 P6 `Eligibility`·analysis schema를 validator 보정에서 제거했다.
- Validation:
  - 잘못된 server response 거부, API request/status와 전체 Frontend 122 tests가 통과했다.
- Next steps:
  - P6 OpenAPI가 구현된 뒤 분석 계약을 별도 추가한다.

## [2026-07-19] Session Summary (P4 Document typed contract·client 구현)

- What was done:
  - 공개 DTO·enum Zod parity와 multipart·idempotency·version·download client를 추가했다.
- Key decisions:
  - storage key·checksum·parser·embedding·provider 내부 field는 type에도 노출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - DTO parity·multipart header·오류 parsing 테스트와 Backend OpenAPI 43/30이 통과했다.
- Next steps:
  - P5 이후 client를 미리 추가하지 않는다.

## [2026-07-19] Session Summary (P3 Agent Run typed contract·client 구현)

- What was done:
  - Run·Step enum, nullable DTO와 6종 SSE payload를 Zod로 고정했다.
  - 목록·상세, retry Idempotency-Key와 cancel stateVersion API client를 추가했다.

- Key decisions:
  - unknown server field는 runtime parsing에서 제거해 내부 field가 UI로 전파되지 않는다.

- Issues encountered:
  - None.

- Validation:
  - enum parity·nullability·repeatable query·mutation header/body tests가 통과했다.

- Next steps:
  - 공개 Run 생성 API는 만들지 않고 domain workflow가 내부 launcher를 사용한다.

## [2026-07-19] Session Summary (P2 profile typed client 구현)

- What was done:
  - 프로필 enum·request·response·page type과 25개 profile/evidence API consumer를 추가했다.
  - HTTP client에 typed PUT·PATCH·DELETE를 추가하고 기존 Cookie·CSRF·401 흐름을 재사용했다.

- Key decisions:
  - Backend 직접 DTO와 enum·nullability를 그대로 반영하고 성공 envelope를 만들지 않는다.

- Issues encountered:
  - None

- Validation:
  - profile API path·method·payload 테스트와 TypeScript 전체 typecheck가 통과했다.

- Next steps:
  - document 연결 성공 type은 P4 Backend 계약 구현 뒤 활성화한다.

## [2026-07-19] Session Summary (P1 typed Axios·CSRF client 구현)

- What was done:
  - baseURL /api/v1, withCredentials, 동적 CSRF와 1회 복구, 공통 오류 normalization을 구현했다.

- Key decisions:
  - signup/login 성공 응답의 새 csrf를 즉시 교체하고 409 mutation은 자동 재시도하지 않는다.

- Issues encountered:
  - None

- Validation:
  - HTTP client unit test와 TypeScript·production build가 통과했다.

- Next steps:
  - Backend OpenAPI 변경 시 contracts와 runtime guard·test를 함께 갱신한다.
