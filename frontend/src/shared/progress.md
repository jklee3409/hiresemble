# Progress

## Overview

여러 화면이 공유하는 P1 인증부터 P5 Job HTTP 계약, Session cleanup port와 비도메인 UI primitive를 관리한다.

## [2026-07-27] Session Summary (공용 UI 표현 기반 추가)

- What was done:
  - `ui/`에 SVG icon, page header, text status, loading·empty·error state와 pagination component를 추가했다.
  - 기존 `api/`와 `session/` 코드는 변경하지 않고 layout·page·feature의 반복 presentation만 공용화했다.
- Key decisions:
  - transport·cleanup과 UI primitive는 같은 shared 상위에 두되 서로의 domain 책임을 침범하지 않는다.
  - status component는 label을 필수로 받아 색상만으로 상태를 전달하지 않는다.
- Issues encountered:
  - 공용화 범위를 넓히지 않도록 두 곳 이상 반복되는 접근성·표현 책임만 추출했다.
- Validation:
  - 공용 UI component test와 전체 Frontend check가 통과하고 API/session diff가 없음을 확인했다.
- Next steps:
  - 새 API나 미래 domain primitive를 선행 추가하지 않는다.

## [2026-07-27] Session Summary (P5 Job 공용 API·cleanup 연결)

- What was done:
  - strict Job DTO/client와 Job별 stream/query cleanup을 공용 lifecycle에 연결했다.
- Key decisions:
  - transport와 cleanup만 shared에 두고 Job 화면 상태는 feature/page가 소유한다.
- Issues encountered:
  - 없음.
- Validation:
  - Zod rejection, logout·사용자 전환 cleanup과 전체 Frontend check가 통과했다.
- Next steps:
  - P6 분석 계약은 Backend OpenAPI 구현 후 추가한다.

## [2026-07-19] Session Summary (P3 Agent Run 공용 API·Session 경계 연결)

- What was done:
  - Agent Run DTO/client를 추가하고 SSE controller를 기존 Session cleanup port에 연결했다.

- Key decisions:
  - transport와 lifecycle만 shared에 두고 화면 상태는 feature/page가 소유한다.

- Issues encountered:
  - None.

- Validation:
  - typed API와 logout·401·사용자 전환 cleanup tests가 통과했다.

- Next steps:
  - 후속 resource API도 user-scoped query key를 사용한다.

## [2026-07-19] Session Summary (P2 profile 공용 API 확장)

- What was done:
  - 공용 API 영역에 profile TypeScript 계약과 25개 endpoint consumer를 추가했다.

- Key decisions:
  - 기존 logout·401·사용자 전환 cleanup 순서를 profile Vue Query cache에도 적용한다.

- Issues encountered:
  - None

- Validation:
  - API·cache 분리 테스트와 frontend 전체 check, 실제 사용자 전환 E2E가 통과했다.

- Next steps:
  - P3 이후 transport는 실제 사용처와 함께 추가한다.

## [2026-07-19] Session Summary (P1 공용 API·Session 기반 구성)

- What was done:
  - typed 인증 client와 사용자 경계 cleanup coordinator를 추가했다.

- Key decisions:
  - 직접 성공 DTO를 사용하고 401/403/409를 안정적 code로 구분한다.

- Issues encountered:
  - None

- Validation:
  - HTTP adapter와 cleanup ordering unit test가 통과했다.

- Next steps:
  - 새 공용 책임은 두 실제 사용처가 생긴 뒤 최소 범위로 추가한다.
