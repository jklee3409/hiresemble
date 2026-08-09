# Progress

## Overview

Spring Session SecurityContext 최소 principal과 WITHDRAWN 사용자의 즉시 접근 차단을 정의한다.

## [2026-08-09] Session Summary (WITHDRAWN request 차단)

- What was done: DB user 상태를 확인해 WITHDRAWN principal의 보호 요청을 인증 실패로 전환하는 filter를 추가했다.
- Key decisions: 탈퇴 직후 같은 Session·다른 Session·download ticket 발급을 모두 차단한다.
- Issues encountered: 없음.
- Validation: Auth integration과 전체 check가 통과했다.
- Next steps: WITHDRAWN을 로그인 가능한 상태로 되돌리지 않는다.

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
