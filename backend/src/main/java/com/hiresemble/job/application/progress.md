# Progress

## Overview

P5 Job과 P6 Job Analysis use case, transaction·Clock·Agent Run 조정 경계, durable 자동 분석 coordinator와 P8 preparation projection port가 구현됐다.

## [2026-08-05] Session Summary (공고 목록 기간 검증)

- What was done:
  - 연도·반기 pair, preset/직접 설정 상호 배타성, 미래 시작일을 검증하고 서울 시작일~현재 시각 조회 범위를 계산했다.
- Key decisions:
  - 기간 종료 parameter는 공개하지 않고 server Clock의 오늘을 사용한다.
- Issues encountered:
  - None.
- Validation:
  - 유효·부분 preset·혼합 방식·미래 날짜 Job 통합 시나리오와 전체 check 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (criterion별 hybrid evidence 검색)

- What was done:
  - semantic 검색 상한에 lexical slot을 예약해 direct evidence가 vector chunk에 밀리지 않게 했다.
- Key decisions:
  - workflow가 criterion마다 최대 5개를 요청하고 application은 semantic 3개·lexical 최대 2개를 조합한다.
- Issues encountered:
  - 기존 semantic 결과가 limit을 채우면 career 등 direct evidence lexical fallback이 실행되지 않았다.
- Validation:
  - workflow query별 검색 호출과 allowlist/hash 검증 회귀 통과.
- Next steps:
  - direct evidence 자체 embedding 영속화는 별도 성능 확장으로 남긴다.

## [2026-08-03] Session Summary (canonical requirement compatibility 소비)

- What was done:
  - persist validation이 requirement text를 다시 keyword 분류하지 않고 Workflow의 canonical support type/category/date만 typed evidence/fact compatibility에 사용하게 했다.
- Key decisions:
  - positive support 불변식과 public analysis projection은 유지한다.
- Issues encountered:
  - None.
- Validation:
  - 학력·자격증·어학·근무일·자기신고 compatibility와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-03] Session Summary (자동 분석 AFTER_COMMIT connection 대기 해소)

- What was done:
  - `JobAutoAnalysisCoordinator.onRequested`를 감싸던 외부 `REQUIRES_NEW`를 제거하고 기존 store claim/mark와 launch의 짧은 독립 트랜잭션만 유지했다.
- Key decisions:
  - durable intent 상태 전이와 launch의 `REQUIRES_NEW` 계약은 그대로 유지했다. listener는 트랜잭션을 오래 점유하지 않는 현재 설계에 맞췄다.
- Issues encountered:
  - thread dump에서 Test worker가 `launchAutomatic`의 connection 획득을 기다렸고, listener transaction과 내부 run creation이 pool size 2를 모두 점유한 사실을 확인했다.
- Validation:
  - 종전 184초 대기하던 단일 `JobAnalysisIntegrationTest` 시나리오가 38초에 통과했고, 전체 `JobAnalysisIntegrationTest`와 `JobAutoAnalysisIntegrationTest`도 각각 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (자동 분석 intent·coordinator)

- What was done:
  - job transaction 안에서 intent를 enqueue하고 commit 뒤 claim하며 scheduler가 미완료 요청을 재조정하는 bounded coordinator를 추가했다.
  - 기존 분석 service에 BALANCED·requested run ID를 받는 `REQUIRES_NEW` 자동 실행 경계를 추가했다.
- Key decisions:
  - 공고 revision마다 하나의 intent와 결정적 run ID를 재사용하고 최대 시도·lease 만료 뒤에만 재claim한다.
- Issues encountered:
  - budget 실패는 BLOCKED safe error로 보존하고 공고 생성 transaction과 분리했다.
- Validation:
  - duplicate replay, crash/restart reuse와 quota 보존 통합 테스트 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (Job extraction canonical retry contributor)

- What was done:
  - 신규·retry input factory와 Job 전용 retry contributor를 추가했다.
- Key decisions:
  - predecessor는 불변으로 두고 현재 Job version·URL·override hash로 v3 successor를 원자 생성한다.
- Issues encountered:
  - resource/generic 호출 순서 모두 같은 successor를 반환하도록 latest successor compatibility를 명시했다.
- Validation:
  - v1 FAILED, v1 INTERRUPTED, v2 FAILED, v3 retry와 양방향 endpoint 회귀 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 owner-aware Job resource 연결)

- What was done:
  - Job typed resource owner adapter를 강화된 user-aware resolver 계약에 맞추고 interview projection query를 제공했다.
- Key decisions:
  - 면접 준비 prerequisite는 P8 application service가 latest analysis·active cover letter와 함께 최종 검증한다.
- Issues encountered:
  - None.
- Validation:
  - cross-user 404, prerequisite와 final-source P6 actual 회귀가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 분석 application 경계)

- What was done:
  - 분석 접수·snapshot·retrieval·reuse·persist·OUTDATED projection을 단일 application service 경계로 추가했다.
- Key decisions:
  - 외부 호출은 transaction 밖, 최종 apply는 serializable transaction에서 owner/version/hash를 다시 검증한다.
- Issues encountered:
  - 없음.
- Validation:
  - Job Analysis 통합 3개와 전체 Backend check가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (Job application use case 구현)

- What was done:
  - 수동/URL 생성, WAITING_USER resume, terminal retry, 상태·삭제와 batch Scheduler를 구현했다.
- Key decisions:
  - Job과 최초 Run은 같은 transaction, URL fetch는 transaction 밖, 상태와 history는 한 transaction이다.
- Issues encountered:
  - Scheduler와 사용자 command 경쟁은 DB 조건부 update와 version으로 한쪽만 성공하게 고정했다.
- Validation:
  - owner 404, version conflict, retry/resume, soft delete와 Scheduler race 통합 테스트가 통과했다.
- Next steps:
  - P6 분석 use case는 별도 application 경계로 추가한다.
