# Progress

## Overview

외부 AI Provider의 로컬 활성화·offline 전환·사용자 P8.5-V 검증 절차와, P8.6–P8.9의 기능 한도·usage accounting·실패 복구·Backoffice 운영 계획이 문서화되어 있다.

## [2026-08-06] Session Summary (선택 가능 OpenAI 모델 운영 기준)

- What was done: active 가격 version과 선택 model 10개, 공식 문서 보고서 링크를 provider activation 절차에 반영했다.
- Key decisions: model 존재 여부와 계정 entitlement를 구분하고 배포 전 제한된 실제 smoke call로 확인한다.
- Issues encountered: 실제 유료 API 호출은 사용자 승인 범위가 아니어서 수행하지 않았다.
- Validation: 문서·설정·V23 migration 일치와 Backend 전체 `check`를 확인했다.
- Next steps: 운영 key로 모델별 entitlement와 비용 한도를 확인한다.

## [2026-08-02] Session Summary (V16 자동 분석 반영 운영 번호 정렬)

- What was done:
  - 실제 V16 공고 자동 분석 intent 반영에 따라 P8.6 이후 운영 migration 범위를 V17~V20으로 이동했다.
- Key decisions:
  - 이미 적용된 V1~V16은 수정하지 않고 미래 운영 schema만 tentative로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - DB 명세·implementation plan·운영 phase 번호 대조 완료.
- Next steps:
  - P8.6 시작 시 V17 availability를 다시 확인한다.

## [2026-08-01] Session Summary (AI 사용량 사용자 표현 경계)

- What was done:
  - 현재 작업 단위 예약/실집계 비율과 향후 월간 집계의 경계를 운영 문서에 반영하고 후속 migration 번호를 V17로 이동했다.
- Key decisions:
  - 실제 결제액이나 월간 총량으로 오해할 수 있는 USD 표시는 제공하지 않는다.
- Issues encountered:
  - 현재 public API에는 월간 누적·잔여 한도가 없다.
- Validation:
  - Agent Run DTO와 Frontend 표시, P8.7 운영 계약을 대조했다.
- Next steps:
  - P8.7에서 원화 참고 금액이 필요한지 가격·환율 정책과 함께 별도 결정한다.

## [2026-08-01] Session Summary (문서 terminal 보정 bounded handoff)

- What was done:
  - 최신 live run의 Chat strict output·evidence 저장·finalize 성공과 terminal 오분류를 구분해 기록했다.
- Key decisions:
  - 성공한 Chat·Embedding·Tavily를 반복하지 않고 문서 terminal 결과만 1회 재검증한다.
- Issues encountered:
  - 이번 구현·검증에서는 실제 Provider를 호출하지 않았다.
- Validation:
  - runbook 명령과 persistent call counter 정책을 저장소 구현에 대조했다.
- Next steps:
  - safe run ID·상태·usage/cost만 기록하고 실패 시 같은 요청을 반복하지 않는다.

## [2026-08-01] Session Summary (semantic 보정 bounded live handoff)

- What was done:
  - schema 수락 뒤 3회 semantic failure·비용·보존 상태와 새 phase/reason 확인 절차를 runbook에 기록했다.
- Key decisions:
  - 성공한 Embedding/Tavily는 반복하지 않고 synthetic Chat 1회 성공 시에만 문서 ingestion 1회를 수행한다.
- Issues encountered:
  - 과거 raw output·finish reason을 조회하지 않으므로 live 원인은 미확정이며 persistent Chat counter가 절대 상한 2에 도달했다.
- Validation:
  - `codexRealOpenAiChatTest` task 존재를 확인했고 이번 작업의 Provider 호출은 0회다.
- Next steps:
  - counter를 우회하지 않고 사용자가 versioned 1회 allowance를 별도 승인한 뒤 Chat→document 순서로 검증한다.

## [2026-08-01] Session Summary (strict Chat 보정 재검증 handoff)

- What was done:
  - 실제 문서 실행의 Embedding 성공·Chat strict 실패와 offline 보정 결과를 runbook에 기록하고 capability별 Chat task를 명시했다.
- Key decisions:
  - 수정 뒤 Chat task 1회, 성공 시 문서 vertical 1회만 수행한다.
- Issues encountered:
  - 당시 Provider raw code·param/request ID는 복구 불가하다.
- Validation:
  - 명령이 실제 Gradle task로 등록됐음을 확인했고 이번 작업의 Provider 호출은 0회다.
- Next steps:
  - 사용자만 key를 주입해 safe ID·usage 합계만 기록한다.

## [2026-08-01] Session Summary (OpenAI `/v1`와 bounded smoke 결과)

- What was done:
  - activation runbook의 OpenAI base URL과 실제 capability 시도 결과를 갱신했다.
- Key decisions:
  - OpenAI quota 복구 전에는 capability verified로 판정하지 않는다.
- Issues encountered:
  - Chat·Embedding `insufficient_quota`; Tavily BASIC 성공.
- Validation:
  - secret·prompt·response 없이 call count와 safe Provider code만 기록했다.
- Next steps:
  - quota 복구 후 Chat·Embedding만 제한 재검증한다.

## [2026-08-01] Session Summary (사용량·실패 복구·Backoffice 운영 계획)

- What was done:
  - 실제 Provider 검증 runbook을 capability smoke와 P4~P8 vertical flow로 분리하고, 사용량 집계·reconciliation·Backoffice 운영 계획을 추가했다.
  - live 기록에 허용되는 request ID·Agent Run ID·합계와 금지되는 key·prompt·원문 경계를 명시했다.
- Key decisions:
  - 일반 `local`은 실제 Provider fail-closed, `local-offline`은 disabled, test·CI·E2E는 Fake 또는 disabled와 외부 network 0을 유지한다.
  - 운영 조회는 P8.9-A read-only로 먼저 제공하고 mutation은 감사 가능한 P8.9-B로 미룬다.
- Issues encountered:
  - 현재 실제 Provider 호출이 Chat 0, Embedding 0, Tavily 0이라 P8.5-V는 사용자 검증 대기로 남겼다.
- Validation:
  - 운영 절차와 설계·명세의 상태, 공개 정보, privacy 경계를 대조하고 링크·Markdown 검사를 통과했다.
- Next steps:
  - 사용자가 key를 노출하지 않고 P8.5-V를 1회 수행해 capability와 기능 품질 결과를 분리 기록한다.

## [2026-07-31] Session Summary (P8.5 AI Provider 활성화 운영 절차)

- What was done:
  - local/local-offline 실행, Secret 주입, immutable 가격 version, Codex bounded live 검증과 장애 시 명시적 disable 절차를 기록했다.
- Key decisions:
  - 자동 Fake fallback을 두지 않고 test·CI·E2E만 network-disabled로 고정한다.
- Issues encountered:
  - 실제 key와 live test gate가 없어 bounded real-provider 검증은 실행하지 않았다.
- Validation:
  - local/local-offline Bean matrix와 Codex task skip gate를 검증했다.
- Next steps:
  - 승인된 환경에서 capability별 bounded live 검증을 1회 실행한다.
