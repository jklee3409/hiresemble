# Progress

## Overview

P2 프로필과 direct evidence의 인증 사용자 use case 및 transaction 조정이 구현되어 있다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/profile/application 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (Document evidence command/query port 추가)

- What was done:
  - AI candidate apply, document evidence 삭제·tombstone과 active document selection port를 추가했다.
- Key decisions:
  - AI는 profile repository 대신 command port만 사용하며 성공 candidate는 자동 승인하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - invalid chunk·cross-owner·partial candidate와 SOURCE_DELETED read-only가 통과했다.
- Next steps:
  - provenance 참조 판단은 contributor 확장으로 유지한다.

## [2026-07-19] Session Summary (P2 owner·version·동기화 use case 구현)

- What was done:
  - 기본·구조화 프로필 mutation, evidence 편집·검토와 가입 기본 프로필 등록을 구현했다.

- Key decisions:
  - 모든 resource lookup에 사용자 ID를 포함하고 source mutation과 evidence 동기화를 같은 transaction으로 묶었다.
  - 대표 학력·version 불변식 충돌은 승인된 409 code로 정규화한다.

- Issues encountered:
  - None

- Validation:
  - 통합 테스트에서 cross-user 404, CSRF, rollback, version 충돌과 source update 우선 동기화를 통과했다.

- Next steps:
  - P4 document 소유 확인 use case가 생기면 현재 404 경계를 실제 복합 owner 검증으로 교체한다.
