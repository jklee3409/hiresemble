# Progress

## Overview

P1 route에 대응하는 인증 Form, 보호 shell page와 전용 404를 관리한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (P1 인증 Page와 shell 구현)

- What was done:
  - signup/login Form, onboarding/dashboard shell, root 대기와 404 page를 구현했다.

- Key decisions:
  - signup은 항상 onboarding, login은 검증된 returnTo 또는 dashboard로 이동한다.

- Issues encountered:
  - server field 오류 시 disabled input에 focus할 수 없는 접근성 결함을 test로 발견해 제출 상태 해제 후 focus하도록 수정했다.

- Validation:
  - authFlow component test와 route shell·404 test, Frontend check가 통과했다.

- Next steps:
  - P2에서 onboarding 실제 Form과 API를 별도 범위로 구현한다.
