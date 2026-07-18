# Progress

## Overview

제품 명세 5종, 전체 시스템 설계·구현 계획, Codex 작업 규칙 6종과 최신순 Session 기반 계층형 추적 문서가 구성되어 있다.

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
