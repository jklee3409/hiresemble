# Progress

## Overview

P3 Agent Run list/detail projection, drawer와 Document·Job·Job Analysis·Cover Letter snapshot-first SSE 복구 기반이 사용자용 `AI 작업` 용어로 구현됐다.

## [2026-08-06] Session Summary (AI 작업 상세 단계명 전면 정리)

- What was done:
  - 현재와 legacy를 포함한 canonical workflow step 57개를 이해하기 쉬운 한국어 작업명으로 매핑하고, 알 수 없는 step의 fallback을 `작업 진행 내용`으로 바꿨다.
  - 상세 panel의 `실제로 진행한 작업을 이해하기 쉬운 이름으로 보여드려요.` 안내 문구를 제거했다.
- Key decisions:
  - backend step key와 API는 그대로 두고 presentation layer만 변환하며 내부 key·underscore·숫자 순번을 사용자에게 노출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 57개 key 전수 component test와 Frontend 전체 `check` 통과. Chromium 상세 화면에서 자기소개서 8단계 사용자명과 안내 문구·숫자 fallback 부재를 확인했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공통 활성 Run 조회)

- What was done:
  - 현재 사용자 기준 `QUEUED/RUNNING/WAITING_USER` Run을 최신순으로 조회하는 공통 query를 추가하고 빈 user ID에서는 요청을 비활성화했다.
- Key decisions:
  - page가 resource type/id를 좁혀 복구 대상을 선택하고 query 자체는 한 번의 owner-scoped 목록을 공유한다.
- Issues encountered:
  - None.
- Validation:
  - 사용하는 문서·공고·자기소개서·면접 page 회귀와 전체 Frontend check가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (진행 중 공고 분석 단계명 보정)

- What was done:
  - `JOB_ANALYSIS` 8개 내부 step key를 공고 분석 준비, 지원 요건 정리, 지원 가능 여부 확인, 관련 경험 찾기, 공고와 경험 비교, 직무 적합도 계산, 분석 결과 확인, 결과 저장의 사용자용 명칭으로 완성했다.
  - 완료된 단계와 현재 실행 중인 단계까지만 있는 Run 상세에서 순번 fallback·내부 key·미도달 단계가 노출되지 않는 회귀 테스트를 추가했다.
- Key decisions:
  - Backend가 실제 시작한 step row만 반환하는 계약을 유지하고 API·Workflow를 변경하지 않은 채 Frontend presentation 매핑만 보정한다.
- Issues encountered:
  - None.
- Validation:
  - `corepack pnpm exec vitest run src/features/agent-runs/AgentRunDetailPanel.test.ts` 9개 테스트와 Frontend 표준 `corepack pnpm check` 67 files/267 tests·typecheck·lint·format·build가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (AI 작업 사용자 문구 정리)

- What was done:
  - drawer·목록·상세의 `AI 작업 내역`과 품질 enum 중심 표현을 `AI 작업`, 빠른 처리·균형 처리·꼼꼼한 처리로 정리했다.
- Key decisions:
  - API enum·Run 상태는 바꾸지 않고 presentation label만 변경한다.
- Issues encountered:
  - None.
- Validation:
  - Agent Run presentation tests와 Frontend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (Job extraction v2 사용자 단계명)

- What was done:
  - page fetch·inspection·image read·validation·apply 내부 step key를 자연스러운 사용자 문구로 매핑했다.
- Key decisions:
  - Provider/OCR engine 이름과 내부 key는 노출하지 않고 SSE reconnect 의미는 run 실패와 분리한다.
- Issues encountered:
  - None.
- Validation:
  - Frontend check와 P5 Chromium 5/5 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (접힌 사용자용 AI 과정·사용량)

- What was done:
  - 기술 단계명을 사용자 단계명으로 변환하고 상세를 기본 접힘으로 변경했으며 USD 대신 작업 한도 비율을 표시했다.
  - 단계 및 run 전체 오류 원문을 렌더링하지 않고 안전한 복구 안내만 표시했다.
- Key decisions:
  - 현재 값은 결제액·월간 한도가 아님을 명시하고 집계가 없으면 값을 만들지 않는다.
- Issues encountered:
  - 브라우저에서 local-offline provider 비활성 문구 노출을 발견해 회귀 테스트와 함께 차단했다.
- Validation:
  - AgentRunDetailPanel tests, Frontend 전체 check, 실제 failed run accordion 확인 통과.
- Next steps:
  - P8.7 월간 집계 API가 생기면 별도 사용량 카드로 확장한다.

## [2026-07-31] Session Summary (AI 작업 내역 삭제 mutation)

- What was done:
  - 개별·선택 history delete mutation과 성공 시 대상 detail cache 제거·owner root invalidation을 추가했다.
- Key decisions:
  - 삭제 UI는 terminal 상태에만 열고 서버가 최종 owner·state를 원자 검증한다.
- Issues encountered:
  - None.
- Validation:
  - Agent Run page/API targeted tests와 Frontend 전체 215 tests 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Cover Letter resource 연결)

- What was done:
  - generation/verification workflow label, cover letter·answer version resource 이동과 terminal query invalidation을 추가했다.
- Key decisions:
  - 자기소개서 결과 본문은 AI 작업 내역에 복제하지 않고 진행·오류·비용·resource link만 표시한다.
- Issues encountered:
  - 없음.
- Validation:
  - Agent Run detail/stream unit test와 P7 actual 진행·resource link 시나리오가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (AI 작업 내역 용어·공고 분석 resource 연결)

- What was done:
  - sidebar·mobile·목록·상세·filter·빈 상태·pagination의 `분석 기록`을 `AI 작업 내역` 계열로 통일했다.
  - `JOB_ANALYSIS` Run은 결과를 복제하지 않고 `/jobs/:jobId/analysis` 연결만 제공하며 terminal 뒤 analysis query를 invalidate한다.
- Key decisions:
  - `/agent-runs` route·API·DTO와 workflow/status 의미는 변경하지 않는다.
- Issues encountered:
  - 기존 Document E2E accessible name도 같은 용어로 갱신했다.
- Validation:
  - Agent Run unit/component, reconnect·WAITING·retry·cancel fixture와 P6 Chromium 3/3이 통과했다.
- Next steps:
  - None.

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
