# 사용량 Metering·집계·Backoffice 운영 계획

이 문서는 P8.6~P8.9의 미래 운영 절차와 검증 경계를 정의한다. 현재 table, API, route가 구현됐다는 뜻이 아니며 모든 항목은 해당 phase 전까지 `PLANNED`다. 계약 결정은 [P8.5 이후 운영 기반 결정](../design/post-p8-5-operations-contract-decision.md), 공개 목표는 [활성 명세](../spec/index.md)를 따른다.

## 1. 단계 상태

| 단계   | 상태                            | 운영 결과                                                    |
| ------ | ------------------------------- | ------------------------------------------------------------ |
| P8.5-V | `USER_LOCAL_VALIDATION_PENDING` | 실제 Provider capability·수직 흐름 증거                      |
| P8.6   | `PLANNED`                       | 기능 한도 reserve/commit/release, 현재 사용량                |
| P8.7   | `PLANNED`                       | 사용자 사용량·내부 원가·과금 가능 unit 집계와 reconciliation |
| P8.8   | `PLANNED`                       | 공통 실패 category·CTA·보존 안내                             |
| P8.9-A | `PLANNED`                       | ADMIN 읽기 전용 Backoffice와 접근 audit                      |
| P8.9-B | `PLANNED_LATER`                 | 제한된 운영 mutation                                         |

## 2. P8.5-V 사용자 local 검증

### 2.1 Capability smoke

일반 `local` profile에서 사용자가 capability별 정확히 1회 수행한다.

```text
OpenAI Chat 1회
OpenAI Embedding 1회
Tavily BASIC 1회
```

key, prompt, response, 사용자 원문은 기록하지 않는다. 기록 가능한 항목은 성공 여부, safe error code, request ID, Agent Run ID, usage/cost 합계뿐이다.

### 2.2 제품 수직 검증

```text
문서 업로드 → embedding → 근거 추출
공고 등록 → 공고 추출 → 공고 분석
자기소개서 생성 → 검증
면접 준비 → Tavily 조사 → 질문 생성 → 답변 피드백
```

연결 성공과 결과 품질을 별도 항목으로 판정한다. capability smoke만 성공하면 `LOCAL_CAPABILITY_VERIFIED`, P4~P8 전체가 성공하면 `LOCAL_VERTICAL_VERIFIED`를 거쳐 P8.5를 `DONE`으로 바꾼다. Codex가 이 문서 작업에서 호출하지 않는다.

## 3. P8.6 기능 한도 운영

### 접수 순서

```text
인증·소유권·요청 형식
→ idempotency replay 조회
→ active feature policy/override 결정
→ Asia/Seoul 기간 row 잠금
→ 1 unit reserve
→ domain resource/Agent Run 접수
→ commit
```

예산 reserve와 기능 unit reserve는 서로 다른 ledger지만 같은 제품 command에서 둘 중 하나라도 실패하면 resource/run을 만들지 않는다. 교착을 막기 위해 구현 단계에서 모든 command가 `feature period → AI budget ledger`의 동일 잠금 순서를 사용한다.

### terminal 처리

| 결과                                   | 기능 reservation | 비용 reservation                      |
| -------------------------------------- | ---------------- | ------------------------------------- |
| Provider 호출 전 validation/취소       | release          | release                               |
| Provider 호출 뒤 실패·취소             | commit           | 실제 usage settle 후 미사용액 release |
| 성공·partial success                   | commit           | 실제 usage settle 후 미사용액 release |
| 같은 idempotency/client request replay | 원래 결과 재생   | 원래 결과 재생, 새 Provider 호출 없음 |
| automatic retry/model 승격             | 같은 unit 유지   | 발생한 모든 Provider usage settle     |
| 새 request ID의 명시적 retry           | 새 unit reserve  | 새 예산 reserve                       |

### Reconciliation

- 만료된 `RESERVED` row는 연결 resource/run/turn 상태와 provider usage를 확인한다.
- 비용 또는 사용자 가치가 발생했으면 commit, 둘 다 없으면 release한다.
- correction event는 원 event를 수정하지 않고 보정 event와 reason·actor·request ID를 남긴다.
- oversubscription, 음수 remaining, event 없는 committed count는 운영 경보다.

## 4. P8.7 집계와 reconciliation

### Source of truth

- 내부 Provider 원가: `ai_usage_records.cost_usd`
- 제품·과금 가능 usage: `feature_usage_events`
- 현재 고객 청구 금액: 0

### 일일 검증

