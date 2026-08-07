# Progress

## Overview

P4 `DOCUMENT_INGESTION`을 Backend port 기반 active v2 9단계와 durable legacy v1 8단계 workflow로 구현했다.

## [2026-08-07] Session Summary (후보 embedding·canonical 경험 판정 단계)

- What was done:
  - 추출과 적용 사이에 후보 embedding 단계를 추가하고 masked 후보만 transaction 밖 gateway에 전달했다.
- Key decisions:
  - 동일 hash 후보는 한 번만 embedding하고 적용 단계에서 중복·semantic match를 각각 판정한다.
- Issues encountered:
  - 최초 전체 검사에서 중복 hash가 embedding 단계에서 실패해 deduplicate 보정 후 집중 재검증했다.
- Validation:
  - active 9단계·legacy 8단계와 partial rejection을 포함한 집중 테스트 통과.
- Next steps:
  - 실제 Provider 호출은 승인된 별도 검증에서만 수행한다.

## [2026-08-02] Session Summary (문서 추출 소재 한국어 검증)

- What was done:
  - evidence title·content와 선택 warning이 한국어 사용자 문장을 포함하도록 record policy를 보강했다.
- Key decisions:
  - 교육 category domain filtering과 기존 DTO를 유지하고 영어 전용 사용자 문장만 correction-once로 교정한다.
- Issues encountered:
  - prompt contract assertion이 문장 줄바꿈 때문에 1회 실패해 두 의미 조각으로 교정했다.
- Validation:
  - manual JSON, 후보 의미 제약, 영어 소재 거부를 포함한 Document workflow contract 테스트 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (candidate rejection 성공 계약)

- What was done:
  - apply output v2에 candidate/applied/rejected와 stable reason count를 추가하고 가짜 `candidate-rejected-*` failed scope를 제거했다.
- Key decisions:
  - 일부·전체 rejection도 apply/finalize가 정상 완료되면 문서와 Run 성공이며 적용 evidence ID만 result reference다.
- Issues encountered:
  - 기존 live run의 정확한 rejection reason은 과거 checkpoint로 복구할 수 없다.
- Validation:
  - 6→4/2, 4→4/0, 3→0/3 시나리오와 Provider/structured/domain failure 회귀 통과.
- Next steps:
  - 실제 Provider 재호출 없이 offline 상태를 유지하고 bounded terminal 재검증을 handoff한다.

## [2026-08-01] Session Summary (local ref trusted mapping과 metadata 제거)

- What was done:
  - Provider payload/output의 document ID·revision·실제 chunk UUID·metadata를 제거하고 `C1` ref를 same-revision trusted UUID로 복원했다.
  - candidate `min(12, chunks×2)`, source ref 8, content 2,000자, warning 500자 정책을 prompt/record/workflow에서 공유했다.
- Key decisions:
  - 사용처 없는 Provider metadata는 제거하되 domain/public `Map<String,Object>`·JSONB는 빈 object와 warning projection으로 유지한다.
- Issues encountered:
  - 과거 live output의 정확한 invalid field는 확인할 수 없다.
- Validation:
  - manual JSON, unknown/duplicate/blank ref, cap/null/warning, UUID mapping과 persistence 회귀 통과.
- Next steps:
  - Chat capability 성공 뒤 실제 document ingestion 1회만 검증한다.

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
