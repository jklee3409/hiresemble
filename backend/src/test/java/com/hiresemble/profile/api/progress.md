# Progress

## Overview

P2 프로필 HTTP·transaction 통합 테스트가 구현되어 있다.

## [2026-07-31] Session Summary (최종 학력 CRUD 재계산 회귀)

- What was done:
  - 고등학교 뒤 학사 생성, 기존 항목의 박사 변경과 박사 삭제 뒤 다음 학력 승계를 검증했다.
- Key decisions:
  - 반환 DTO의 단계·최종 학력 flag와 evidence 미생성을 함께 assertion한다.
- Issues encountered:
  - None.
- Validation:
  - `ProfileIntegrationTest`와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (학력 evidence 비노출 회귀)

- What was done:
  - 학력 CRUD가 evidence를 만들지 않고 legacy EDUCATION tombstone이 목록·수정에서 404로 숨겨지는지 검증했다.
  - 기존 owner/version evidence 검증 fixture를 CAREER direct evidence로 전환했다.
  - 직접 입력 CAREER 근거의 승인·거절 API가 state conflict로 거부되는지 확인했다.
- Key decisions:
  - 학력 구조화 CRUD와 비학력 evidence 계약을 독립적으로 assertion한다.
- Issues encountered:
  - None.
- Validation:
  - `ProfileIntegrationTest` 10 tests와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-19] Session Summary (P2 프로필 API 통합 검증)

- What was done:
  - 프로필 CRUD, evidence 동기화, CSRF·401, owner 404, version 409와 document 지연 경계를 검증했다.

- Key decisions:
  - 타 사용자 ID와 없는 ID는 같은 공개 오류로 assertion한다.

- Issues encountered:
  - None

- Validation:
  - `backend\\gradlew.bat check`에서 통합 테스트가 PostgreSQL Testcontainers로 통과했다.

- Next steps:
  - P4에서 document owner 연결 성공 경로를 별도 추가한다.
