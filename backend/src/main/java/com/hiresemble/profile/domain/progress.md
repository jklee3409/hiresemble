# Progress

## Overview

P2 프로필 완료도와 구조화 source·direct evidence 불변식이 구현되어 있다.

## [2026-07-19] Session Summary (P2 완료도·날짜·evidence 정책 구현)

- What was done:
  - 다섯 완료 항목, 배열 canonicalization, 학력·GPA·자격·어학·경력 날짜와 source mapping 정책을 구현했다.
  - source별 direct evidence title·content·metadata 생성 규칙을 구현했다.

- Key decisions:
  - profile completion은 저장 입력이 아닌 read 시 서버 계산 결과이며 기능 접근을 차단하지 않는다.
  - source 수정은 evidence 별도 편집을 덮고 `VERIFIED`·`verifiedAt`을 다시 설정한다.

- Issues encountered:
  - None

- Validation:
  - 도메인 단위 테스트에서 완료도, 날짜·GPA, mapping, 재동기화 우선과 `SOURCE_DELETED` 금지를 검증했다.

- Next steps:
  - P4 AI 추출 evidence는 direct source 규칙과 분리해 추가한다.
