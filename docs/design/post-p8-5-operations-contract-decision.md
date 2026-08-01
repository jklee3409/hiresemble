# P8.5 이후 운영 기반 계약 결정

- 상태: `APPROVED_DECISION_RECORD`
- 기준일: 2026-08-01
- 기준 commit: `42d4296a5b62d073cf0f059f0cee5cfbaf1f04cf`
- 적용 범위: P8.5-V, P8.6, P8.7, P8.8, P8.9-A와 P9·P10 선행 관계
- 활성 계약: [`../spec/`](../spec/index.md)

이 문서는 P8.5 구현 뒤 P9 전에 필요한 운영 기반을 단계화한 결정 기록이다. 현재 구현 완료를 선언하지 않으며, 공개 API·DB·페이지의 목표 계약은 활성 명세가 원천이다.

## 1. 검증된 기준선

저장소와 P8.5 결과를 다시 대조한 판정은 다음과 같다.

| 항목                            | 판정                            | 근거                                                                                      |
| ------------------------------- | ------------------------------- | ----------------------------------------------------------------------------------------- |
| OpenAI Chat adapter             | `IMPLEMENTED`                   | `OpenAiChatModel.call`, 요청별 model·timeout·output token·strict schema, provider retry 0 |
| OpenAI Embedding adapter        | `IMPLEMENTED`                   | `OpenAiEmbeddingModel.call`, 요청별 model·timeout·dimension, provider retry 0             |
| Tavily Search adapter           | `IMPLEMENTED`                   | HTTPS, redirect `NEVER`, 2MB bounded stream, BASIC/ADVANCED, raw content·answer 비활성    |
| local/local-offline Bean wiring | `VERIFIED`                      | capability별 단일 real 또는 disabled Bean integration test                                |
| Mock/WireMock integration       | `VERIFIED`                      | Fake gateway와 WireMock의 option·failure·bounded response·usage·migration test            |
| 실제 capability smoke           | `PARTIALLY_VERIFIED`            | Chat 2회·Embedding 1회는 `insufficient_quota`, Tavily BASIC 1회 성공                      |
| 실제 P4~P8 수직 흐름            | `NOT_VERIFIED`                  | 기록된 actual은 Fake/disabled이며 외부 network 호출 0                                     |
| P8.5 전체                       | `IMPLEMENTED_NOT_LIVE_VERIFIED` | 구현과 configuration readiness는 완료됐으나 사용자 local 실제 검증 전                     |

`StructuredOutputValidator`와 domain validation이 최종 권위다. Provider strict JSON Schema는 조기 형식 제한이며 이를 대체하지 않는다. Provider failure 때 disabled adapter로 fallback하지 않는다.

## 2. 승인된 결정

| ID    | 상태                | 결정                                                                                                         |
| ----- | ------------------- | ------------------------------------------------------------------------------------------------------------ |
| OP-01 | `APPROVED_DECISION` | Provider 비용 예산과 제품 기능 한도를 서로 다른 policy·ledger·오류로 운영한다.                               |
| OP-02 | `APPROVED_DECISION` | 사용자별 제품 사용량, 내부 Provider 원가, 과금 가능 usage unit을 집계한다.                                   |
| OP-03 | `APPROVED_DECISION` | 현재 paid plan·고객 청구·결제·구독·인보이스·환불·세금은 만들지 않는다.                                       |
| OP-04 | `APPROVED_DECISION` | P9 전에 P8.5-V→P8.6→P8.7→P8.8→P8.9-A를 순서대로 완료한다.                                                    |
| OP-05 | `APPROVED_DECISION` | P8.9-A는 ADMIN 읽기 전용 Backoffice이고 mutation은 P8.9-B로 분리한다.                                        |
| OP-06 | `APPROVED_DECISION` | B2C 실패 projection은 내부 Provider code와 분리된 category·CTA·보존 안내를 사용한다.                         |
| OP-07 | `APPROVED_DECISION` | Backend 목표 경계는 `usage`, `billing`, `backoffice`이며 기존 Provider 원가 ledger는 `agentrun`/`ai`에 둔다. |
| OP-08 | `APPROVED_DECISION` | API는 기존 implemented 63 paths/84 operations와 미래 `PLANNED` 계약을 분리한다.                              |
| OP-09 | `APPROVED_DECISION` | 일반 가입은 항상 USER이고 ADMIN은 공개 가입 없이 배포 통제 provisioning으로만 부여한다.                      |
| OP-10 | `APPROVED_DECISION` | P8.7 billing accounting은 대안 B를 채택해 `feature_usage_events`에 immutable billing snapshot을 둔다.        |

## 3. 네 가지 경계

