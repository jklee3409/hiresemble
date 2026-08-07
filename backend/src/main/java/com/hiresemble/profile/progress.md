# Progress

## Overview

P2 기본·구조화 프로필·비학력 direct evidence와 P4 Document PENDING evidence·증빙 문서 FK를 owner-scoped transaction 경계로 구현했고, P6~P7용 canonical profile·현재 `VERIFIED` 비학력 evidence snapshot query를 제공한다.

## [2026-08-07] Session Summary (문서·GitHub 공통 canonical 경험 적용)

- What was done:
  - 문서 provenance 검증과 canonical apply를 분리하고 GitHub candidate 검증·evidence link·경험 DTO provenance projection을 추가했다.
- Key decisions:
  - exact/SAME은 새 item 없이 corroborating source만 보강하고 NEW/RELATED/CONFLICT는 PENDING으로 유지한다. category alias는 비교 group에서만 정규화한다.
- Issues encountered:
  - 기존 document 결과를 바꾸지 않도록 characterization fixture 아래에서 서비스를 추출했다.
- Validation:
  - document characterization 및 GitHub NEW/SAME/RELATED/CONFLICT/source-delete/foreign-unit 통합 테스트가 통과했다.
- Next steps:
  - Gate 2에서 additive GitHub source projection을 화면에 표시한다.

## [2026-08-07] Session Summary (canonical 경험 라이브러리 Backend)

- What was done:
  - 다중 문서에서 추출한 경험을 canonical item으로 통합하고 출처·검증·semantic match를 관리하는 전 계층을 구현했다.
- Key decisions:
  - 자동 동일은 높은 cosine·공통 anchor·수치 무충돌을 모두 요구하며 전역 downstream은 canonical evidence만 소비한다.
- Issues encountered:
  - 문서 삭제와 사용자 승인 경험의 생명주기를 분리했다.
- Validation:
  - domain·OpenAPI·Document semantic integration 집중 테스트 통과.
- Next steps:
  - 경험 관리 Frontend와 golden-set 임계치 평가가 남는다.

## [2026-08-02] Session Summary (공고 분석용 구조화 학력·지원 자격 snapshot)

- What was done:
  - 대표 학력의 ID/version/단계/상태/학위/전공/졸업일/대표 여부와 지원 자격 자기신고를 owner-scoped analysis snapshot에 추가했다.
  - 근무 가능일·병역·해외여행·채용 결격 상태를 관리하는 1:1 record와 version 기반 조회·수정 경계를 추가했다.
- Key decisions:
  - 학력은 기존 구조화 profile aggregate로 유지하고 `EDUCATION profile_evidence`를 생성하지 않는다. 신규 enum 기본값은 `UNSPECIFIED`다.
- Issues encountered:
  - None.
- Validation:
  - `*ProfileAnalysis*` 및 Profile 통합 집중 테스트가 통과했다.
- Next steps:
  - None.

## [2026-08-01] Session Summary (직접 대외활동과 소재 상태 경계)

- What was done:
  - 사용자 직접 대외활동 aggregate와 `ACTIVITY` evidence projection, PENDING 포함 검토 상태 변경 및 batch 검토를 profile 전 계층에 추가했다.
- Key decisions:
  - 활동 생명주기는 문서와 분리하고 소재 선택 상태만 verified evidence 경계에 반영한다.
- Issues encountered:
  - None.
- Validation:
  - Profile·Document 통합 테스트와 Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (document candidate rejection 분류)

- What was done:
  - document evidence validator가 candidate별 안전한 rejection reason count를 반환하도록 구현했다.
- Key decisions:
  - 정상 rejection은 transaction failure가 아니며 유효 candidate 저장은 그대로 commit한다.
- Issues encountered:
  - 기존 데이터의 reason은 소급 생성하지 않는다.
- Validation:
  - `DocumentIntegrationTest`와 전체 check 통과.
- Next steps:
  - rejected candidate 값은 persistence·로그에 남기지 않는다.

## [2026-07-31] Session Summary (학력 단계 기반 최종 학력)

- What was done:
  - 학력 API·domain·service·persistence에 명시적 단계와 mutation 후 최종 학력 재계산을 연결했다.
- Key decisions:
  - profile owner row lock 아래 hierarchy와 tie-break를 적용해 concurrent mutation도 하나의 최종 학력만 남긴다.
- Issues encountered:
  - None.
- Validation:
  - Profile 통합 테스트와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (학력 evidence 완전 제외)

- What was done:
  - 학력 CRUD의 direct evidence 생성을 제거하고 source/category 조회·분석·문서 추출 경로에서 학력을 제외했다.
- Key decisions:
  - 구조화 학력은 프로필 완료도에 유지하며 대외활동 evidence로는 만들지 않는다.
