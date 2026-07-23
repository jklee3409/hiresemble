# Progress

## Overview

P1~P3 운영 코드와 분리된 JUnit·MockMvc·Testcontainers 검증 source set을 관리한다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - 운영 Java 158개와 package-private 결합 테스트 4개의 책임별 이동 및 상위 source tree 문서 연결을 반영했다.

- Key decisions:
  - 파일 경로, package·import와 필요한 FQCN만 변경하고 API·DB·workflow·접근 제한자는 유지했다.
  - 실제 파일이 있는 책임 package만 생성하고 P5 이후 기능과 빈 디렉터리는 만들지 않았다.

- Issues encountered:
  - 한국어 literal/comment 19개의 중간 인코딩 손상을 발견해 HEAD UTF-8 원문을 복원하고 byte-safe 본문 대조로 재확인했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P3 Agent Run·AI test source 확장)

- What was done:
  - Agent Run·AI runtime Java tests와 test-only Fake prompt resource를 추가했다.

- Key decisions:
  - production endpoint·Fake bean 없이 내부 port와 Testcontainers를 사용한다.

- Issues encountered:
  - None.

- Validation:
  - Backend 전체 243 tests가 통과했다.

- Next steps:
  - 후속 external adapter는 Fake/WireMock으로 검증한다.

## [2026-07-19] Session Summary (P2 profile·migration 테스트 확장)

- What was done:
  - P2 domain·API·migration 테스트와 공유 table cleanup을 추가했다.

- Key decisions:
  - 운영 DB·유료 외부 provider 없이 PostgreSQL Testcontainers를 사용한다.

- Issues encountered:
  - None

- Validation:
  - Backend check 54개 test가 통과했다.

- Next steps:
  - 후속 phase 테스트는 해당 운영 code와 함께 추가한다.

## [2026-07-19] Session Summary (P1 백엔드 테스트 source set 구성)

- What was done:
  - 인증, 공통 오류, idempotency와 migration 테스트 계층을 처음 추가했다.

- Key decisions:
  - Repository 통합은 PostgreSQL 18/pgvector Testcontainers를 사용하고 Spring Session·Flyway·JPA validate를 함께 검증한다.

- Issues encountered:
  - 초기 Jackson 2/3 bean 차이와 CSRF 형식, JDBC 시간 binding 실패를 테스트로 발견하고 production 구현을 보정했다.

- Validation:
  - gradlew check에서 26 tests, 0 failures·errors·skips로 통과했다.

- Next steps:
  - 새 기능은 가장 가까운 단위 test와 PostgreSQL 경계 test를 함께 추가한다.
