# Progress

## Overview

Document·Job 생성 mutation이 사용하는 DB 기반 HMAC reservation, 원 응답 replay와 Agent Run successor metadata를 제공한다.

## [2026-07-28] Session Summary (Idempotency key startup fail-closed)

- What was done:
  - active hash key가 없거나 비어 있으면 application context 초기화에서 즉시 실패하도록 properties 검증을 추가했다.
- Key decisions:
  - 알려진 개발 키는 명시적 local profile에서만 제공하고 profile 미지정·비로컬 환경은 환경 secret을 요구한다.
- Issues encountered:
  - 전역 default local profile은 배포 설정 누락 시 알려진 key를 사용할 수 있어 제거했다.
- Validation:
  - Properties 단위 회귀, Backend 전체 check와 실제 P4 Browser E2E 4/4가 통과했다.
- Next steps:
  - 운영 key rotation에서는 current version과 과거 hash key map을 함께 제공한다.

## [2026-07-27] Session Summary (Job 201/202 replay 연결)

- What was done:
  - 최초 HTTP status와 Job·Run ID를 그대로 replay하도록 prepared/transaction 실행 경계를 확장했다.
- Key decisions:
  - 같은 key의 다른 request hash는 409이고 URL-only 생성의 linked Run reservation은 reconciliation까지 보존한다.
- Issues encountered:
  - 없음.
- Validation:
  - 수동 201·비동기 202 replay와 key reuse 충돌 통합 테스트가 통과했다.
- Next steps:
  - 없음.

## [2026-07-19] Session Summary (Document prepared idempotency transaction 연결)

- What was done:
  - 외부 Object 준비 뒤 business mutation과 idempotency 완료 응답을 한 transaction에서 커밋하고 실패 시 준비 결과를 보상하는 `executePrepared` 경계를 추가했다.

- Key decisions:
  - P1~P3 기존 `execute` 동작은 유지하고 최초 실제 aggregate인 Document upload만 prepared 경계를 사용한다.

- Issues encountered:
  - Object 저장을 DB transaction 밖에 유지하면서 완료 응답 실패까지 보상하려면 preparation·operation·compensation을 분리해야 했다.

- Validation:
  - idempotency 완료 trigger 실패 시 Document·Run·budget rollback과 Object 삭제를 실제 PostgreSQL에서 검증했다.

- Next steps:
  - 후속 외부 side effect 기반 aggregate도 같은 prepared 경계를 재사용할지 각 phase에서 결정한다.

## [2026-07-19] Session Summary (Agent Run retry idempotency metadata 연결)

- What was done:
  - COMPLETED record에 nullable resource pair와 agentRunId를 원자 저장하고 replay가 successor를 반환하도록 연결했다.

- Key decisions:
  - retry 요청 body는 없으므로 canonical body hash는 `{}`이고 predecessor unique가 다른 key 경쟁도 제한한다.

- Issues encountered:
  - owner FK 때문에 기존 가상 agentRunId fixture를 실제 owner Run으로 변경했다.

- Validation:
  - replay, generic hash mismatch, concurrent retry와 predecessor당 successor 하나가 통과했다.

- Next steps:
  - typed resource metadata는 해당 domain phase에서 연결한다.

## [2026-07-19] Session Summary (Durable idempotency 저장·hash·replay 기반 구현)

- What was done:
  - unique scope reservation, versioned HMAC hash, IN_PROGRESS 충돌, COMPLETED replay와 만료 후 원자 reclaim을 구현했다.

- Key decisions:
  - HMAC key는 설정에서만 읽고 DB에는 key와 canonical 원문을 저장하지 않으며 완료 TTL은 24시간이다.
  - 만료 linked IN_PROGRESS는 자동 회수하지 않고 run terminal 상태를 확인할 후속 reconciliation까지 보호한다.

- Issues encountered:
  - PostgreSQL JDBC가 Instant type을 추론하지 못해 UTC OffsetDateTime binding으로 명시했다.
  - 1차 validator가 만료 시각만 저장한 record가 영구 replay되는 문제를 발견해 조건부 upsert로 보정했다.

- Validation:
  - 기존 replay·충돌·재시작·민감정보 test와 만료 새 실행·동시 reclaim·linked row 보호 test가 통과했다.

- Next steps:
  - P2 이후 첫 적용 endpoint에서 business resource와 완료 record의 transaction 경계를 구체화한다.
