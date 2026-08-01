# Progress

## Overview

외부 AI Provider의 로컬 활성화·offline 전환·사용자 P8.5-V 검증 절차와, P8.6–P8.9의 기능 한도·usage accounting·실패 복구·Backoffice 운영 계획이 문서화되어 있다.

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