1. 기간별 feature event 합계와 period committed count를 비교한다.
2. `provider_call_id + price_item_id` 중복이 없는지 확인한다.
3. Agent Run `actual_cost_usd`와 연결 usage 합계를 비교한다.
4. failed/cancelled/retry/cache/reuse/0-cost event를 outcome별로 대조한다.
5. billing snapshot이 당시 immutable policy item과 일치하는지 확인한다.
6. aggregation query watermark와 현재 시각의 lag를 측정한다.

불일치는 원본 row를 덮어쓰지 않고 reconciliation finding과 보정 근거를 남긴다. 사용자 원문은 진단 데이터에 포함하지 않는다.

### 사용자와 운영자 표시 경계

| 사용자 `/settings/usage`                | ADMIN Backoffice                                            |
| --------------------------------------- | ----------------------------------------------------------- |
| 기능별 사용량·남은 횟수·reset·unlimited | 사용자/기간/기능별 사용량                                   |
| 현재 실행 가능 여부                     | capability/model tier별 내부 원가                           |
| 기간별 과금 가능 unit                   | success/failure/cancel/retry/cache/reuse                    |
| 현재 무료·청구 없음                     | 상위 비용 사용자, Agent Run drill-down, reconciliation 상태 |
| Provider/model/내부 원가 비노출         | 원문·prompt/response·API key 비노출                         |

## 5. P8.8 실패 운영

기능별 safe code는 versioned presentation mapping으로 사용자 category와 suggested action에 투영한다. 최소 matrix는 문서 parse·embedding/근거, 공고 URL/분석, 자기소개서 생성/검증, 면접 조사/답변 feedback, Agent Run list/detail, P9 mock turn/feedback이다.

운영 확인 항목:

- 같은 category가 같은 title/message/CTA를 사용하는가
- 기능 한도와 Provider 예산이 다른 category·code인가
- SSE 단절을 `CONNECTION_RECOVERING`으로 표시하고 run 실패로 오판하지 않는가
- `retryable=false`에 retry CTA가 없는가
- draft/partial result 보존과 `usageMayHaveOccurred`가 사실과 일치하는가
- request ID로 Backoffice finding을 찾을 수 있는가
- Provider/model/raw response/stacktrace가 노출되지 않는가

## 6. P8.9-A 읽기 전용 Backoffice

### 접근

- Backend가 `/api/v1/backoffice/**`를 ADMIN only로 인가한다.
- 일반 signup은 USER만 만든다.
- 배포 통제 provisioning command만 ADMIN을 부여하고 사유·actor·request ID를 audit한다.
- 사용자 검색, 사용자 상세, 비용·run drill-down 접근을 모두 audit한다.
- 일반 사용자 navigation과 AppLayout에는 Backoffice link를 넣지 않는다.

### KPI

```text
active users
feature usage
internal AI cost
run success/failure/cancel
retry count
failure category
feature limit rejection
budget rejection
provider readiness
aggregation lag
```

MRR, revenue, subscriber count, payment status, invoice와 refund는 표시하지 않는다.

### 장애 시

- 집계가 stale이면 watermark/lag와 마지막 정상 시각을 표시하고 추정치를 최신값처럼 보이지 않는다.
- Provider readiness는 configuration readiness와 live verification을 분리한다.
- audit 쓰기 실패 시 사용자 상세·drill-down을 fail-closed한다.
- 일반 사용자 요청 경로는 Backoffice query 장애와 분리한다.

## 7. 탈퇴·보존

- 탈퇴 purge는 사용자 원문과 owner-scoped 상세 event를 기존 개인정보 정책에 따라 삭제한다.
- 법적·운영 보존이 승인되지 않은 상태에서는 이메일과 raw user ID를 aggregate에 영구 보존하지 않는다.
- 비식별 상관키, 기간·feature·outcome·cost/quantity aggregate는 재식별할 수 없는 형태로만 보존한다.
- Agent Run soft delete는 비용·usage audit을 보존하되 일반 사용자와 Backoffice 기본 목록에서 원문을 제공하지 않는다.

## 8. Phase별 운영 인수

| Phase  | 필수 운영 검증                                                                                |
| ------ | --------------------------------------------------------------------------------------------- |
| P8.6   | 동시 초과 차단, replay 중복 없음, reserve terminal 정리, override 격리                        |
| P8.7   | raw↔read model 합계, 월/일 경계, 0-cost·failed·retry, immutable billing snapshot              |
| P8.8   | category/CTA matrix, 보존 안내, request ID, 접근성·한국어 문구, 내부 정보 비노출              |
| P8.9-A | USER 403/404 정책, ADMIN 조회, cross-user isolation, access audit, 원문 비노출, lag/readiness |

각 phase 시작 시 latest migration과 implemented OpenAPI baseline을 다시 확인한다. 미래 V14~V17과 API·route는 실제 merge 전까지 tentative/`PLANNED`로 유지한다.
