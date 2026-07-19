# Progress

## Overview

가입·로그인·로그아웃 transaction과 SecurityContext·CSRF Session 전이를 조정한다. 현재 P1 구현과 검증 상태만 기록한다.

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
