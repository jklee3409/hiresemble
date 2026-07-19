# Progress

## Overview

Spring Session SecurityContext에 저장할 최소 현재 사용자 principal을 정의한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (Session 인증 principal 구현)

- What was done:
  - UUID를 principal name으로 사용하고 email·displayName·role·status의 인증 snapshot을 직렬화 가능하게 구성했다.

- Key decisions:
  - Spring Session principal index에는 이메일 대신 사용자 UUID가 저장되도록 했다.

- Issues encountered:
  - None

- Validation:
  - 두 독립 Session의 /auth/me 격리와 spring_session principal_name test가 통과했다.

- Next steps:
  - 사용자 정보 변경 시 principal 갱신 정책을 해당 계정 기능 단계에서 확정한다.
