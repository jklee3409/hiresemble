# Progress

## Overview

P0 승인 제품 명세 5종, 전체 시스템 설계·구현 계획·승인 결정 기록, Codex 작업 규칙 6종과 최신순 Session 기반 계층형 추적 문서가 구성되어 있다. P0–P4가 완료됐고 P5–P10은 미착수다.

## [2026-07-23] Session Summary (backend package 문서 체계 동기화)

- What was done:
  - 백엔드 규칙·설계와 새 책임 package 44개의 `index.md`·`progress.md`를 실제 Java 구조에 맞춰 연결했다.

- Key decisions:
  - 새 source directory마다 책임·의존성·주의사항과 검증 이력을 기록하고 상위 문서에는 관계만 요약했다.
  - 활성 `docs/spec/**` 계약은 변경되지 않아 수정하지 않았다.

- Issues encountered:
  - 새 디렉터리와 기존 상위 문서의 상대 링크를 함께 정리해야 했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P4 구현 계획·추적 문서 동기화)

- What was done:
  - P4 실제 구현 범위와 Document·AI·Frontend·migration·E2E 추적 문서를 상호 링크했다.
- Key decisions:
  - 활성 `docs/spec/**`는 계약 충돌이 없어 수정하지 않고 구현 계획과 상태 문서만 갱신했다.
- Issues encountered:
  - 최초 Validator의 Agent Run Document filter MAJOR를 owner resolver와 직접 회귀 테스트로 한 차례 보정했다.
- Validation:
  - 상대 링크·progress 표준 필드·P5 이후 미착수 표현을 최종 문서 감사 대상으로 포함했다.
  - 최종 read-only Validator가 문서와 실제 상태를 포함해 `PASS`했다.
- Next steps:
  - P5/P6 착수 시 P4의 typed resource·masked-only·forward migration 경계를 보존한다.

## [2026-07-18] Session Summary (P0 제품 계약 문서 기준선 확정)

- What was done:
  - 제품 소유자 승인을 다섯 기준 명세에 동기화하고 spec 결정 추적, 설계 승인 상태와 구현 계획을 한 문서 계층으로 정렬했다.
  - proposal은 승인 근거 기록으로 전환하고 활성 계약이 `spec/`에만 존재하도록 책임 경계를 갱신했다.

- Key decisions:
  - 기능·API·DB·페이지·기술 계약의 P0 기준선은 문서 버전 1.1이며 D-01–D-18과 Gate A–C가 모두 닫혔다.
  - 문서 기준선 확정과 코드 구현 완료를 분리하고 P1은 공통 HTTP·인증 기반부터 시작한다.

- Issues encountered:
  - 기존 설계와 proposal에는 승인 전 충돌·권장 문구가 남아 있어 활성 계약과 역사 기록의 precedence를 명시해야 했다.
  - 명세 간 조건부 응답, 상태 복구, 공개/내부 필드 경계를 정규화했지만 비즈니스 파일이나 기존 V1 migration은 수정하지 않았다.

- Validation:
  - 전문 분석 3개는 읽기 전용 `DONE`, 독립 validator는 다섯 명세의 정책·Gate·상태·API·DB·페이지 정합성을 `PASS`로 반환했다.
  - Markdown 표·링크·enum·endpoint·field bound·quality·idempotency와 Prettier·`git diff --check`를 검사했다. 문서 전용이라 backend/frontend build는 실행하지 않았다.

- Next steps:
  - P1 구현 작업에서 OpenAPI·migration·code를 활성 명세와 대조하고 Fake 기반 테스트로 공통 기반을 먼저 고정한다.

## [2026-07-18] Session Summary (P0 계약 제안서 최종 감사)

- What was done:
  - `design/p0-contract-decision-proposal.md`의 공개 계약·DB 수명주기·AI runtime·route projection을 기준 명세와 의미 기반으로 재감사하고 차단 계약만 보정했다.
  - 수정 전 validator `NEEDS_CHANGES`, 보정 후 새 validator `PASS`를 확인해 제안서를 `READY_FOR_OWNER_REVIEW`로 전환했다.

