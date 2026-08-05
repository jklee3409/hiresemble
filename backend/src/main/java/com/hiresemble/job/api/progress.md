# Progress

## Overview

P5~P6 Job 공개 API 10개의 validation·OpenAPI·HTTP status 계약, 자동 분석 projection과 P7/P8 child resource projection이 구현됐다.

## [2026-08-05] Session Summary (공고 기간 목록 API 계약)

- What was done:
  - `GET /jobs`를 등록 반기 preset·직접 시작일 filter와 `availablePeriods` page projection으로 변경했다.
- Key decisions:
  - 추출 상태와 마감 범위·임박 query는 allowlist에서 제거하고 기존 sort 계약은 유지한다.
- Issues encountered:
  - OpenAPI enum은 별도 component가 아닌 기간 DTO 필드에 inline 생성됐다.
- Validation:
  - OpenAPI contract와 Job API 통합 테스트, Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Job Analysis coverage API 확장)

- What was done:
  - summary/detail DTO와 mapper에 nullable `analysisCoverage`를 추가했다.
- Key decisions:
  - 기존 필드는 유지하는 additive response 확장으로 호환성을 보존한다.
- Issues encountered:
  - 과거 rubric row에는 coverage가 없어 nullable 계약이 필요했다.
- Validation:
  - OpenAPI/DTO compile과 Frontend typed contract 검증 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Job detail 자동 분석 projection)

- What was done:
  - `JobDetailDto`에 state·BALANCED quality·run ID·safe error의 `automaticAnalysis`를 additive하게 매핑했다.
- Key decisions:
  - endpoint와 request는 유지하고 내부 claim·attempt·provider 정보는 공개하지 않는다.
- Issues encountered:
  - OpenAPI exact field test를 새 projection과 동기화했다.
- Validation:
  - Job API 통합, owner 404와 OpenAPI contract 통과.
- Next steps:
  - None.

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
