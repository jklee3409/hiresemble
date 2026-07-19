# Progress

## Overview

P3 Agent Run list/detail projection, drawer와 snapshot-first SSE 복구 기반이 구현됐다.

## [2026-07-19] Session Summary (Document resource stream 재사용)

- What was done:
  - Document Run deep link, terminal·WAITING_USER invalidation과 document별 stream close를 추가했다.
- Key decisions:
  - REST Document 상태를 최종 원천으로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - P3 Playwright 2/2와 P4 실제 SSE 시나리오가 통과했다.
- Next steps:
  - 다른 resource stream도 같은 cleanup 경계를 재사용한다.

## [2026-07-19] Session Summary (Agent Run UI와 SSE 복구 구현)

- What was done:
  - filter·pagination·sort URL canonicalization, list/detail query와 retry·cancel mutation을 구현했다.
  - 모든 SSE event merge, monotonic stateVersion, terminal cleanup과 resource query invalidation을 구현했다.
  - WAITING action, safe error, 단계 timeline, 비용 안내와 최근 active Run drawer를 구현했다.

- Key decisions:
  - 재연결은 1초, 2초, 5초에 총 3회이며 세 번째 실패 뒤 5초 REST polling으로 전환한다.
  - 명세의 10초·30초 backoff 값은 이번 3회 threshold에서 사용하지 않는다.
  - SSE 연결 상태는 마지막 snapshot 위에 안내만 표시하고 Run status를 변경하지 않는다.
  - Header count는 같은 owner-scoped active 목록의 `totalElements`를 사용하고 최근 항목은 최대 5개만 표시한다.

- Issues encountered:
  - Playwright의 중복 `45%` locator를 progressbar attribute로 좁혔다.
  - repeatable canonical query test가 배열 값을 정확히 기대하도록 보정했다.

- Validation:
  - Frontend 전체 20 files/78 tests와 Agent Run Chromium fixture 2/2가 통과했다.
  - main JS 508.47 kB/gzip 140.83 kB이며 Drawer·List·Detail은 별도 lazy chunk다.

- Next steps:
  - P4 이후 typed resource가 생기면 실제 resource query invalidation과 deep link를 연결한다.