| 경계                          | 목적                              | 원천·목표 저장소                                                             | 일반 사용자 노출                         |
| ----------------------------- | --------------------------------- | ---------------------------------------------------------------------------- | ---------------------------------------- |
| Provider 비용 예산            | 내부 원가 상한과 실행 전 보호     | 기존 `ai_budget_*`, `ai_price_*`, `ai_usage_records`                         | 실행 가능 여부와 안전한 예산 오류만      |
| 제품 기능 한도                | 사용자·기능·기간별 제품 사용 횟수 | P8.6 `feature_usage_policy_*`, assignment/override, period/reservation/event | 사용량, 남은 횟수, reset, 실행 가능 여부 |
| 사용자 사용량·과금 가능 usage | 운영 집계와 미래 과금 단위 고정   | `feature_usage_events` billing snapshot + immutable `billing_policy_*`       | 기능 사용량과 현재 청구 없음 안내        |
| 결제·구독                     | 실제 고객 금액 청구와 정산        | 이번 범위에 없음                                                             | 없음                                     |

Provider의 `cost_usd`는 내부 예상 원가이며 고객 청구 금액이 아니다. 제품 한도는 Provider 가격 변경으로 자동 변경되지 않는다.

## 4. 제품 기능 한도 계약

Canonical feature key는 다음과 같다.

```text
DOCUMENT_EVIDENCE_EXTRACTION
JOB_POSTING_EXTRACTION
JOB_ANALYSIS
COVER_LETTER_GENERATION
COVER_LETTER_VERIFICATION
INTERVIEW_PREPARATION
INTERVIEW_ANSWER_FEEDBACK
MOCK_INTERVIEW_SESSION_CREATE
MOCK_INTERVIEW_TURN
MOCK_INTERVIEW_SESSION_FEEDBACK
```

마지막 세 key는 P8.6에서 계약만 고정하고 P9에서 소비한다.

### 4.1 원자 상태 수명주기

```text
RESERVED
 ├─ COMMITTED  첫 Provider 비용 발생 또는 사용자 가치 산출물 생성
 ├─ RELEASED   외부 호출·가치 산출물 전 실패/취소
 └─ EXPIRED    claim 없이 만료되어 reconciliation으로 release
```

- 새 논리 제품 요청은 transaction에서 기간 row를 잠그고 1 unit을 reserve한다.
- 같은 `Idempotency-Key` 또는 `clientRequestId` replay는 기존 reservation/event를 재생하며 추가 소비하지 않는다.
- 자동 retry와 model 승격은 같은 제품 reservation 안에서 수행한다.
- Provider 비용이 0인 cache/reuse라도 새 사용자 의도와 새 idempotency key면 제품 unit은 1회 commit한다.
- 외부 호출 전 validation·owner·configuration 실패는 release한다.
- Provider 호출 뒤 timeout·structured output 실패·취소는 비용 발생 가능성을 반영해 commit한다.
- partial success는 commit한다. 새 client request ID를 사용하는 명시적 사용자 retry는 새 unit이다.
- 정책 item이 `UNLIMITED`이면 reservation/event는 audit용으로 남기되 잔여량을 차감하지 않는다.

### 4.2 기간과 override

- reset zone은 `Asia/Seoul`이다.
- period type은 `DAILY|WEEKLY|MONTHLY|LIFETIME`이며 시작·종료 instant를 row에 snapshot한다.
- immutable active policy version에 feature별 기본 limit와 소비 정책을 저장한다.
- 사용자 assignment는 active policy를 선택하고 override는 feature별 limit/unlimited와 유효기간을 가진다.
- override는 해당 사용자에만 적용되고 변경 전후·사유·actor를 audit한다.
- 동시 요청은 period row lock 또는 동등한 CAS로 `committed + reserved <= limit`를 원자 보장한다.

### 4.3 공개 오류

- `429 FEATURE_USAGE_LIMIT_EXCEEDED`: 제품 기능 한도. `/settings/usage` CTA와 reset 시각을 제공한다.
- `429 RATE_OR_BUDGET_LIMIT_EXCEEDED`: Provider 원가 예산 또는 rate 보호. 내부 USD를 오류 본문에 노출하지 않는다.

HTTP status가 같아도 code, 사용자 문구, suggested action과 운영 지표를 합치지 않는다.

## 5. 과금 가능 usage 결정

### 5.1 비교

| 기준                 | 대안 A: 별도 `billing_usage_events`     | 대안 B: feature event snapshot                       |
| -------------------- | --------------------------------------- | ---------------------------------------------------- |
| source of truth      | feature와 billing 두 event ledger       | feature event 하나                                   |
| 재생·중복 위험       | 두 ledger 원자 쓰기·reconciliation 필요 | feature commit과 같은 transaction                    |
| 과거 policy 보존     | 자연스럽지만 중복 저장 큼               | immutable billing policy ID와 unit snapshot으로 충족 |
| 미래 결제 분리       | 유리                                    | 실제 청구 도입 시 invoice ledger를 별도 추가 가능    |
| 현재 데이터량·복잡도 | 과도함                                  | 현재 no-paid-plan 범위에 적합                        |

