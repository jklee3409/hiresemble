# Progress

## Overview

P2 프로필 Zod·query key·version conflict와 공용 입력 component가 구현되어 있다.

## [2026-07-28] Session Summary (경험 정보·학력·충돌 문구 재설계)

- What was done:
  - profile navigation의 `직접 입력 근거`를 `경험 정보`로, 대표 학력을 `먼저 보여 줄 학력`으로 바꾸고 conflict·입력 helper를 자연화했다.
  - 경험 정보 metadata를 기존 key와 string·number·boolean·null을 그대로 보존하는 항목형 편집기로 바꾸고 round-trip 경계를 테스트했다.
- Key decisions:
  - `SOURCE_DELETED` read-only와 409 field 재적용 동작은 그대로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - profile page·schema·conflict test와 actual E2E selector의 동일 행동 명칭을 갱신했다.
- Next steps:
  - 실제 경력 timeline의 긴 성과 내용은 Backend 환경에서 추가 검수한다.

## [2026-07-27] Session Summary (Profile navigation·입력·충돌 UX 개선)

- What was done:
  - 구현된 7개 profile route navigation, string chip 입력과 409 비교·재적용 panel을 공통 제품 스타일과 mobile overflow 대응으로 개선했다.
- Key decisions:
  - keyboard 추가·삭제, 사용자별 query key와 자동 overwrite 금지 계약을 유지한다.
- Issues encountered:
  - generic 구조화 profile page의 여러 유형을 분리하지 않고 동일 component 안에서 data 성격별 presentation을 조정했다.
- Validation:
  - profile page/component test와 기존 ID·testid 보존 검사가 통과했다.
- Next steps:
  - 실제 데이터가 많은 경력 timeline은 cross-stack 환경에서 추가 시각 검수한다.

## [2026-07-19] Session Summary (P4 증빙 문서 selector 활성화)

- What was done:
  - 자격증·어학·수상 active document selector와 evidence document filter schema·query key를 활성화했다.
- Key decisions:
  - 선택 후보는 같은 사용자 active document 목록만 사용한다.
- Issues encountered:
  - None.
- Validation:
  - schema·query key·page component와 owner filter E2E가 통과했다.
- Next steps:
  - deleted document는 selector cache에서 즉시 제거한다.

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
