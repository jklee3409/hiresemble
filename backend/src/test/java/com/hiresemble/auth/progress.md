# Progress

## Overview

인증 HTTP·OpenAPI와 Gate 5 password/account deletion worker 회귀의 상위 경계를 관리한다.

## [2026-09-25] Session Summary (계정 삭제 worker 통합 테스트 시계 혼용 flaky 수정)

- What was done:
  - `AccountDeletionWorkerIntegrationTest`의 모든 fixture 시각(users·profile·document·GitHub connection insert, 만료 lease, outbox 완료 시각)을 DB `now()` 대신 application clock(`appNow()`)으로 바꿨다.
- Key decisions:
  - worker는 JVM clock으로 `updated_at`을 쓰므로 fixture도 같은 clock을 써야 한다. 운영 코드는 이미 app clock만 사용해 변경하지 않았다.
- Issues encountered:
  - Docker VM 시계가 Windows보다 조금이라도 앞서면 connection `created_at`(DB 시각) > `updated_at`(JVM 시각)이 되어 `github_app_connections_time_ck` 위반(`DataIntegrityViolationException`) → worker retry → connection이 `ACTIVE`로 남아 `githubUninstallMustReachSucceededAndExpiredTaskLeaseIsRecovered`가 간헐 실패했다. 이전 추정(`@Scheduled` 경쟁)이 아니었다.
- Validation:
  - 단독 3회 연속 6/6 통과, 전체 `check` 107 suites/733 tests 통과, 테스트 출력에 무결성 위반 retry 경고 0건.
- Next steps:
  - 다른 통합 테스트에도 DB `now()` fixture와 JVM clock worker를 섞는 곳이 있으면 같은 방식으로 정리한다.

## [2026-08-09] Session Summary (AUTH-004 회귀)

- What was done: password/session rotation, delete 202/WITHDRAWN, crash·lease·outbox terminal gate·final purge 테스트를 추가했다.
- Key decisions: 다른 사용자 격리와 같은 이메일 재가입 경계를 포함한다.
- Issues encountered: 없음.
- Validation: Auth/account focused test와 전체 check가 통과했다.
- Next steps: 새로운 owner table은 purge fixture에 추가한다.

## [2026-07-19] Session Summary (P2 포함 OpenAPI 테스트 경계 확장)

- What was done:
  - 인증 API 동작은 그대로 유지하고 생성 OpenAPI 검증 범위를 profile operation까지 확장했다.

- Key decisions:
  - profile 업무 동작 테스트는 별도 profile package가 소유한다.

- Issues encountered:
  - None

- Validation:
  - 인증 회귀와 exact 30-operation OpenAPI 테스트가 통과했다.

- Next steps:
  - 계정·Dashboard fixture는 실제 phase 전까지 추가하지 않는다.

## [2026-07-19] Session Summary (인증 통합 테스트 영역 구성)

- What was done:
  - 인증 API와 OpenAPI 전용 test package를 추가했다.

- Key decisions:
  - 두 사용자 격리는 user ID 조회 endpoint 없이 각각의 /auth/me Session으로 검증한다.

- Issues encountered:
  - None

- Validation:
  - 인증·OpenAPI tests가 전체 check에서 통과했다.

- Next steps:
  - 후속 인증 기능은 동일 API 계약 경계에서 별도 test로 추가한다.