### 5.2 채택안

`APPROVED_DECISION`: 대안 B를 채택한다.

- `feature_usage_events`에 `billing_policy_version`, `billable_quantity`, `billing_unit`, `charge_mode`를 commit snapshot으로 둔다.
- `billing_policy_versions`와 `billing_policy_items`는 immutable이며 feature→unit·charge mode mapping을 소유한다.
- `charge_mode`는 `METERED_ZERO_RATE|NO_CHARGE`다.
- 사용자에게 제공한 기능 unit은 기본 `METERED_ZERO_RATE`, system reconciliation·비과금 보정 event는 `NO_CHARGE`다.
- 고객 청구 금액 column과 payment/invoice row는 만들지 않는다. 현재 금액은 항상 0이며 내부 `cost_usd`를 복사하지 않는다.
- 미래 유료 상품 도입 시 이 snapshot을 invoice input으로 읽되 별도 결제 ledger를 새 계약으로 만든다.

### 5.3 집계 전략

`DEFAULT_ASSUMPTION`: P8.7은 raw source에 index를 둔 SQL read model을 먼저 사용한다.

- 원천은 Provider 원가 `ai_usage_records`, 제품·과금 가능 unit `feature_usage_events`다.
- 사용자/기간/feature/workflow/outcome/capability/quality dimension은 SQL projection으로 집계한다.
- 동일 source query로 reconciliation 합계를 계산할 수 있게 한다.
- aggregate table은 실제 데이터에서 Backoffice 대표 query p95가 목표를 넘거나 raw scan이 운영 부하를 만든다는 측정 증거가 있을 때만 후속 migration으로 추가한다.
- SQL read model은 삭제 후에도 audit 정책에 따라 비식별 사용자 상관키를 사용하고 원문을 포함하지 않는다.

## 6. AI 실패 UX 계약

내부 `FailureKind`, safe error code, HTTP code를 사용자 category와 일대일로 가정하지 않는다. versioned mapping이 다음 공개 projection을 만든다.

```text
failureCategory
title
message
suggestedAction
actionRoute
retryable
dataPreserved
requestId
usageMayHaveOccurred
```

Category:

```text
INPUT_REQUIRED
INSUFFICIENT_SOURCE_DATA
FEATURE_LIMIT_REACHED
COST_BUDGET_REACHED
TEMPORARY_PROVIDER_FAILURE
CONNECTION_RECOVERING
OUTPUT_VALIDATION_FAILED
CONTENT_SAFETY_BLOCKED
CONFIGURATION_UNAVAILABLE
RESOURCE_CONFLICT
INTERNAL_FAILURE
CANCELLED
```

Suggested action:

```text
EDIT_INPUT
OPEN_USAGE
RETRY_SAME_REQUEST
CREATE_NEW_RETRY
RESUME_RUN
OPEN_RESOURCE
CONTACT_SUPPORT
WAIT_AND_REFRESH
NONE
```

`CONNECTION_RECOVERING`은 SSE 단절 등 transport 상태이며 run failure로 저장하지 않는다. Provider 실명, model, raw response, prompt, stacktrace, 내부 endpoint와 secret은 projection에 포함하지 않는다.

P8.8은 기존 safe code와 Agent Run 상태에서 projection을 생성하므로 DB migration을 소비하지 않는다. stable code→category mapping과 문구 version을 테스트로 고정한다. 새 영속 snapshot이 필요하다는 계약 변경이 승인될 때만 당시 next available migration을 사용한다.

## 7. Backoffice 결정

### 7.1 권한

`APPROVED_DECISION`: P8.9-A는 기존 `users.role`을 `USER|ADMIN`으로 확장한다. 별도 operator table과 세분화 RBAC는 읽기 전용 운영 화면 하나만 필요한 현재 범위에 과도하므로 보류한다.

- 일반 signup·초대 없는 사용자 생성은 항상 USER다.
- ADMIN은 공개 API로 승격할 수 없다.
- 배포 통제된 backend provisioning command가 기존 사용자 ID, 사유, 실행자 상관키를 받아 한 transaction에서 role과 audit를 기록한다.
- `/api/v1/backoffice/**`는 Backend의 ADMIN 인가가 최종 권위다.
- AppLayout 일반 navigation에 Backoffice를 노출하지 않는다.
- Backoffice는 별도 `BackofficeLayout`과 ADMIN route guard를 사용하되 frontend guard만으로 권한을 판단하지 않는다.

