# Progress

## Overview

P3 Agent Run domain·PostgreSQL·API·SSE와 P4~P8·GitHub·Career Artifact typed resource 통합 검증이 구현됐다.

## [2026-08-08] Session Summary (Career Artifact Agent Run 회귀)

- What was done:
  - typed resource, retry private request 복사·새 target ID, cancel/interruption와 history delete compensation을 검증했다.
- Key decisions:
  - 공유 가격 fixture는 현재 catalog와 겹치지 않는 닫힌 과거 구간을 사용한다.
- Issues encountered:
  - open-ended fake 가격 row가 이후 통합 test context를 오염시켜 기존 provider/job 기대를 깨뜨렸다.
- Validation:
  - Career Artifact 집중 test와 기존 workflow 회귀 통과.
- Next steps:
  - None.

## [2026-08-03] Session Summary (프로필 eligibility 기반 재시도 fixture 정합성)

- What was done:
  - Agent Run 공통 `seedUser` fixture가 `user_profiles`와 함께 `profile_eligibility_declarations`의 기본 `UNSPECIFIED` 행도 생성하도록 보강했다.
- Key decisions:
  - production 조회 로직을 완화하지 않고 실제 signup과 동일한 필수 profile snapshot 전제만 테스트 사용자에 적용했다.
- Issues encountered:
  - Backend 전체 check의 Cover Letter generation/verification retry 2건이 eligibility 선언 부재로 `RESOURCE_NOT_FOUND`를 반환했다.
- Validation:
  - `CoverLetterAgentRunIntegrationTest` 집중 실행과 Backend 전체 `check` 525건이 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (Agent Run history delete 통합 검증)

- What was done:
  - terminal 개별·선택 삭제, active conflict, foreign ID atomic rollback, 공개 조회 제외와 budget reservation 보존을 검증했다.
- Key decisions:
  - 물리 삭제가 아니라 `deleted_at`과 owner-visible behavior를 assertion 대상으로 삼았다.
- Issues encountered:
  - None.
- Validation:
  - `AgentRunApiSseIntegrationTest` 8 tests와 Backend 전체 385 tests 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (Cover Letter Agent Run 통합 검증)

- What was done:
  - generation cover letter link, verification answer version link, owner isolation, retry lineage·partial seed·cancel과 raw content 부재를 검증했다.
- Key decisions:
  - Run은 진행·비용·resource link만 소유하고 자기소개서 결과 본문을 저장하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - `CoverLetterAgentRunIntegrationTest`와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-19] Session Summary (Agent Run P3 통합 테스트 구현)

- What was done:
  - claim 경쟁, queue saturation, restart·stale recovery와 heartbeat lease를 검증했다.
  - blocking gateway 중 주기 heartbeat가 stale reconciliation을 차단하는 실제 PostgreSQL 경계를 검증했다.
  - budget 경쟁·날짜·settle/release, retry lineage·reuse, cancel·compensation과 SSE race를 검증했다.

- Key decisions:
  - 실제 provider와 production Fake endpoint 없이 내부 port와 격리 DB를 사용한다.

- Issues encountered:
  - 기존 idempotency fixture를 V4 owner FK에 맞춰 실제 Run을 생성하도록 보정했다.

- Validation:
  - Backend 전체 243 tests가 failure·error·skip 0으로 통과했다.

- Next steps:
  - typed resource를 포함한 retry/apply E2E는 P4 이후 추가한다.
