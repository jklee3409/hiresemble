# Progress

## Overview

P2 기본·구조화 프로필과 direct evidence를 owner-scoped transaction 경계로 구현했다. 문서 aggregate와 AI 추출 근거는 아직 없다.

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