### 7.2 P8.9-A 읽기 범위

- overview KPI
- 사용자 검색·상세와 aggregate usage
- 내부 AI 예상 원가와 capability breakdown
- Agent Run·failure category 조회
- Provider readiness와 active policy version
- 접근 audit

이력서·자기소개서·면접 답변/transcript, prompt/response, API key는 기본 조회와 drill-down 모두에서 제외한다. 이메일은 사용자 검색 업무에 필요한 최소 field로만 제공하고 접근을 audit한다.

### 7.3 P8.9-B 후속 mutation

feature limit override, active run cancel, retryable run retry, account lock/unlock, Provider feature kill switch는 `PLANNED_LATER`다. 각 mutation은 reason, expected version, idempotency, before/after snapshot, admin ID, request ID, confirmation과 audit가 없으면 구현하지 않는다.

## 8. 단계 선행 관계

```text
P8
→ P8.5 실제 Provider 연결
→ P8.5-V 사용자 로컬 실제 Provider 검증
→ P8.6 제품 기능 한도·metering
→ P8.7 사용자 사용량·내부 원가·과금 가능 사용량 집계
→ P8.8 AI 실패 UX·복구
→ P8.9-A 읽기 전용 Backoffice
→ P9 모의 면접
→ P10-A 사용자 Dashboard·설정
→ P10-B 운영 안정성·동시성
→ P10-C 출시 준비·전체 회귀
```

P8.9-B는 P8.9-A 뒤 별도 승인하는 후속이며 P9의 필수 선행은 아니다. P9의 필수 선행은 P3, P8.5-V, P8.6, P8.7, P8.8, P8.9-A다.

## 9. Migration·package 책임

미래 번호와 filename은 `TENTATIVE`이며 각 phase 시작 시 latest migration을 다시 확인한다.

| Phase  | tentative 책임                                                                                                 |
| ------ | -------------------------------------------------------------------------------------------------------------- |
| P8.6   | V16 feature policy·assignment/override·period·reservation·event (`TENTATIVE`; V15는 별도 대외활동 보정에 사용) |
| P8.7   | V17 immutable billing policy/snapshot 제약과 집계용 index                                                      |
| P8.8   | migration 없음                                                                                                 |
| P8.9-A | V18 USER/ADMIN role 확장, provisioning/access audit                                                            |
| P8.9-B | 예약하지 않음; 실제 착수 시 next available                                                                     |
| P9     | P8.9-A 완료 시점의 next available, 현재 예상 V19                                                               |

Backend 목표 책임:

```text
com.hiresemble/
├─ usage/        # 제품 기능 한도·metering
├─ billing/      # 과금 가능 usage policy·집계, 결제 제외
└─ backoffice/   # ADMIN 운영 query/action
```

Frontend 목표 책임:

```text
frontend/src/
├─ layouts/BackofficeLayout.vue
├─ pages/backoffice/
├─ features/usage/
├─ features/ai-failures/
└─ features/backoffice/
```

빈 directory는 선행 생성하지 않는다. Provider 원가 ledger를 `billing`으로 이동하거나 새 정책을 `common`에 넣지 않는다.

## 10. 거절한 대안

- 기존 USD budget ledger를 제품 횟수 한도로 재사용: 단위·reset·cache 의미가 달라 거절.
- 내부 Provider 원가를 사용자 청구 금액으로 노출: 현재 no-paid-plan 결정과 보안·제품 의미에 어긋나 거절.
- P8.9-A에서 운영 mutation까지 구현: audit·복구·confirmation 범위가 커져 단계 분리를 위해 거절.
- USER-only 상태에서 frontend route만 숨겨 Backoffice 보호: 서버 최종 인가가 없어 거절.
- 별도 `billing_usage_events`를 지금 추가: 이중 ledger와 reconciliation 비용이 현재 범위를 초과해 거절.
- 실측 없이 aggregate table, Redis, Kafka, microservice를 추가: 운영 근거가 없어 거절.
- P8.5 live 호출을 문서 작업에서 반복: 사용자 local 검증 gate와 비용·비밀값 경계를 침범하므로 거절.

## 11. Handoff

`OPEN_DECISION_BLOCKER`: None. 실제 phase 시작 시 latest migration과 live verification 결과를 다시 확인하는 것은 blocker가 아니라 실행 gate다.

첫 다음 단계는 P8.5-V 사용자 local 검증이다. capability smoke가 성공하면 `LOCAL_CAPABILITY_VERIFIED`, P4~P8 수직 흐름까지 성공하면 P8.5를 `DONE`으로 판정한다. 검증과 병행 가능한 첫 코드 단계는 P8.6이며, Provider 실제 응답 품질에 의존하지 않는 quota transaction·idempotency부터 시작한다.
