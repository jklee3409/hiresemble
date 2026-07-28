# Progress

## Overview

P3 Agent Run list/detail projection, drawer와 Document·Job snapshot-first SSE 복구 기반이 구현됐다.

## [2026-07-28] Session Summary (분석 상세 내부 실행 정보 제거)

- What was done:
  - 상세 화면에서 요청 quality, model tier와 예약 비용을 제거하고 진행률·시간·예상 사용 비용·다음 행동만 남겼다.
  - 목록 filter는 mobile disclosure로 정리하되 workflow/status URL query 계약은 유지했다.
- Key decisions:
  - DTO 필드는 그대로 소비하되 사용자 화면에는 provider/model/prompt/hash와 내부 비용 예약 정보를 표시하지 않는다.
- Issues encountered:
  - 기존 component test의 model label assertion을 내부 정보 미노출 assertion으로 교체했다.
- Validation:
  - Agent Run component test와 reconnect·polling·retry·cancel fixture Playwright 2/2가 통과했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (Agent Run 사용자 용어를 분석 기록으로 전환)

- What was done:
  - `AI 작업`을 문맥별 `진행 중인 분석`, `분석 기록`, `분석 진행 상황`으로 바꿨다.
- Key decisions:
  - 내부 Agent Run·workflow·status 타입과 route는 유지했다.
- Issues encountered:
  - 기존 E2E accessible name을 새 용어와 맞췄다.
- Validation:
  - Agent Run unit/component와 Playwright fixture 2/2가 통과했다.
- Next steps:
  - 새 workflow가 추가될 때 사용자 결과 중심 label을 함께 정의한다.

## [2026-07-28] Session Summary (AI 작업 소비자 경험 재설계)

- What was done:
  - 제품 화면의 Agent Run을 `AI 작업`, workflow를 `작업 종류`, 비용을 `예상 사용 비용`, timeline을 `진행 단계`로 통일했다.
  - 작업 종류 label을 사용자의 실제 행동 결과로 다시 작성했다.
  - `currentStep`, `stepKey`, scope key와 fallback resource type은 화면에서 숨기고 상태 문장·번호 단계·완료 개수로 표현했다.
- Key decisions:
  - SSE reconnect·polling은 `진행 상황을 다시 확인하는 중`으로만 안내하고 run business 상태와 계속 분리한다.
- Issues encountered:
  - 최초 validator가 fixture 내부 단계 key가 그대로 보이는 문제를 찾아 사용자 의미를 유지하는 표시 formatter로 보정했다.
- Validation:
  - detail/drawer component test와 reconnect·polling·retry·cancel·logout Chromium fixture가 통과했다.
- Next steps:
  - provider·model·prompt·hash 같은 내부 정보는 계속 노출하지 않는다.

## [2026-07-27] Session Summary (Agent Run 운영 기록 UI 개선)

- What was done:
  - detail의 전체 상태·진행률·timeline·비용·소요 시간·safe error·required action 계층과 Progress Drawer의 compact 목록을 개선했다.
- Key decisions:
  - Run business 상태와 SSE reconnect/polling 상태를 계속 분리하고 provider·model·prompt·hash를 노출하지 않는다.
- Issues encountered:
  - drawer accessible name을 기존 테스트·사용자 계약과 동일하게 복구했다.
- Validation:
  - component/unit test와 reconnect·polling·retry·cancel fixture Chromium 2/2가 통과했다.
- Next steps:
  - 실제 provider 내부 정보는 후속 UI에도 추가하지 않는다.

## [2026-07-27] Session Summary (Job resource stream 재사용)

- What was done:
  - Job terminal·WAITING_USER event의 목록·상세·Run query invalidation과 삭제/user 전환 stream cleanup을 연결했다.
- Key decisions:
  - 새로운 SSE client를 만들지 않고 기존 `AgentRunStreamController`를 재사용한다.
- Issues encountered:
  - 없음.
- Validation:
  - stream unit test, logout·사용자 전환 test와 P5 Browser E2E가 통과했다.
- Next steps:
  - P6에서도 동일 stream을 분석 Run에 재사용한다.

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
