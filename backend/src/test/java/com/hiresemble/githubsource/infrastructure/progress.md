# Progress

## Overview

Coverage targets public-only GitHub input and deterministic local fixtures. Detailed
session results are recorded by the root agent after integrated verification.

## [2026-08-10] Session Summary (GitHub 후보 상한과 삭제 재수집 회귀)

- What was done: provenance 후보 3개 상한과 삭제 경험의 목록·상세·raw evidence 비노출, 동일 claim 재적용 no-op을 PostgreSQL에서 검증했다.
- Key decisions: 삭제 source link를 유지해 suppression identity로 사용한다.
- Issues encountered: 최초 SOURCE_DELETED 전이는 DB 제약에 맞지 않아 REJECTED 퇴역으로 보정했다.
- Validation: `GitHubCanonicalIntegrationTest`와 Backend 전체 `check` 통과.
- Next steps: None.

## [2026-08-09] Session Summary (public archive security boundary)

- What was done: bounded archive 정상 수집, redirect origin 거부와 compressed size 초과 거부 테스트를 추가했다.
- Key decisions: loopback fixture는 package constructor로만 주입하고 production origin은 고정한다.
- Issues encountered: 없음.
- Validation: archive gateway와 기존 REST gateway focused tests가 통과했다.
- Next steps: None.

## [2026-08-09] Session Summary (optional GitHub App ID binding regression)

- What was done:
  - private repository 기능이 꺼진 상태의 blank App ID가 null로 binding되고 typed properties 검증을 통과하는 회귀를 추가했다.
- Key decisions:
  - 실제 credential 값은 fixture와 결과에 포함하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `GitHubAppSecurityBoundaryTest`와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-07] Session Summary (GitHub gateway·sanitizer 경계 검증)

- What was done: URL·selection·sanitizer와 WireMock REST error/conditional/truncation/redirect fixture를 추가했다.
- Key decisions: test loopback base URL 외 host 우회와 Authorization header를 허용하지 않는다.
- Issues encountered: timeout과 oversized body를 별도 safe failure로 고정했다.
- Validation: boundary 및 gateway test 통과.
- Next steps: None.