- Issues encountered:
  - 기존 학력 ID를 참조할 수 있는 provenance 때문에 물리 삭제 대신 비식별 tombstone migration을 사용했다.
- Validation:
  - Profile domain 6, API 10, Document 12 tests와 Backend 전체 385 tests 통과.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 evidence provenance 삭제 참조)

- What was done:
  - 문서 evidence 삭제가 Job Analysis뿐 아니라 Cover Letter answer provenance 참조도 확인하도록 다중 reference contributor를 연결했다.
- Key decisions:
  - 참조된 evidence ID는 삭제하지 않고 `SOURCE_DELETED` tombstone으로 전환해 과거 answer/verification link를 보존한다.
- Issues encountered:
  - 없음.
- Validation:
  - 문서 삭제→historical provenance 유지→새 generation 제외 실제 E2E와 Backend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (P6 프로필·승인 근거 snapshot 경계)

- What was done:
  - 공고 분석이 사용할 canonical profile hash와 owner-scoped `VERIFIED` evidence snapshot query를 추가했다.
- Key decisions:
  - `PENDING`, `REJECTED`, `SOURCE_DELETED` evidence와 profile entity·문서 원문은 분석용 application model에 포함하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - snapshot filtering, 분석 뒤 근거 상태 전환과 공고 분석 통합을 포함한 Backend `check` 352개 테스트가 통과했다.
- Next steps:
  - None.

## [2026-07-23] Session Summary (책임별 backend package 세분화)

- What was done:
  - backend/src/main/java/com/hiresemble/profile 영역의 기존 Java 책임을 실제 하위 package와 추적 문서에 반영했다.

- Key decisions:
  - 파일 경로, package·import와 필요한 FQCN만 변경하고 API·DB·workflow·접근 제한자는 유지했다.
  - 실제 파일이 있는 책임 package만 생성하고 P5 이후 기능과 빈 디렉터리는 만들지 않았다.

- Issues encountered:
  - package-private 결합은 접근 제한자를 넓히지 않고 같은 package 이동 또는 명시적 이동 제외로 처리했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P4 Document evidence·증빙 문서 연결)

- What was done:
  - evidence document filter, document candidate PENDING 적용과 자격증·어학·수상 owner document 연결을 활성화했다.
- Key decisions:
  - 참조 없는 document evidence는 삭제하고 참조가 있으면 같은 ID의 `SOURCE_DELETED` tombstone으로 전환한다.
- Issues encountered:
  - P5 이후 provenance table 없이 Fake `EvidenceReferenceQueryPort` contributor로 tombstone branch를 검증했다.
- Validation:
  - active document owner 404, evidence edit·VERIFIED·REJECTED와 tombstone mutation 409가 통과했다.
  - 최종 read-only Validator가 document filter·증빙 FK·SOURCE_DELETED 정책을 포함해 `PASS`했다.
- Next steps:
  - 실제 downstream provenance contributor는 해당 domain phase에 추가한다.

## [2026-07-19] Session Summary (P2 프로필·직접 입력 근거 구현)

- What was done:
  - 기본 프로필 2개, 구조화 프로필 20개, evidence 3개 operation과 완료도·pagination·sort·optimistic version 계약을 구현했다.
  - 다섯 구조화 source의 생성·수정·soft delete와 direct evidence 생성·재생성·삭제를 같은 transaction으로 연결했다.

- Key decisions:
  - 원본 source를 source of truth로 두고 source 수정 시 evidence 별도 편집보다 동기화 결과와 `VERIFIED` 상태가 우선한다.
  - 모든 조회·mutation은 사용자 ID를 포함하며 존재하지 않음과 타 사용자 소유를 동일한 404로 처리한다.
  - P2 document field는 nullable 계약만 유지하고 non-null 입력·filter는 404로 거부한다.

- Issues encountered:
  - 기존 개발 DB에는 Flyway V2 이력 없이 Session table이 남아 있어 E2E에는 별도 빈 PostgreSQL DB를 사용했다. 기존 DB 데이터는 수정하지 않았다.

- Validation:
  - `backend\\gradlew.bat check`에서 9개 test class, 54개 test가 failure·error·skip 0으로 통과했다.
  - 빈 DB V1→V2→V3 및 V1-only·V2-only upgrade, V1·V2 hash, DB CHECK·owner·rollback을 PostgreSQL로 검증했다.
  - 실제 Chromium E2E에서 가입·완료도·학력 수정·두 사용자 owner 404·로그아웃/재로그인을 통과했다.
  - 최종 read-only validator가 AC-02와 profile 계약을 `PASS`로 판정했다.

- Next steps:
  - P2는 완료 상태다.
  - P4에서 `documents` aggregate를 구현할 때 nullable document column의 복합 FK를 forward migration으로 추가한다.
