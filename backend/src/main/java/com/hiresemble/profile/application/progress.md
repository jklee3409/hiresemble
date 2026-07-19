# Progress

## Overview

P2 프로필과 direct evidence의 인증 사용자 use case 및 transaction 조정이 구현되어 있다.

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