- Key decisions:
  - URL과 memo 상한을 각각 2000자로 통일하고, 면접 답변 source를 `USER_EDITED` 전용 타입으로 분리했다.
  - 탈퇴 idempotency를 제거하고 embedding model·dimension 및 profile 완료 정책을 제품 승인 항목으로 분리했다.

- Issues encountered:
  - 최초 감사에서 품질 request 부재, session 폐기 뒤 replay 불가, 근거 없는 vector 차원, 취소 후 PENDING과 내부 DTO 노출이 승인 차단 문제로 확인됐다.
  - 기준 명세·코드·migration·설정은 수정하지 않고 제안서 안에서만 해소했다.

- Validation:
  - 최종 validator가 D-01~~D-18, Gate A~~C, 상태·enum·DTO·API·DB·제품 결정과 변경 경로를 `PASS`했다.
  - Markdown 표·링크·중복·상한·allowlist·상태 전이, Prettier와 `git diff --check`를 검사했다. 문서 작업이라 build는 생략했다.

- Next steps:
  - 제품 소유자 승인 후 다섯 기준 명세를 동기화하고 proposal을 결정 기록으로 전환한다.

## [2026-07-18] Session Summary (P0 계약 결정 제안서 통합)

- What was done:
  - 설계의 D-01–D-18과 Gate A–C를 다섯 기준 명세에 연결한 `design/p0-contract-decision-proposal.md`를 만들고, 설계 index·진행 기록·링크와 기존 Markdown 표·범위 표기를 정리했다.
  - API·DB·AI runtime·route projection의 구현 전 기준선과 제품 소유자 질문 6개를 작성했다.

- Key decisions:
  - 11개 기술 항목은 단일 권장안, 제품 경험·보존·비용 선택이 필요한 7개 항목은 승인 필요로 분류했다.
  - 이 제안서는 기준 명세가 아니며 사용자 승인과 명세 동기화 전에는 P0 완료나 구현 근거로 확정하지 않는다.

- Issues encountered:
  - read-only validator의 두 차례 판정은 모두 `NEEDS_CHANGES`였고, 첫 4개 차단점은 재검증에서 해소 확인됐다.
  - 두 번째에 새로 발견된 DTO 상한·출처 enum·path 불일치는 루트가 정합화했으나 규칙상 세 번째 validator를 실행하지 않아 최종 보정분은 독립 미검증이다.

- Validation:
  - 최종 루트 검사에서 D 18행, Gate A~C, 기준 endpoint 95개, 필수 타입 18개, 제품 질문 6개, 표 열 수·링크·Prettier·`git diff --check`를 확인했다.
  - 코드·테스트·dependency·migration·설정과 `spec/**`는 변경하지 않았다.

- Next steps:
  - 제품 승인 후 `spec/**`를 동기화하고 독립 계약 검증을 통과시킨 다음 구현 단계로 이동한다.

## [2026-07-18] Session Summary (명세 기반 전체 시스템 설계와 구현 계획 작성)

- What was done:
  - `docs/design/`을 만들고 전체 architecture·도메인·DB/API/page 연결, 주요 업무·AI workflow와 보안·비동기 설계를 작성했다.
  - 계약 결정부터 AC-01~~13까지의 P0~~P10 구현 순서, 단계별 완료 조건과 개발·검증 에이전트 파일 소유권을 작성했다.
  - `docs/index.md`와 상위 저장소 안내를 갱신해 기준 명세, 파생 설계, 작업 규칙의 책임을 분리했다.

- Key decisions:
  - 기준 계약은 `spec/`, 파생 구현 구조는 `design/`, Codex 작업 절차는 `agent-rules/`에서 관리한다.
  - 명세 충돌·누락은 권장안과 구현 보류 범위를 함께 기록하고 P0 승인 전 확정 사실로 취급하지 않는다.

