# Progress

## Overview

P4 Document부터 P8 Interview까지 pipeline을 격리 PostgreSQL 18+pgvector·MinIO가 필요한 단계만 사용하고 Spring·Vue·Fake gateway·Chromium으로 검증한다.

## [2026-08-04] Session Summary (P6/P7 requirements v6 Fake 계약)

- What was done:
  - P6/P7 Fake chat gateway가 실제 요청의 sourceBlocks에서 ID·text·ordinal을 복사해 source-output-v6만 반환하도록 갱신했다.
- Key decisions:
  - E2E Fake에서도 제거된 sourceSection/sourceLocation을 재생성하지 않는다.
- Issues encountered:
  - `p6BrowserE2eTest p7BrowserE2eTest`는 P6 Chrome 대기로 3분 timeout됐다. P7 단독은 Job Analysis 이전 문서 정리 UI가 120초 안에 나타나지 않아 실패했다.
- Validation:
  - 두 fixture는 `compileTestJava`와 전체 `check`에서 컴파일됐다. P6/P7 Job Analysis 브라우저 경로는 미검증이다.
- Next steps:
  - 범위 밖 browser/document 대기 문제를 해결한 후 전용 E2E를 재검증한다.

## [2026-08-04] Session Summary (P6/P7 source requirements v5 fixture)

- What was done:
  - P6/P7 Fake provider의 Job requirement schema dispatch와 응답을 source-output-v5로 올렸다.
- Key decisions:
  - E2E 공고 원문과 동일한 source text를 유지해 server-owned block 검증을 통과시킨다.
- Issues encountered:
  - canonical workflow schema가 v5로 변경돼 구 v4 dispatch가 호출되지 않았다.
- Validation:
  - Backend 전체 `check` 79 suites/538 tests에서 P6/P7 E2E compile·실행 통과.
- Next steps:
  - None.

## [2026-08-03] Session Summary (P6/P7 source requirements v4 fixture)

- What was done:
  - P6/P7 Fake Job Analysis requirements를 source-only v4와 한국어 source provenance로 갱신했다.
- Key decisions:
  - 외부 Provider 호출 없이 기존 공개 Browser/API/DB 시나리오를 유지한다.
- Issues encountered:
  - `p6BrowserE2eTest p7BrowserE2eTest`는 P6 두 시나리오가 분석 전 `직접 입력해서 등록` locator를 찾지 못해 300초·240초 timeout됐고 P7은 선행 task 실패로 미실행이다.
- Validation:
  - fixture compile과 Backend 전체 check는 통과했다. P6/P7 전용 Chromium E2E는 위 UI harness failure로 미통과다.
- Next steps:
  - 현재 공고 등록 화면 계약에 맞춰 범위 밖 locator를 보정한 뒤 P6/P7 전용 task를 재실행한다.

## [2026-08-02] Session Summary (P6/P7 Job Analysis Provider fixture v2)

- What was done:
  - P6/P7 Fake Chat의 requirements·eligibility·match 응답을 실제 Provider DTO와 schema v2로 갱신했다.
- Key decisions:
  - 브라우저/API/DB 시나리오는 유지하고 실제 외부 Provider는 호출하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - P6/P7 Spring 통합 fixture는 통과했다. 전체 check는 범위 밖 Object Deletion Outbox 2건 실패로 미통과했고, 별도 Chromium task는 이번 범위에서 실행하지 않았다.
- Next steps:
  - None.

## [2026-08-01] Session Summary (P4/P7 local-ref fixture 회귀)

- What was done:
  - P4/P7 Fake Chat fixture를 문서 evidence output v2와 `chunkRef` 입력 계약으로 갱신했다.
- Key decisions:
  - 브라우저 시나리오와 공개 metadata 계약은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Backend full check에서 P4/P7 E2E test 통과.
- Next steps:
  - 실제 Provider UI 검증은 별도 1회 gate로 수행한다.

## [2026-08-01] Session Summary (evidence Provider fixture 계약 동기화)

