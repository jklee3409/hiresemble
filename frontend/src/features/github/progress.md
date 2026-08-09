# Progress

## Overview

Gate 2 공개 GitHub와 Gate 5 GitHub App private source의 owner-scoped query, connection mutation, callback cleanup과 focused Run 표시 기반을 관리한다.

## [2026-08-09] Session Summary (GitHub App connection과 private source card)

- What was done:
  - capability/unavailable, 연결 시작·결과, personal/organization 목록·상태·refresh·manage link·disconnect dialog와 private source 입력을 구현했다.
- Key decisions:
  - install/manage URL은 HTTPS github.com의 고정 path/query shape만 허용하고 callback 민감 query는 즉시 제거한다. PAT 입력 UI는 없다.
- Issues encountered:
  - setup mock 302의 route interception 우회가 있어 E2E fixture를 명시적 OAuth link와 catch-all HTTPS 차단으로 보정했다.
- Validation:
  - component/navigation/query test와 Frontend 전체 check가 통과했다. 통합 Chromium은 strict locator 보정 뒤 재검증 대기다.
- Next steps:
  - 같은 5개 Chromium journey 재검증 후 실제 App UAT를 사용자에게 인계한다.

## [2026-08-08] Session Summary (Career Artifact suggestion readiness 연동)

- What was done:
  - READY/PARTIAL 성공 summary에 선택적 Career Artifact 제안을 연결하고 source 완료·삭제 뒤 readiness를 갱신했다.
- Key decisions:
  - Career Artifact flag off 또는 readiness 실패 시 제안과 요청만 숨기고 GitHub 주 흐름은 유지한다.
- Issues encountered:
  - 없음.
- Validation:
  - 기존 `github-source.spec.ts`를 Career Artifact spec과 함께 Chromium에서 통과했다.
- Next steps:
  - Private GitHub는 Gate 5까지 추가하지 않는다.

## [2026-08-08] Session Summary (GitHub Source query·mutation·Run monitor)

- What was done:
  - source list/detail/repository key, server query, create/selection/refresh/delete mutation, 상태·URL presentation과 focused Agent Run monitor를 구현했다.
  - 같은 pending action의 idempotency key 재사용, 성공·입력 identity 변경 시 교체, delete stream 종료와 source/experience cache 정리를 검증했다.
- Key decisions:
  - mutation retry는 모두 false이고 409는 자동 재시도하지 않는다. refresh unchanged는 source snapshot만 갱신하며 changed run만 monitor한다.
- Issues encountered:
  - None.
- Validation:
  - `src/features/github` 단위·component test와 전체 Frontend 80 files/369 tests 통과.
- Next steps:
  - None.
