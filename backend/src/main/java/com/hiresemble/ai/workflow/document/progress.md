# Progress

## Overview

P4 `DOCUMENT_INGESTION`을 Backend port 기반의 고정 8단계 workflow로 구현했다.

## [2026-08-01] Session Summary (evidence metadata Provider 계약 분리)

- What was done:
  - Provider output metadata를 key/type/value scalar entry 배열로 바꾸고 중복·예약 key·type·크기·민감 문자열을 domain apply 전에 검증했다.
  - 명시적 mapper로 기존 scalar map과 nullable validation warning 의미를 보존했다.
- Key decisions:
  - metadata key가 실제로 동적이므로 고정 record 대신 제한 entry를 선택하고 JSONB/public API는 그대로 유지한다.
- Issues encountered:
  - 수정 전 `Map<String,Object>`가 strict-incompatible bare object schema를 생성했다.
- Validation:
  - 저장 의미·invalid metadata·warning·embedding 보존 integration 회귀가 통과했다.
- Next steps:
  - 실제 문서 vertical 1회로 Chat 단계와 최종 PENDING evidence를 확인한다.

## [2026-08-01] Session Summary (Embedding provider canonical key 적용)

- What was done:
  - active embedding policy 검증을 V14의 canonical `openai` key와 일치시켰다.
- Key decisions:
  - workflow와 gateway가 동일한 lowercase provider contract를 사용한다.
- Issues encountered:
  - None.
- Validation:
  - Document ingestion 통합 테스트와 Backend 전체 check가 통과했다.
- Next steps:
  - quota 복구 후 실제 문서 embedding 수직 흐름을 검증한다.

## [2026-08-01] Session Summary (실제 Embedding 가격 계약 연결)

- What was done:
  - 문서 ingestion의 Embedding 요청에 run 고정 price version을 전달했다.
- Key decisions:
  - masked chunk·순서·dimension 기존 계약은 그대로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - P4 actual Chromium 4/4와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-19] Session Summary (P4 DOCUMENT_INGESTION contribution 구현)

- What was done:
  - `LOAD_DOCUMENT_SOURCE`부터 `FINALIZE_DOCUMENT`까지 정확한 순서와 structured candidate validation/apply를 구현했다.
  - short text WAITING_USER, manual same-run resume, embedding·extraction partial failure 보상을 연결했다.
- Key decisions:
  - gateway 입력은 masked chunk만 허용하고 checkpoint·Agent Step에는 ID·hash·count·safe summary만 저장한다.
- Issues encountered:
  - production Agent Step FK를 위해 V5에 active model policy version 1 seed가 필요해 통합 시 root가 추가했다.
- Validation:
  - 성공 PENDING evidence, same-run resume, invalid dimension partial failure와 P3 orchestrator 회귀가 통과했다.
  - 최종 read-only Validator가 masked-only·partial success·Fake provider 경계를 포함해 `PASS`했다.
- Next steps:
  - 실제 provider와 전체 RAG retrieval은 후속 phase에서 별도 policy로 구현한다.