- What was done:
  - P4/P7 browser fixture의 evidence metadata와 P7 answer TipTap을 strict-compatible Provider output으로 맞췄다.
- Key decisions:
  - 실제 공개 metadata object와 사용자 시나리오는 변경하지 않는다.
- Issues encountered:
  - 브라우저 actual wrapper는 이번 offline 수정에서 재실행하지 않았다.
- Validation:
  - Backend check에서 fixture compile 완료; actual E2E `NOT_RUN`.
- Next steps:
  - 다음 final-source actual 회귀에서 기존 P4/P7 wrapper를 실행한다.

## [2026-08-01] Session Summary (P8.5 전체 actual 회귀)

- What was done:
  - Provider Bean·port 변경 뒤 P8/P7/P6/P5/P4 actual wrapper를 모두 재실행했다.
- Key decisions:
  - actual E2E는 계속 Fake/disabled gateway만 사용하며 외부 network를 금지한다.
- Issues encountered:
  - P7의 일시적 optimistic-lock race는 standalone 재검증으로 닫았고 P5의 과거 UI 문구 fixture는 현재 계약으로 보정했다.
- Validation:
  - P8 1/1, P7 1/1, P6 2/2, P5 5/5, P4 4/4가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 Browser wrapper·DB audit)

- What was done:
  - 격리 PostgreSQL·Fake Chat/Search·Spring random port·Vue·Chromium을 조정하고 browser 종료 뒤 P8 provenance·version·lineage·usage를 DB에서 검증했다.
- Key decisions:
  - P8에 필요 없는 MinIO는 실행하지 않고 Playwright exit code와 DB assertion을 모두 task 결과로 판정한다.
- Issues encountered:
  - fixture terminal timestamp의 JVM/DB clock skew를 제품 로직 변경 없이 test helper에서 보정했다.
- Validation:
  - `p8BrowserE2eTest` Java 1/1·Chromium 1/1과 final-source P7/P6 회귀가 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 문항 409 포함 final-source Browser E2E)

- What was done:
  - API 선행 변경으로 nested question version 충돌을 만든 뒤 UI가 최신 문항·최대 글자 수·메모와 최초 사용자 snapshot을 표시하고 최신 CAS로 명시적 재적용하는 흐름을 actual spec에 추가했다.
- Key decisions:
  - title aggregate 충돌만으로 409 UX를 대표하지 않고 question resource의 optimistic version도 실제 Backend에서 검증한다.
- Issues encountered:
  - 없음.
- Validation:
  - `p7BrowserE2eTest --rerun-tasks --info --no-daemon --console=plain`: Chromium 1/1, wrapper·후속 DB assertion PASS, `BUILD SUCCESSFUL in 1m 11s`.
  - `p6BrowserE2eTest --rerun-tasks --info --no-daemon --console=plain`: Chromium 2/2, wrapper·후속 DB assertion PASS, `BUILD SUCCESSFUL in 58s`.
- Next steps:
  - 최종 read-only validator 재판정을 반영한다.

## [2026-07-30] Session Summary (P7 실제 자기소개서 Browser E2E)

- What was done:
  - 가입·VERIFIED evidence·공고 분석에서 자기소개서 생성, 질문 CRUD/order, partial generation/retry, 저장·검증·제안·finalize, restore·archive·evidence lifecycle·owner 격리를 실행했다.
  - wrapper 후속 SQL로 immutable version, partial retry 중복 부재, verification freshness, provenance와 typed Run link를 검증했다.
- Key decisions:
  - Backend·PostgreSQL·MinIO·Fake AI·Vue·Chromium을 단일 Gradle wrapper가 소유하고 모든 process를 종료한다.
- Issues encountered:
  - 초기 실행은 number input parser TypeError와 응답/UI race로 중단돼 frontend parser 회귀와 mutation postcondition 대기로 보정했다.
- Validation:
  - `p7BrowserE2eTest`: Chromium 1/1, JUnit wrapper 1/1과 DB assertions 통과, `BUILD SUCCESSFUL`.
  - 최종 P7 source에서 `p6BrowserE2eTest`: Chromium 2/2와 DB assertions 통과.
