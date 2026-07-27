# Progress

## Overview

Signup·login의 client 입력 schema와 Backend UTF-8 byte 계약을 관리한다. 현재 P1 구현과 검증 상태만 기록한다.

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
