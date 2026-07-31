# Progress

## Overview

P5~P6 Job 공개 API 10개의 validation·OpenAPI·HTTP status 계약과 P7/P8 child resource projection이 구현됐다.

## [2026-07-31] Session Summary (P8 면접 준비 projection)

- What was done:
  - Job detail에 현재 사용 가능한 interview question set·research·Agent Run 최소 projection을 연결했다.
- Key decisions:
  - 내부 workflow step/provider는 노출하지 않고 면접 tab 진입에 필요한 owner-scoped ID·status만 반환한다.
- Issues encountered:
  - None.
- Validation:
  - Job contract·P8 preparation page·OpenAPI 회귀가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 자기소개서 진입 projection)

- What was done:
  - Job detail mapping에 현재 자기소개서 상태·ID를 최소 projection으로 연결했다.
- Key decisions:
  - 자기소개서 전체 결과나 P8 field를 Job DTO에 복제하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Job DTO/API 회귀, OpenAPI 70 operations/51 paths와 P7 actual 공고 tab이 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 Job Analysis API 3개)

- What was done:
  - 분석 접수, immutable 이력과 최신 상세 endpoint·DTO·mapper를 추가했다.
- Key decisions:
  - 실제 HTTP 202/200과 공통 오류 DTO를 사용하고 owner resource는 404로 숨긴다.
- Issues encountered:
  - OpenAPI exact operation 기준선을 53/37로 갱신했다.
- Validation:
  - API 통합·OpenAPI와 Backend 전체 check가 통과했다.
- Next steps:
  - P7 API는 이번 범위에 없다.

## [2026-07-27] Session Summary (Job 공개 API 구현)

- What was done:
  - 생성, 목록, 상세, 수정, 상태 변경, 추출 retry와 soft delete endpoint를 추가했다.
- Key decisions:
  - 수동 본문 생성은 201, URL 추출 생성은 202이며 replay도 최초 status와 ID를 유지한다.
- Issues encountered:
  - P6 projection은 명세가 요구한 null·false·빈 값만 반환하도록 제한했다.
- Validation:
  - OpenAPI 50 operations/34 paths와 Job 7개 operationId·security·response 계약이 통과했다.
- Next steps:
  - P6 전까지 analysis endpoint를 추가하지 않는다.
