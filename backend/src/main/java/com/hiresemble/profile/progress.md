# Progress

## Overview

P2 기본·구조화 프로필·direct evidence와 P4 Document PENDING evidence·증빙 문서 FK를 owner-scoped transaction 경계로 구현했다.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/profile 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

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

## [2026-07-19] Session Summary (P4 Document evidence·증빙 문서 연결)

- What was done:
  - evidence document filter, document candidate PENDING 적용과 자격증·어학·수상 owner document 연결을 활성화했다.
- Key decisions:
  - 참조 없는 document evidence는 삭제하고 참조가 있으면 같은 ID의 `SOURCE_DELETED` tombstone으로 전환한다.
- Issues encountered:
  - P5 이후 provenance table 없이 Fake `EvidenceReferenceQueryPort` contributor로 tombstone branch를 검증했다.
- Validation:
  - active document owner 404, evidence edit·VERIFIED·REJECTED와 tombstone mutation 409가 통과했다.
  - 최종 read-only Validator가 document filter·증빙 FK·SOURCE_DELETED 정책을 포함해 `PASS`했다.
- Next steps:
  - 실제 downstream provenance contributor는 해당 domain phase에 추가한다.

## [2026-07-19] Session Summary (P2 프로필·직접 입력 근거 구현)

- What was done:
  - 기본 프로필 2개, 구조화 프로필 20개, evidence 3개 operation과 완료도·pagination·sort·optimistic version 계약을 구현했다.
  - 다섯 구조화 source의 생성·수정·soft delete와 direct evidence 생성·재생성·삭제를 같은 transaction으로 연결했다.

- Key decisions:
  - 원본 source를 source of truth로 두고 source 수정 시 evidence 별도 편집보다 동기화 결과와 `VERIFIED` 상태가 우선한다.
  - 모든 조회·mutation은 사용자 ID를 포함하며 존재하지 않음과 타 사용자 소유를 동일한 404로 처리한다.
  - P2 document field는 nullable 계약만 유지하고 non-null 입력·filter는 404로 거부한다.

- Issues encountered:
  - 기존 개발 DB에는 Flyway V2 이력 없이 Session table이 남아 있어 E2E에는 별도 빈 PostgreSQL DB를 사용했다. 기존 DB 데이터는 수정하지 않았다.

- Validation:
  - `backend\\gradlew.bat check`에서 9개 test class, 54개 test가 failure·error·skip 0으로 통과했다.
  - 빈 DB V1→V2→V3 및 V1-only·V2-only upgrade, V1·V2 hash, DB CHECK·owner·rollback을 PostgreSQL로 검증했다.
  - 실제 Chromium E2E에서 가입·완료도·학력 수정·두 사용자 owner 404·로그아웃/재로그인을 통과했다.
  - 최종 read-only validator가 AC-02와 profile 계약을 `PASS`로 판정했다.

- Next steps:
  - P2는 완료 상태다.
  - P4에서 `documents` aggregate를 구현할 때 nullable document column의 복합 FK를 forward migration으로 추가한다.
