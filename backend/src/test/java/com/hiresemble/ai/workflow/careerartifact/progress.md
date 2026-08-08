# Progress

## Overview

두 Career Artifact canonical workflow의 정적·실행 경계가 검증된다.

## [2026-08-08] Session Summary (Career Artifact workflow contract)

- What was done:
  - 각 8단계 순서, Chat/local 분리, 같은 exact model, tool 없음, correction 상한과 prompt 핵심 정책을 고정했다.
- Key decisions:
  - 전체 prompt 문자열 snapshot 대신 version·schema와 면접관 중심 핵심 정책을 assertion한다.
- Issues encountered:
  - None.
- Validation:
  - `CareerArtifactWorkflowContractTest` 통과.
- Next steps:
  - 실제 provider 호출은 범위 밖이다.
