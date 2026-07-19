# Progress

## Overview

Hiresemble JUnit package의 중간 Java namespace를 관리한다. 현재 P1·P2 test package를 포함한다.

## [2026-07-19] Session Summary (P2 테스트 namespace 확장)

- What was done:
  - 하위 `hiresemble` namespace에 profile 테스트 영역을 연결했다.

- Key decisions:
  - 중간 namespace에는 기능 test를 직접 두지 않는다.

- Issues encountered:
  - None

- Validation:
  - Backend 전체 check가 통과했다.

- Next steps:
  - 후속 test도 기능 package 아래에 둔다.

## [2026-07-19] Session Summary (테스트 com namespace 구성)

- What was done:
  - Hiresemble P1 test package의 중간 namespace를 생성했다.

- Key decisions:
  - 실제 테스트 책임은 하위 com.hiresemble package에 둔다.

- Issues encountered:
  - None

- Validation:
  - Gradle test source compilation이 통과했다.

- Next steps:
  - 새 테스트는 가장 가까운 기능 package에 둔다.
