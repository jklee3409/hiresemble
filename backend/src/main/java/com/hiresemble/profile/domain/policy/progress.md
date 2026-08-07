# Progress

## Overview

com.hiresemble.profile.domain.policy package의 책임과 검증 상태를 추적한다. 이 package는 기존 Java 파일의 책임별 이동으로 생성됐으며 동작 계약은 변경하지 않았다.

## [2026-08-07] Session Summary (경험 semantic 유사도 정책)

- What was done:
  - SHA-256 fingerprint, anchor·수치 추출과 cosine 기반 동일·검토·충돌 판정을 추가했다.
- Key decisions:
  - 자동 동일은 0.94 이상·공통 anchor 2개·수치 충돌 없음, 0.82 이상은 검토 대상으로 제한한다.
- Issues encountered:
  - 비슷한 표현의 다른 수치 경험이 합쳐지지 않도록 수치 집합 불일치를 `CONFLICT`로 우선 판정한다.
- Validation:
  - exact·same·review·conflict·new 단위 테스트 통과.
- Next steps:
  - 임계치는 정책 version과 평가 dataset으로만 조정한다.

## [2026-07-31] Session Summary (교육 evidence category 정책)

- What was done:
  - 영문 canonical·한국어 학력/교육 label을 separator와 대소문자 차이 없이 판별하는 정책을 추가했다.
- Key decisions:
  - 문서 후보 방어와 DB constraint가 같은 category 집합을 사용한다.
- Issues encountered:
  - None.
- Validation:
  - category 단위 회귀를 포함한 Profile domain 6 tests 통과.
- Next steps:
  - None.

## [2026-07-23] Session Summary (책임별 policy package 분리)

- What was done:
  - 기존 Java 파일 1개를 policy 책임 package로 이동하고 package·import·필요한 FQCN을 정리했다.

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
