# Progress

## Overview

P1 users credential·상태의 JPA mapping과 repository만 소유한다. `user_profiles` 영속성은 P2 profile 영역으로 이동했다.

## [2026-07-19] Session Summary (user_profiles 영속성 소유권 이동)

- What was done:
  - auth의 `UserProfileEntity`와 `UserProfileRepository`를 제거하고 사용자 entity·repository만 남겼다.

- Key decisions:
  - 동일 table을 두 영역이 중복 mapping하지 않고 profile JDBC store를 단일 소유자로 사용한다.

- Issues encountered:
  - None

- Validation:
  - JPA schema validate와 가입·Session 통합 테스트가 통과했다.

- Next steps:
  - 계정 관련 table이 실제 추가될 때만 이 영역을 확장한다.

## [2026-07-19] Session Summary (P1 사용자·기본 프로필 JPA mapping 구현)

- What was done:
  - users와 user_profiles entity·repository를 추가하고 가입 transaction에 연결했다.

- Key decisions:
  - 프로필 상세 CRUD 없이 빈 배열 기본값을 가진 사용자당 하나의 기본 row만 생성한다.

- Issues encountered:
  - 사용자 unique 위반만 이메일 중복으로 매핑하고 profile 실패는 내부 오류와 transaction rollback으로 유지했다.

- Validation:
  - Testcontainers PostgreSQL에서 JPA validate, BCrypt 평문 비저장과 user/profile 원자성을 검증했다.

- Next steps:
  - P2 프로필 입력 제약은 새 migration과 전용 application 경계에서 추가한다.