- Issues encountered:
  - 독립 validator가 최초 설계에서 자기소개서 목록·`ARCHIVED`, 조사 재시도, 면접 준비 목록의 직접 추적 누락과 Markdown format 차이를 발견했다.
  - 세 보조 MVP 흐름을 한 차례 보완하고 format을 적용했다.

- Validation:
  - 세 전문 분석 에이전트와 독립 validator가 모든 `docs/spec/*.md`와 설계 문서를 읽기 전용으로 교차 검증했다.
  - 정적 재검사에서 AC 13개, 필수 5필드를 가진 이슈 18개, 변경 문서 상대 링크와 `git diff --check`가 통과했다.
  - 변경 Markdown의 Prettier 검사가 통과했고 비즈니스 코드·dependency·migration·API·UI는 변경하지 않았다.

- Next steps:
  - 구현 전에 설계의 P0 결정 게이트를 사용자 승인으로 닫고 영향받는 명세를 일관되게 갱신한다.

## [2026-07-17] Session Summary (Session 기반 작업 이력 문서 체계 표준화)

- What was done:
  - 전체 관리 대상 `progress.md`를 단일 Overview와 최신순 Session Summary 구조로 전환하고 기존 이력을 보존했다.
  - 문서 작업 절차와 추적 규칙에 역할별 최신 5개 기본 조회, 제한 과거 검색과 루트 관리자 갱신 책임을 반영했다.

- Key decisions:
  - 제품 계약인 `docs/spec/` 원문은 변경하지 않고 작업 이력과 Codex 운영 규칙만 갱신했다.
  - 상위 문서에는 통합 영향, 하위 규칙 문서에는 구체적인 조회·작성 책임을 기록해 중복을 줄였다.

- Issues encountered:
  - 기준 파일 자체에는 중간 Overview/Notes, 표준 필드 누락과 날짜 역전이 있어 제목 패턴 외에는 사용자 명시 표준을 우선했다.

- Validation:
  - 관리 대상 22개 문서의 구조·필드·정렬과 기존 21개 문서의 168개 섹션 보존을 정적으로 검증했다.
  - 변경 Markdown 전체의 Prettier 검사가 통과했다.

- Next steps:
  - 이후 작업은 관련 기록의 최신 5개만 기본 조회하고 새 Session을 Overview 바로 아래에 추가한다.

## [2026-07-17] Session Summary (제품 명세 및 Codex 규칙 문서 체계 구축)

- What was done:
  - 당시 구현 상태:
    제품 명세 5종, Codex 작업 규칙 6종과 계층형 추적 문서가 구성되어 있다.
  - 완료된 작업:
    - 기능, API, DB, 페이지, 기술 스택 명세 작성
    - 문서 영역의 책임과 명세/작업 규칙 경계 정의
    - 레퍼런스 응답·예외 구조와 현재 API 계약 차이 분석
    - `agent-rules/` 세부 규칙 6종과 21개 관리 대상의 문서 추적 계층 생성
  - 당시 진행 중인 작업:
    없음. 이번 문서 구조 초기화는 완료됐다.

- Key decisions:
  - 제품 계약은 `spec/`, Codex의 수행 절차는 `agent-rules/`에서 관리한다.
  - 분석 결과는 적용 규칙과 함께 기록하되 레퍼런스 코드를 복제하지 않는다.

- Issues encountered:
  - 레퍼런스의 공통 성공 envelope는 `spec/api.md`의 직접 반환 계약과 충돌한다. API 명세를 우선하는 적용 규칙이 필요하다.
  - 명세는 구현 계획이며 현재 구현 완료를 의미하지 않는다.

- Validation:
  - 5개 명세 파일의 존재와 주요 공통 계약을 확인했다.
  - 55개 Markdown 파일의 저장소 내부 상대 링크와 42개 추적 문서의 필수 구조를 검사했다. 결과: 성공.
  - 신규 Markdown 49개를 Prettier로 검사했으며 최초 형식 차이를 수정한 뒤 모두 통과했다.

- Next steps:
  - 기능 구현 시 명세와 실제 API/OpenAPI 간 계약 검증 자동화
  - 주요 기술 결정에 대한 ADR 도입 여부 검토
