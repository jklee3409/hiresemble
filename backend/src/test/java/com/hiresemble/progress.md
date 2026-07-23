# Progress

## Overview

P1 인증·공통, P2 profile, P3 Agent Run·AI runtime과 P4 Document·migration·실제 E2E 테스트를 기능별로 구성한다.

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

## [2026-07-19] Session Summary (P4 Document·실제 E2E 테스트 추가)

- What was done:
  - migration·document·parser·storage·workflow·outbox와 Backend 주도 Browser E2E 테스트를 추가했다.
- Key decisions:
  - 기본 `test`와 비용이 큰 `p4BrowserE2eTest`를 분리하고 CI에서 둘 다 실행한다.
- Issues encountered:
  - random frontend port와 Fake price catalog로 격리 실행 안정성을 보정했다.
- Validation:
  - 기본 30 suites/287 tests와 실제 Playwright 4/4가 통과했다.
- Next steps:
  - GitHub-hosted runner 결과는 push/PR 뒤 확인한다.

## [2026-07-19] Session Summary (P3 Agent Run·AI 테스트 추가)

- What was done:
  - Agent Run domain/runtime/API/SSE, AI registry·router·orchestrator와 V4 migration tests를 추가했다.
  - 공용 PostgreSQL cleanup을 P3 table까지 확장했다.

- Key decisions:
  - 실제 provider와 기존 개발 DB 대신 Testcontainers를 사용한다.

- Issues encountered:
  - P3 owner FK에 맞춰 공통 idempotency fixture를 확장했다.

- Validation:
  - 21 suites/243 tests가 모두 통과했다.

- Next steps:
  - 실제 typed resource integration은 P4 이후 추가한다.

## [2026-07-19] Session Summary (P2 프로필·migration 테스트 추가)

- What was done:
  - profile api/domain과 V3 migration 테스트를 추가하고 공유 cleanup을 P2 table까지 확장했다.

- Key decisions:
  - AC-02 HTTP·transaction은 실제 PostgreSQL에서, 순수 완료도·validation은 domain 단위로 검증한다.

- Issues encountered:
  - None

- Validation:
  - 9개 test class, 54개 test가 failure·error·skip 0으로 통과했다.

- Next steps:
  - P4 문서 경계는 실제 aggregate 구현 뒤 별도 테스트로 추가한다.

## [2026-07-19] Session Summary (P1 기능별 백엔드 테스트 구성)

- What was done:
  - auth, common, migration, support 테스트 영역을 추가했다.

- Key decisions:
  - 공유 context는 support에 제한하고 각 계약 assertion은 담당 기능 package에 둔다.

- Issues encountered:
  - None

- Validation:
  - Backend 전체 check와 Testcontainers migration 검증이 통과했다.

- Next steps:
  - P2 테스트도 사용자 소유권과 cross-user 실패를 기능별로 추가한다.
