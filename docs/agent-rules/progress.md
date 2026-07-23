# Progress

## Overview

저장소 공통, Session 기반 문서 추적, 백엔드·Controller OpenAPI/Swagger, 응답·예외, 프론트엔드, 인프라 규칙과 Codex 역할 위임 절차가 문서화되어 있다. 규칙은 루트 `AGENTS.md`의 라우팅 표를 통해 사용한다.

## [2026-07-23] Session Summary (backend 책임별 package 규칙 정립)

- What was done:
  - `backend-development.md`에 계층별 허용 책임 package, 빈 package 금지와 package-private stop rule을 추가하고 규칙 index를 동기화했다.

- Key decisions:
  - 모든 백엔드 작업이 기존 라우팅으로 이 규칙을 읽으므로 루트 `AGENTS.md`에는 중복 규칙을 추가하지 않았다.
  - `common`과 `ai`는 기능 package 계층을 기계적으로 복제하지 않고 전문 경계를 유지한다.

- Issues encountered:
  - 구현 완료 상태가 초기 bootstrap으로 남아 있던 오래된 문구를 P1~P4 실제 상태로 바로잡았다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (Controller Swagger 문서·API 시험 규칙 추가)

- What was done:
  - `backend-development.md`에 Controller tag·operationId·response schema·Session/CSRF requirement와 안전한 example 규칙을 추가했다.
  - Swagger UI에서 anonymous CSRF bootstrap, `csrfToken` Authorize, mutation과 Session rotation 뒤 token 교체 순서를 정의했다.
  - 규칙 인덱스가 백엔드 규칙의 Controller OpenAPI·Swagger UI 테스트 책임을 명시하도록 갱신했다.

- Key decisions:
  - 모든 Spring 서버 작업이 이미 `backend-development.md`를 필수로 읽으므로 같은 내용을 루트 `AGENTS.md`에 중복하지 않는다.
  - Session+CSRF는 OpenAPI의 같은 requirement 객체로 AND를 표현하고 test fixture Controller는 production 문서에서 제외한다.

- Issues encountered:
  - Swagger security 배열의 OR/AND 의미와 JSON CSRF token 계약을 향후 구현자가 놓치지 않도록 명시 규칙이 필요했다.

- Validation:
  - 실제 Controller/OpenAPI 구현과 33개 Backend test가 새 규칙을 충족하는지 대조했다.
  - 변경 Markdown의 Prettier와 상대 링크 검사를 통과했다.

- Next steps:
  - 후속 Controller 변경에서 반복 누락이 확인되면 OpenAPI lint 또는 공통 contract test로 자동화한다.

## [2026-07-17] Session Summary (진행 이력 조회와 서브 에이전트 통합 규칙 정비)

- What was done:
  - `workflow.md`에서 모든 상위 progress 연쇄 조회를 제거하고 역할별 관련 문서의 최신 Session 5개만 읽도록 변경했다.
  - `documentation-tracking.md`에 표준 템플릿, KST 날짜, 최신순 삽입, 과거 기록 보존, 제한 검색과 모듈별 갱신 책임을 정의했다.
  - 루트 `AGENTS.md`에 관리자 역할, 전문 역할 선택, 작업 분배 정보, 파일 충돌 방지, 독립 검증과 구조화 Handoff 흐름을 통합했다.

- Key decisions:
  - 서브 에이전트는 추적 문서를 직접 수정하지 않고 루트 관리자가 모든 결과를 받은 뒤 최종 `index.md`와 `progress.md`를 갱신한다.
  - 읽기 중심 분석은 병렬화하되 백엔드·AI 워크플로가 같은 Spring 파일을 필요로 하면 계약을 먼저 정하고 순차 작업한다.
  - 과거 이력은 문서 전체가 아니라 키워드·파일명·클래스명·오류·기능명·날짜 범위로만 추가 조회하고 이유를 보고한다.

- Issues encountered:
  - 기존 규칙의 8개 상태 섹션과 모든 상위 progress 조회가 새 Session·최신 5개 정책과 직접 충돌해 세 규칙 문서를 함께 정합화해야 했다.

- Validation:
  - 문서의 표준 제목·필드·최신순 정적 검사와 변경 Markdown Prettier 검사를 통과했다.
  - 루트 지침이 네 역할, 관리자 책임과 추적 문서 통합 경계를 포함하는지 직접 확인했다.

- Next steps:
  - 실제 개발 위임에서 구조화 결과와 관리자 통합 흐름을 적용하고 반복되는 누락이 있으면 규칙을 보강한다.

## [2026-07-17] Session Summary (Codex 작업 규칙 및 응답 예외 적용 기준 수립)

- What was done:
  - 당시 구현 상태:
    저장소 공통, 문서 추적, 백엔드, 응답·예외, 프론트엔드, 인프라 규칙이 문서화되어 있다. 규칙은 루트 `AGENTS.md`의 라우팅 표를 통해 사용한다.
  - 완료된 작업:
    - 기존 Codex 및 타 AI 에이전트 설정 부재 확인
    - 공식 Codex의 `AGENTS.md`와 프로젝트 설정 탐색 방식 확인
    - 레퍼런스 Spring 응답·예외 구조 분석
    - 현재 `docs/spec/api.md`와 레퍼런스 계약 충돌 분석
    - 6개 작업 유형별 규칙 문서 생성
  - 당시 진행 중인 작업:
    없음. 규칙 작성, 라우팅과 문서 검증을 완료했다.

- Key decisions:
  - 개발 규칙은 Markdown으로 이 디렉터리에 두고 `.codex/rules`와 분리한다.
  - 현재 API 계약을 우선하며 레퍼런스에서는 중앙화된 생성·변환 구조만 채택한다.

- Issues encountered:
  - 레퍼런스는 실패 응답에도 실제 HTTP 200을 사용하고 Security 오류 형식도 서로 달라 그대로 적용할 수 없다.
  - 현재 프로젝트에는 응답·예외 코드가 없어 규칙과 예상 구조만 먼저 확정했다.

- Validation:
  - 레퍼런스 파일 위치, 클래스 필드, factory, 예외 상속, handler, Security 실패 경로를 소스에서 직접 확인했다.
  - 루트 `AGENTS.md`에서 6개 규칙의 작업 유형별 라우팅을 확인했다.
  - 모든 규칙의 상대 링크와 신규 Markdown Prettier 검사를 실행했다. 최초 형식 차이를 수정한 뒤 재검사가 통과했다.

- Next steps:
  - 공통 응답·예외 처리 실제 구현 시 규칙의 예상 파일 구조를 구현 결과에 맞게 갱신
  - 반복되는 실패 패턴이 확인되면 lint/test 자동화로 규칙을 보강
