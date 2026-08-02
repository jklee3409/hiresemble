# Progress

## Overview

P3 응답 5단계 validation 앞에 OpenAI strict request schema의 중앙 생성·호환성 검증 경계가 구현됐다.

## [2026-08-02] Session Summary (한국어 사용자 문장 최소 검증 정책)

- What was done:
  - 공고 분석과 문서 소재가 공유하는 `KoreanUserFacingTextPolicy`를 추가해 한국어 음절 포함 여부를 값 비노출 방식으로 검사했다.
- Key decisions:
  - 고유명사·제품명·기술 용어가 섞인 한국어 문장을 허용하고 자연어 품질 전체를 정규식으로 판단하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 한국어·기술 용어 혼합, 영어 전용 문장과 null 단위 테스트 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (Structured Output phase·reason 분리)

- What was done:
  - JSON_PARSE, SCHEMA_SHAPE, JAVA_BINDING, JAVA_RECORD, WORKFLOW_CONTEXT, DOMAIN_COMMAND 경계를 typed exception과 safe code로 분리했다.
- Key decisions:
  - parse/schema/binding은 deterministic, 명시된 record/workflow reason만 repair-once이며 domain은 기존 DOMAIN_VALIDATION을 유지한다.
- Issues encountered:
  - generic exception message·JSON path·실제 invalid value는 진단에 사용할 수 없다.
- Validation:
  - phase별 generic/typed failure, 비노출, retry disposition tests와 strict schema 전수 검증 통과.
- Next steps:
  - 과거 live invalid field는 미확정으로 유지하고 새 run에서 stable reason만 확인한다.

## [2026-08-01] Session Summary (OpenAI strict schema 중앙 검증)

- What was done:
  - 중첩 object/required/additionalProperties, nullable union, keyword·깊이·property·enum 한도를 재귀 검사하고 validated schema registry를 추가했다.
- Key decisions:
  - 실제 runtime generator를 test와 공유하며 schema 문자열 치환이나 non-strict fallback을 사용하지 않는다.
- Issues encountered:
  - Spring AI 기본 converter는 `Map<String,Object>`를 bare object로, 선택 warning을 non-null string으로, 공개 TipTap DTO annotation을 확인되지 않은 `default` keyword로 생성했다.
- Validation:
  - 기존 schema 실패 재현, 수정 schema 14개, completeness와 deterministic hash 검사가 통과했다.
- Next steps:
  - OpenAI subset 변경 시 공식 계약을 근거로 validator와 회귀 fixture를 함께 갱신한다.

## [2026-07-19] Session Summary (Structured Output 검증 chain 구현)

- What was done:
  - JSON parsing, schema, Java record, workflow와 domain command 검증 순서를 고정했다.

- Key decisions:
  - structured 오류는 retryable, domain command 오류는 non-retryable이다.

- Issues encountered:
  - None.

- Validation:
  - 순서와 오류 분류 unit test 2개가 통과했다.

- Next steps:
  - 각 실제 output schema와 workflow-specific validator를 해당 phase에서 추가한다.