- Next steps:
  - 독립 validator 판정을 반영한다.

## [2026-07-30] Session Summary (P6 wrapper DB assertion 보정과 최종 통과)

- What was done:
  - `P6BrowserE2eTest` 후속 실패 Run assertion의 컬럼을 실제 V4 schema `error_code`에 맞췄다.
- Key decisions:
  - assertion 1줄만 변경하고 migration·운영 repository·actual Browser spec은 유지했다.
- Issues encountered:
  - 최초 current-source 실행은 Playwright 2/2 뒤 존재하지 않는 `safe_error_code` 조회로 실패했다.
- Validation:
  - 허용된 1회 재실행에서 Playwright 2/2, JUnit wrapper 1/1, 분석 2 version·criterion·provenance·성공/실패 Run·resource link assertion과 process 종료가 모두 통과했다.
- Next steps:
  - P7 구현 뒤 P7 actual wrapper와 P6 핵심 회귀를 함께 실행한다.

## [2026-07-29] Session Summary (P6 실제 Backend·Frontend 분석 E2E harness)

- What was done:
  - `p6BrowserE2eTest`가 PostgreSQL·Spring async runtime·Fake Chat/Embedding·Vue·Chromium을 연결하고 분석·reuse·OUTDATED·재분석·근거 부족·owner 격리를 실행하도록 추가했다.
- Key decisions:
  - direct career VERIFIED evidence를 사용해 Object Storage 없이 P6 RAG application 경계를 검증하고 실제 provider 호출은 금지한다.
- Issues encountered:
  - 1차 locator 충돌을 보정한 2차 실행에서 비공개 evidence GET assertion이 500을 반환했다. 공개 PUT endpoint 404 assertion으로 수정했지만 자동 재검증 상한 때문에 세 번째 실행은 하지 않았다.
- Validation:
  - 2차 Playwright에서 근거 부족 test 1/1은 통과했고 정상 test는 마지막 evidence 격리 assertion 전까지 분석·reuse·재분석·공고/분석/Run 404를 통과했다.
- Next steps:
  - 수정된 evidence PUT owner 404와 wrapper DB assertion을 향후 단일 실행으로 확인한다.

## [2026-07-27] Session Summary (P5 실제 Backend·Frontend Job E2E)

- What was done:
  - 수동 201 상태 수명주기, URL-only 202 추출, WAITING_USER same-run resume, owner 404와 Scheduler 자동 마감 5개를 연결했다.
- Key decisions:
  - 실제 외부 사이트·유료 AI 없이 test-scope Fake URL fetch와 Fake Chat을 사용한다.
- Issues encountered:
  - 생성 직전 CSRF 요청과 mutation 완료를 명시적으로 기다리도록 harness를 안정화했다.
- Validation:
  - `gradlew.bat p5BrowserE2eTest`에서 Playwright 5/5와 JUnit wrapper DB assertion이 통과했다.
- Next steps:
  - P6 분석 E2E는 분석 기능 구현 전 추가하지 않는다.

## [2026-07-19] Session Summary (P4 실제 Backend·Frontend·SSE E2E)

- What was done:
  - 성공·manual same-run resume·AI partial failure·두 사용자 404의 Playwright 4개 시나리오를 Backend Gradle task로 연결했다.
- Key decisions:
  - production Fake endpoint 없이 test-scope `@Primary` gateway와 immutable Fake price catalog만 사용한다.
- Issues encountered:
  - 고정 Frontend port 충돌과 Vite 인자 전달을 random validated port와 직접 `--host/--port/--strictPort` command로 해결했다.
  - Fake usage price pair 제약은 Chat·Embedding price item을 seed해 해결했다.
- Validation:
  - `.\gradlew.bat p4BrowserE2eTest --rerun-tasks --info --no-daemon --console=plain`에서 Playwright 4/4가 통과했다.
- Next steps:
  - GitHub-hosted runner의 신규 P4 job 결과는 첫 push/PR에서 확인한다.
