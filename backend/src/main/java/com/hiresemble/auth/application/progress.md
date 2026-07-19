# Progress

## Overview

가입·로그인·로그아웃 transaction과 SecurityContext·CSRF Session 전이를 조정하고 기본 profile·AI preference 등록을 각 service에 위임한다.

## [2026-07-19] Session Summary (가입 transaction의 AI preference 등록 위임)

- What was done:
  - `AuthService`가 사용자·profile 생성 뒤 `AiPreferenceRegistrationService`를 호출한다.

- Key decisions:
  - preference 생성과 Session 저장은 기존 가입 transaction 원자성을 공유한다.

- Issues encountered:
  - None.

- Validation:
  - preference 실패 trigger에서 user/profile/session/preference 잔여 row가 없음을 검증했다.

- Next steps:
  - 사용자 preference 조회·수정 API는 P10 설정 단계에 남긴다.

## [2026-07-19] Session Summary (가입 transaction의 profile 등록 위임)

- What was done:
  - `AuthService`가 사용자 저장 뒤 `ProfileRegistrationService`를 호출하도록 연결했다.

- Key decisions:
  - 사용자·기본 프로필·Session SQL은 기존과 같이 하나의 가입 transaction에서 원자적으로 처리한다.

- Issues encountered:
  - None

- Validation:
  - 가입 성공·중복·profile/Session 실패 rollback 회귀가 Backend check에서 통과했다.

- Next steps:
  - 프로필 세부 use case를 auth service로 되돌려 중복하지 않는다.

## [2026-07-19] Session Summary (인증 use case와 Session 전이 구현)

- What was done:
  - 가입 user+profile+Session transaction, 정규화 로그인, Session rotation과 logout 무효화를 구현했다.

- Key decisions:
  - 미등록 이메일과 잘못된 비밀번호를 같은 INVALID_CREDENTIALS로 처리하고 BCrypt dummy 비교를 수행한다.
  - 인증 Session 저장 실패는 현재 SecurityContext와 Session을 폐기해 request-end 재저장을 허용하지 않는다.

- Issues encountered:
  - profile insert 실패를 test trigger로 유발해 가입 user row까지 rollback되는지 검증했다.
  - Spring Session 기본 request-end 저장이 JPA commit과 분리돼 공식 transaction extension과 즉시 flush로 보정했다.

- Validation:
  - 가입·로그인·로그아웃·me, Session persistence 실패와 deferred commit rollback 통합 테스트가 통과했다.

- Next steps:
  - 후속 credential 변경 시 다른 Session 폐기 계약을 별도 승인 범위에서 구현한다.
