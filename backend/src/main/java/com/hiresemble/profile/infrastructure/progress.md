# Progress

## Overview

P2 프로필·direct evidence의 owner-scoped JDBC 영속성과 optimistic mutation이 구현되어 있다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/profile/infrastructure 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

- Key decisions:
  - 파일 경로, package·import와 필요한 FQCN만 변경하고 API·DB·workflow·접근 제한자는 유지했다.
  - 실제 파일이 있는 책임 package만 생성하고 P5 이후 기능과 빈 디렉터리는 만들지 않았다.

- Issues encountered:
  - package-private 결합은 접근 제한자를 넓히지 않고 같은 package 이동 또는 명시적 이동 제외로 처리했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P4 document evidence JDBC 연결)

- What was done:
  - owner-scoped document filter·candidate apply·delete/tombstone와 structured profile document FK 조회를 추가했다.
- Key decisions:
  - source chunk는 같은 user·document·revision이어야 하고 deleted source content는 tombstone에서 제거한다.
- Issues encountered:
  - None.
- Validation:
  - cross-user, invalid chunk, metadata/confidence, unreferenced delete와 referenced tombstone가 통과했다.
- Next steps:
  - P5 provenance store는 실제 schema가 생긴 뒤 contributor로 추가한다.

## [2026-07-19] Session Summary (P2 프로필 JDBC 영속성 구현)

- What was done:
  - 기본 프로필, 다섯 source와 evidence 조회·변경, pagination·sort, version·soft delete SQL을 구현했다.

- Key decisions:
  - resource 존재 여부와 타 사용자 소유 여부를 드러내지 않도록 모든 lookup을 `(user_id, id)`로 제한한다.

- Issues encountered:
  - None

- Validation:
  - PostgreSQL 통합 테스트에서 owner 404, soft-delete 제외, unique·CHECK·trigger와 transaction rollback을 검증했다.

- Next steps:
  - P4 document table 추가 때 owner 복합 FK를 새 migration과 함께 연결한다.
