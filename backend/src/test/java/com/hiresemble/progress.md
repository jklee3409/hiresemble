# Progress

## Overview

P1 인증·공통부터 P8 Interview·Dashboard, Phase 1 GitHub Backend·migration·actual E2E 테스트를 기능별로 구성한다.

## [2026-08-07] Session Summary (GitHub Source test package 추가)

- What was done: GitHub source, workflow와 V27 migration 테스트 package를 상위 test 구조에 연결했다.
- Key decisions: 운영 network와 유료 provider는 모든 자동 fixture에서 차단한다.
- Issues encountered: None.
- Validation: 관련 test class compile 및 focused execution 통과.
- Next steps: Gate 2 Frontend E2E는 별도 추가한다.

## [2026-08-02] Session Summary (Dashboard·Career Guide Backend 회귀)

- What was done:
  - owner 집계·서울 월 경계·게시 가이드 통합, V17 upgrade와 OpenAPI 회귀를 추가했다.
- Key decisions:
  - 실제 PostgreSQL에서 두 사용자와 UTC/서울 경계 fixture를 검증한다.
- Issues encountered:
  - Instant fixture binding을 `OffsetDateTime`으로 보정했다.
- Validation:
  - Backend 전체 73 suites/498 tests 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 Backend·AI·actual 검증)

- What was done:
  - Interview migration/API/workflow/Tavily와 P8 Browser wrapper 테스트를 추가하고 P7/P6 actual 회귀를 재실행했다.
- Key decisions:
  - 일반 `test`에서는 actual wrapper를 제외하고 명시적 `p8BrowserE2eTest`에서만 격리 browser stack을 실행한다.
- Issues encountered:
  - JVM과 PostgreSQL의 수 ms clock 차이를 terminal fixture의 `GREATEST(now(), queued_at)`로 제한 보정했다.
- Validation:
  - Backend 61 suites/407 tests, actual P8/P7/P6 Java wrapper 1/1·1/1·1/1과 Chromium 1/1·1/1·2/2 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 validator 보정 회귀·final-source E2E)

- What was done:
  - verification suggestion 20/21개·1000/1001자, aggregate 합산 20개 제한과 OpenAPI item 제약 회귀를 추가했다.
  - 실제 문항 409 비교·재적용이 추가된 P7 wrapper와 P6 회귀 wrapper를 final source에서 재실행했다.
- Key decisions:
  - 1차 validator finding을 재현하는 경계는 domain apply/persist 전에 검증하고 Browser 충돌은 실제 nested resource CAS로 검증한다.
- Issues encountered:
  - 없음.
- Validation:
  - Backend 54 suites/380 tests, failure·error·skip 0.
  - P7 Chromium 1/1·DB assertions와 P6 Chromium 2/2·DB assertions가 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 반영한다.

## [2026-07-30] Session Summary (P7 Backend·workflow·actual E2E 검증)

- What was done:
  - Cover Letter API/application/domain/migration/finalization, AI workflow, Agent Run resource와 실제 Browser wrapper 테스트를 추가했다.
- Key decisions:
  - PostgreSQL/MinIO/Fake AI/Vue/Chromium을 wrapper가 격리 실행하고 실제 유료 provider는 사용하지 않는다.
- Issues encountered:
  - 실제 form parser 결함과 UI 대기 race를 actual E2E에서 발견해 frontend 회귀와 명시적 postcondition으로 보정했다.
- Validation:
  - Backend 54 suites/380 tests, P7 Chromium 1/1·DB assertions와 P6 회귀 Chromium 2/2가 통과했다.
- Next steps:
  - 독립 validator 판정을 반영한다.

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
