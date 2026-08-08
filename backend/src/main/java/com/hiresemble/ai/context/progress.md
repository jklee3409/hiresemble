# Progress

## Overview

P3 provenance-only ContextBuilder와 Document·Job·Cover Letter·Interview·Career Artifact owner/version/hash snapshot이 구현됐다.

## [2026-08-08] Session Summary (Career Artifact bounded context)

- What was done:
  - 선택한 active VERIFIED experience/evidence version과 allowlist profile section을 시작 시 재검증해 bounded context로 구성했다.
- Key decisions:
  - GitHub raw source, 연락처, 이름과 credential number는 LLM context에서 제외한다.
- Issues encountered:
  - 빈 profile section은 hard failure가 아니라 warning으로 유지했다.
- Validation:
  - source type·owner/version, raw source·render profile 부재와 truncation 계약을 검증했다.
- Next steps:
  - None.

## [2026-08-06] Session Summary (자기소개서 memo·model snapshot)

- What was done: generation v4 context에 선택 model과 문항 memo를 포함하고 verification v4도 exact model을 복원하도록 확장했다.
- Key decisions: memo는 bounded 사용자 작성 방향이며 VERIFIED 사실 근거로 취급하지 않는다.
- Issues encountered: None.
- Validation: generation workflow test와 Backend 전체 `check` 통과.
- Next steps: memo 길이 제한과 prompt 영향은 품질 평가 dataset으로 관찰한다.

## [2026-08-05] Session Summary (Cover Letter v1/v2/v3 Context 호환)

- What was done:
  - generation·verification ContextBuilder가 v1/v2/v3 exact workflow version을 허용하고 v2/v3는 modern immutable snapshot loader를 공유하도록 정리했다.
- Key decisions:
  - 답변 본문은 checkpoint에 저장하지 않으며 v3 bounded Provider payload의 원본 count·전달 count·truncated·full hash만 durable refs에 남긴다.
- Issues encountered:
  - None.
- Validation:
  - Context owner/version/hash 회귀를 포함한 Backend check 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (Cover Letter v1/v2 ContextBuilder 호환)

- What was done:
  - generation·verification ContextBuilder가 active v2와 durable v1 Run을 모두 owner/version/hash 검증 후 로드하도록 확장했다.
- Key decisions:
  - checkpoint Context에는 기존처럼 ID·hash·count만 유지하고 본문은 저장하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Backend 전체 check 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 preparation·feedback context)

- What was done:
  - 공고·분석·자기소개서 current answer·structured final education·non-education VERIFIED evidence와 immutable feedback 대상 답변 context를 추가했다.
- Key decisions:
  - 외부 검색에는 개인 profile·evidence·문서·답변 원문을 전달하지 않고 education tombstone은 positive provenance에서 제외한다.
- Issues encountered:
  - None.
- Validation:
  - context owner·학력 projection·검색 query privacy 테스트와 P8 actual DB assertion이 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (Cover Letter generation·verification context)

- What was done:
  - 공고·latest analysis·질문/current version·선택/현재 VERIFIED evidence와 immutable answer provenance의 P7 context builder를 추가했다.
- Key decisions:
  - masked chunk와 답변 본문은 필요한 step 메모리에만 두고 durable context에는 hash·ID·상태만 저장한다.
- Issues encountered:
  - 없음.
- Validation:
  - owner/version snapshot, SOURCE_DELETED 제외와 workflow 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-29] Session Summary (Job Analysis context builder)

- What was done:
  - tenant·Job/profile/evidence/context/policy hash와 verified evidence reference만 담는 P6 context를 추가했다.
- Key decisions:
  - 공고·문서 원문과 profile PII는 durable context에 저장하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - workflow context/Fake 실행과 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-27] Session Summary (Job Posting Extraction context 추가)

- What was done:
  - Job owner·resource version과 사용자 override를 검증하는 context builder와 workflow dispatch를 추가했다.
- Key decisions:
  - 전체 공고 본문·HTML은 durable context에 저장하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - owner/version 재검증과 context hash 통합 테스트가 통과했다.
- Next steps:
  - P6 context는 승인 근거 reference와 새 analysis snapshot을 별도 추가한다.

## [2026-07-19] Session Summary (Document ingestion context 추가)

- What was done:
  - typed Document owner·resource와 source revision을 검증하는 context builder를 추가했다.
- Key decisions:
  - raw document text는 context snapshot에 저장하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 타 사용자 resource 404와 typed owner resolution이 통과했다.
- Next steps:
  - P5 resource context는 해당 aggregate가 생긴 뒤 추가한다.

## [2026-07-19] Session Summary (안전한 context snapshot 계약 구현)

- What was done:
  - user scope, resource/version/hash, upstream output, truncation, verification와 model policy projection을 정의했다.

- Key decisions:
  - profile repository를 직접 횡단하지 않고 후속 domain query port를 기다린다.

- Issues encountered:
  - None.

- Validation:
  - Fake 3-step의 input hash·reuse·quality 분리 테스트가 통과했다.

- Next steps:
  - 각 workflow phase에서 승인 evidence query port를 구현한다.
