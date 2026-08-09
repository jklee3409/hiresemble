# Progress

## Overview

public/private GitHub Source와 GitHub App connection API 구현 상태를 추적한다.

## [2026-08-09] Session Summary (GitHub App 7-operation API)

- What was done: capability/start/setup callback/OAuth callback/list/refresh/disconnect와 additive source DTO를 구현했다.
- Key decisions: callback은 one-time state로 보호하고 고정 result code로만 redirect하며 owner mismatch는 404다.
- Issues encountered: OpenAPI account description 누락과 count fixture를 최종 실제 수로 보정했다.
- Validation: private on 97/127, off 90/120 OpenAPI와 Backend 전체 check가 통과했다.
- Next steps: 실제 App UAT는 사용자 검증 대기다.

## [2026-08-07] Session Summary (GitHub Source 7개 operation)

- What was done: 등록·목록·상세·repository 목록·선택·refresh·delete Controller/DTO/mapper를 구현했다.
- Key decisions: Session/CSRF, owner 404, version, idempotency와 내부 snapshot 값 비노출을 유지한다.
- Issues encountered: refresh의 unchanged 200과 changed 202를 같은 DTO로 안전하게 투영했다.
- Validation: MockMvc API·OpenAPI·gateway error mapping 테스트가 통과했다.
- Next steps: Gate 2 runtime validation과 API client를 연결한다.
