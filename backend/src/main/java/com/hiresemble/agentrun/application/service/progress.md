# Progress

## Overview

com.hiresemble.agentrun.application.service package의 책임과 검증 상태를 추적한다. 이 package는 기존 Java 파일의 책임별 이동으로 생성됐으며 동작 계약은 변경하지 않았다.

## [2026-07-31] Session Summary (Agent Run 삭제 use case)

- What was done:
  - 개별 삭제를 선택 삭제로 합성하고 owner·시각·1~100개 non-null ID를 검증하는 use case를 추가했다.
- Key decisions:
  - terminal·visibility 판정과 transaction은 persistence port가 row lock 안에서 최종 확인한다.
- Issues encountered:
  - None.
- Validation:
  - owner·active conflict·foreign atomic rollback 통합 테스트와 전체 check 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (Run interruption compensation 조정)

- What was done:
  - 실패·취소·reconcile terminal 전환이 workflow별 resource compensation을 일관되게 호출하는 service를 추가했다.
- Key decisions:
  - P7 verification PENDING을 영구 방치하지 않고 terminal Run과 domain 상태를 함께 안정화한다.
- Issues encountered:
  - 없음.
- Validation:
  - failure/cancel/reconcile compensation과 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-23] Session Summary (책임별 service package 분리)

- What was done:
  - 기존 Java 파일 7개를 service 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

- Key decisions:
  - 실제 구현 파일이 있는 package만 생성하고 미래 기능이나 빈 책임 디렉터리는 만들지 않았다.
  - API·DB·workflow·Spring Bean 동작과 접근 제한자는 유지했다.

- Issues encountered:
  - 구조 세분화 과정에서 추가 기능 변경이나 계약 충돌은 발견되지 않았다.

- Validation:
  - 운영·테스트 Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import와 package-private 교차 참조 정적 검사를 통과했다.
  - HEAD 대비 package·import·FQCN을 제외한 본문 비교 237건이 모두 일치했고 `git diff --check HEAD`가 통과했다.
  - Docker를 찾을 수 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 수행하지 않았으며 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.
