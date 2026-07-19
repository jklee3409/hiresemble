# Progress

## Overview

P1 users와 기본 user_profiles row의 JPA mapping 및 repository를 소유한다. 현재 P1 구현과 검증 상태만 기록한다.

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
