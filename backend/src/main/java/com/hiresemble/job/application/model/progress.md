# Progress

## Overview

P5 Job과 P6 Job Analysis application 결과·snapshot·command record, 자동 분석 request·projection model이 구현됐다.

## [2026-08-02] Session Summary (자동 분석 application model)

- What was done:
  - durable 요청 snapshot, 사용자 projection과 after-commit event record를 추가했다.
- Key decisions:
  - 공개 state는 내부 CLAIMED 상태를 숨기고 사용자 행동과 복구 가능성만 표현한다.
- Issues encountered:
  - None.
- Validation:
  - compile과 Job API projection tests 통과.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 분석 immutable model)

- What was done:
  - 최소 profile/evidence snapshot, retrieval candidate, summary/detail과 persist command record를 추가했다.
- Key decisions:
  - domain entity·원문 전체·provider/model/storage 내부값을 노출하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - compile·workflow/Job 통합과 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (Job application projection 정의)

- What was done:
  - 생성·목록·상세·mutation·workflow snapshot 결과를 immutable record로 분리했다.
- Key decisions:
  - P6 projection은 null·false·빈 값의 P5 기본값으로만 표현한다.
- Issues encountered:
  - 없음.
- Validation:
  - API와 workflow compile·통합 테스트에서 mapping을 검증했다.
- Next steps:
  - P6 계약 승인 전 분석 결과 record를 추가하지 않는다.
