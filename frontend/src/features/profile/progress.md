# Progress

## Overview

P2 프로필 Zod·query key·version conflict와 공용 입력 component가 구현되어 있다.

## [2026-07-19] Session Summary (P2 프로필 feature 규칙 구현)

- What was done:
  - 배열 최대 10개·중복, 날짜·GPA·current career schema와 사용자별 query key를 구현했다.
  - 409에서 미저장 값과 최신 snapshot을 비교하고 field별 재적용하는 UI를 구현했다.

- Key decisions:
  - 서버 상태는 Vue Query, form draft는 component local state로 유지하고 Pinia에 프로필 데이터를 저장하지 않는다.
  - version 충돌 mutation은 자동 재시도하지 않는다.

- Issues encountered:
  - 409 snapshot의 GPA 문자열 변환을 form schema와 맞추도록 보정했다.

- Validation:
  - schema·query key·conflict 단위 테스트와 frontend 전체 check가 통과했다.

- Next steps:
  - P4 document 기능은 실제 Backend 계약 확정 뒤 별도 feature로 연결한다.
