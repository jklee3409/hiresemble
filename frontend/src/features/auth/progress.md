# Progress

## Overview

Signup·login과 프로필 닉네임 변경의 client 입력 schema, Backend 비밀번호 조합·UTF-8 byte·표시 이름 계약을 관리한다.

## [2026-08-04] Session Summary (비밀번호 기술 용어 비노출)

- What was done:
  - Signup 길이 부족 오류를 요청된 10자 안내로 맞춰고 저장 경계 초과 오류는 기술 단위 없이 짧게 입력해 달라는 문장으로 바꾸었다.
- Key decisions:
  - `TextEncoder`와 72-byte client/server 경계는 검증 계약으로 유지하고 UI 안내에서만 기술 표현을 숨긴다.
- Issues encountered:
  - None.
- Validation:
  - Auth 집중 Vitest와 Frontend 전체 67 files/279 tests·build가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 credential blur·조합 검증)

- What was done:
  - 이메일·비밀번호·비밀번호 확인의 독립 field 검증과 10자·문자·숫자·특수문자·72바이트 정책을 추가했다.
- Key decisions:
  - 최초 오류는 blur 시 표시하고, 오류가 보인 field만 input 중 재검증해 유효해지면 즉시 해제한다.
- Issues encountered:
  - None.
- Validation:
  - Auth schema·flow 집중 테스트와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (비밀번호 실제 byte 규칙 안내)

- What was done:
  - 가입 비밀번호의 짧음·김 오류를 실제 UTF-8 10..72바이트 경계를 포함한 문구로 바꾸고 경계 회귀를 갱신했다.
- Key decisions:
  - 서버가 강제하지 않는 숫자·특수문자 조합은 Frontend도 필수화하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Auth validation·flow 집중 Vitest 18건과 전체 Frontend check 통과.
- Next steps:
  - Backend credential 계약이 바뀌면 안내·schema·경계 test를 함께 갱신한다.

## [2026-07-31] Session Summary (프로필 닉네임 validation 재사용)

- What was done:
  - Signup의 display-name schema를 공통화하고 프로필 저장에서 사용할 `validateDisplayNameForm`과 trim·blank·100자·제어 문자·경로 구분자 테스트를 추가했다.
- Key decisions:
  - Frontend 문구는 닉네임을 사용하되 공개 request field `displayName`은 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Auth validation tests와 Frontend 53 files/214 tests가 통과했다.
- Next steps:
  - Backend 표시 이름 제약이 바뀌면 Signup과 Profile 소비자를 함께 갱신한다.

## [2026-07-28] Session Summary (이메일 형식·닉네임 검증 명시)

- What was done:
  - 가입·로그인 공통 이메일에 공백·@·domain dot 형식을 명시적으로 검사하고 invalid table test를 추가했다.
  - signup의 `displayName` 사용자 문구를 닉네임으로 바꿨다.
- Key decisions:
  - Backend DTO field와 email normalization, password byte 계약은 유지했다.
- Issues encountered:
  - None.
- Validation:
  - Auth validation과 visible flow test, Frontend 전체 check가 통과했다.
- Next steps:
  - Backend email 계약이 바뀌면 공통 schema 경계를 함께 갱신한다.

## [2026-07-28] Session Summary (인증 Validation 소비자 문구 개선)

- What was done:
  - byte 경계 검사는 그대로 두고 비밀번호 짧음·김·불일치, 이메일 empty·format·duplicate 메시지를 자연스러운 행동 안내로 바꿨다.
- Key decisions:
  - 화면에는 byte·문자 수를 약속하지 않고 실제 contract는 TextEncoder와 경계 test로 계속 고정한다.
- Issues encountered:
  - None.
- Validation:
  - Unicode byte 경계와 visible message를 포함한 auth validation·flow test가 통과했다.
- Next steps:
  - Backend credential 계약이 바뀌면 경계 test와 소비자 메시지를 함께 검토한다.

## [2026-07-19] Session Summary (P1 인증 Form validation 구현)

- What was done:
  - email, displayName, consent, password confirm과 UTF-8 10..72·1..72 byte 규칙을 구현했다.

- Key decisions:
  - trim 가능한 이름·email만 Zod 결과로 정규화하고 password 공백은 보존한다.

- Issues encountered:
  - None

- Validation:
  - Form validation unit test가 경계값과 동의·확인을 통과했다.

- Next steps:
  - Backend request validation 변경 시 동일 경계 test를 함께 갱신한다.
