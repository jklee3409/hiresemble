# Progress

## Overview

비용·생성 mutation이 사용하는 DB 기반 HMAC reservation, 원 응답 replay와 P3 Agent Run successor metadata를 제공한다.

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
