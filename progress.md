# Progress

## Overview

- 초기 프론트엔드, 백엔드, Docker Compose, CI 환경이 구성되어 있다.
- 제품 기능·API·DB·화면·기술 명세는 P0 승인 기준선으로 `docs/spec/`에 존재한다.
- 실제 비즈니스 Controller, 도메인 모델, 공통 응답·예외 처리 코드는 아직 구현되지 않았다.
- Codex 관리자·전문 서브 에이전트 설정, 작업 규칙과 23개 관리 대상 디렉터리의 Session 기반 문서 계층이 구성되어 있다.

## [2026-07-18] Session Summary (P0 제품 계약 기준선 승인 반영 완료)

- What was done:
  - 승인된 8개 제품 정책과 제안서의 D-01–D-18을 다섯 기준 명세에 통합하고 설계·계획·진행 문서의 상태를 동기화했다.
  - backend·ai_workflow·frontend의 읽기 전용 분석을 루트에서 통합하고 새로운 read-only validator로 계약 기준선을 독립 검증했다.

- Key decisions:
  - `docs/spec/**`만 활성 제품 계약이며 proposal은 `APPROVED_DECISION_RECORD`로 승인 과정과 근거를 보존한다.
  - P0 계약 기준선은 완료됐지만 P1은 미착수다. Java·TypeScript·Vue, Flyway, dependency·설정·Compose 구현은 이번 범위에 포함하지 않았다.

- Issues encountered:
  - 공고 수동 본문의 동기/비동기 응답 분기, mock 실패 replay, evidence tombstone read-only, Agent retry identity와 DB 상한을 명세 전체에서 일치시켜야 했다.
  - `index.md` 범위 기호가 Markdown 취소선으로 포맷되는 문제는 en dash로 교체해 해결했다.

- Validation:
  - validator가 승인 정책 8개, D 18개, Gate 16개, canonical enum, 97 endpoint, owner·idempotency·quality·embedding과 공개 DTO 경계를 `PASS`로 판정했다.
  - Markdown 표·상대 링크·enum·endpoint·field bound·상태 전이·allowlist 검사와 Prettier, `git diff --check`, 변경 범위 검사를 수행했다.
  - 문서 전용 작업이라 backend/frontend build를 실행하지 않았고 외부 유료 API, commit, push, 배포를 수행하지 않았다.

- Next steps:
  - P1에서 공통 HTTP 오류·Session·CSRF·request ID·idempotency와 테스트 기반을 구현하고, 목표 DB 계약은 새 Flyway migration으로 단계적으로 검증한다.

## [2026-07-18] Session Summary (P0 계약 제안서 제품 검토 준비 전환)

- What was done:
  - 승인 전 P0 제안서를 수정 전·후 독립 validator로 감사하고, 구현자가 추측하거나 미승인 정책을 확정하지 않도록 계약을 정합화했다.
  - 최종 의미 검증 `PASS`에 따라 상태를 `READY_FOR_OWNER_REVIEW`로 변경하고 설계·문서·루트 추적 기록을 갱신했다.

- Key decisions:
  - D 항목은 권장 10개·제품 승인 필요 8개이며 제품 질문도 8개다.
  - 회원 탈퇴 replay 제거, mock feedback 품질 고정, 성공 feedback만 저장, embedding과 profile 완료의 승인 전 구현 차단을 채택했다.
  - P0는 아직 승인·완료가 아니며 승인 후 `docs/spec/**` 동기화와 재검증이 필요하다.

- Issues encountered:
  - 최초 validator는 4 BLOCKER와 URL·memo·source·취소·공개 DTO 경계 등 MAJOR를 포함해 `NEEDS_CHANGES`로 판정했다.
  - 한 차례 보정 후 새 validator가 승인 차단 충돌 없음으로 `PASS`했다.

- Validation:
  - D-01~~D-18과 Gate A~~C, enum/상태, request-response-DB 상한, quality/idempotency, cancel/retry, 사용자 격리·provenance를 의미·기계적으로 검사했다.
  - Markdown Prettier와 `git diff --check`를 실행했다. 코드·migration·설정·`docs/spec/**`는 변경하지 않았고 문서 전용이라 backend/frontend build를 실행하지 않았다.

- Next steps:
  - 제품 소유자가 8개 승인 질문을 검토한 뒤 승인된 결정을 기준 명세에 반영하고 P0 완료 여부를 판단한다.

## [2026-07-18] Session Summary (P0 계약 결정 제안과 구현 차단 항목 정리)

- What was done:
  - 필수 작업 규칙·기준 명세·설계·구현 계획과 현재 backend/frontend/infrastructure bootstrap을 확인하고, D-01–D-18과 Gate A–C의 승인 전 계약 제안서를 작성했다.
  - backend·ai_workflow·frontend의 읽기 전용 병렬 분석을 통합해 상태·enum, 전체 API projection, tenant·수명주기, AI runtime, route·UX 기준선과 제품 질문 6개를 확정 제안으로 정리했다.
  - 설계 index·progress와 루트·docs progress를 갱신하고 기존 설계 문서의 링크, 깨진 소유권 표와 범위 표기를 정리했다.

- Key decisions:
  - D-01~D-18은 `RECOMMENDED` 11개, `OWNER_DECISION_REQUIRED` 7개이며 사용자 승인 전 P0는 미완료 상태다.
  - 단일 Spring Boot·PostgreSQL·S3 호환 storage, REST snapshot 원천, 유한 AI workflow, 사용자 복합 소유권과 provenance·중복 비용 방지를 유지한다.
  - 회원 탈퇴 삭제 task는 Agent Run·user FK에서 분리하고, 공개 품질·내부 모델 tier·검색 품질은 별도 타입으로 고정했다.

- Issues encountered:
  - 1차 validator의 4개 계약 차단점은 1회 보정 뒤 2차 validator가 해소를 확인했다.
  - 2차 validator가 추가 DTO 상한·연구 출처 enum·path 표기 불일치를 발견해 `NEEDS_CHANGES`로 종료했으며, 루트가 해당 불일치를 최종 정합화했다.
  - 동일 역할 검증 상한에 따라 세 번째 validator를 실행하지 않았으므로 마지막 루트 보정분은 독립 validator 미검증으로 남는다.

- Validation:
  - 세 분석 에이전트는 모두 `DONE`·파일 변경 없음, validator는 두 번 모두 read-only·파일 변경 없음으로 종료했다.
  - 최종 루트 검사에서 D 18행(11/7), Gate A~C, 기준 API 95개 누락 0, 필수 타입 18개, 질문 6개, Markdown 표·링크, Prettier와 `git diff --check`를 통과시켰다.
  - 비즈니스 코드·테스트·dependency·migration·설정, `docs/spec/**`를 변경하지 않았고 commit·push·배포·외부 유료 API 호출을 수행하지 않았다.

- Next steps:
  - 제품 소유자가 6개 질문과 제안 전체를 승인·수정한 후 기준 명세를 동기화하고, 독립 계약 검증을 다시 통과시킨 뒤 P1 구현을 시작한다.

## [2026-07-18] Session Summary (Hiresemble 전체 시스템 설계와 단계별 구현 계획 수립)

- What was done:
  - `AGENTS.md`와 `docs/spec/`의 Markdown 7개를 모두 읽고 프로젝트 목적, MVP, 모듈·도메인 의존, 기능·DB·API·페이지 연결을 통합했다.
  - 문서·공고·자기소개서·면접과 Agent Orchestrator·Model Router·Context Builder·Budget Guard의 실행 흐름, 인증·격리·개인정보와 비동기·복구·SSE 설계를 작성했다.
  - `docs/design/`의 전체 시스템 설계, 구현 계획과 추적 문서를 만들고 루트·문서 영역 인덱스를 갱신했다.
  - P0~P10 구현 순서, 완료 조건과 backend·AI workflow·frontend·validator의 단일 파일 소유권을 정리했다.

- Key decisions:
  - 다섯 제품 명세를 변경하지 않고 파생 설계와 권장 해결안을 별도 문서로 관리한다.
  - 공개 계약·데이터 수명주기·AI 운영 정책의 미결 항목은 P0 결정 게이트 전 migration이나 API/UI로 구현하지 않는다.
  - 백엔드는 도메인·HTTP·persistence, AI workflow는 context·model·prompt·workflow, frontend는 UI·API consumer를 소유한다.

- Issues encountered:
  - 공고 상태 축, 품질·version·질문 enum, tenant DB 제약, 삭제·provenance, 멱등성·Agent Run 복구·SSE, 자기소개서 최종화·보관, 조사·모의 면접 lifecycle 등 18개 이슈 그룹을 확인했다.
  - 독립 validator가 보조 MVP 직접 추적 3건과 상위 진행 문서·format 보완을 요구해 허용된 한 차례 수정에 통합했다.

- Validation:
  - backend·AI workflow·frontend 분석 에이전트가 모두 `DONE`, 파일 변경 없음으로 종료했다.
  - 독립 validator는 사용자 요구 1~~15, AC-01~~13, 사용자 격리, 동기·비동기, 역할 경계와 링크를 통과시키고 세 보완점을 반환했다.
  - 보완 후 정적 검사에서 AC 13개, 필수 5필드를 가진 이슈 18개, 상대 링크와 `git diff --check`가 통과했다.
  - 변경 Markdown의 Prettier 검사가 통과했다. 비즈니스 코드·dependency·migration·API·UI를 변경하지 않아 backend/frontend build test는 실행하지 않았다.

- Next steps:
  - 구현 시작 전에 P0의 공개 API·상태, 데이터 수명주기, AI 비용·복구 정책을 사용자 승인으로 확정한다.

## [2026-07-18] Session Summary (Codex 멀티 에이전트 종료 안전성 보완 및 런타임 재검증)

- What was done:
  - 루트 `AGENTS.md`에 최대 2개 오케스트레이션 라운드, 역할별 생성 상한, 실패·Timeout 자동 재생성 금지, 최대 1회 수정-재검증과 명시적 종료 상태를 추가했다.
  - 세 구현 Agent에 보호 경로와 공유 계약 파일의 순차 소유 규칙을 직접 명시하고, 네 Agent에 서로 다른 런타임 식별 마커를 추가했다.
  - fresh read-only Codex 부모 세션 두 개에서 구현 역할 3개와 Validator 1개를 정확히 2개 라운드로 실행했다.

- Key decisions:
  - 기존 TOML의 필수 필드와 역할 경계가 유효하므로 전체 재작성과 `ai-workflow.toml` 파일명 변경은 하지 않았다.
  - Spawn 이름이나 Agent 자기 선언만으로 custom developer instruction 주입을 확정하지 않고, 전용 마커 또는 동등한 런타임 증거가 없으면 `NOT_VERIFIED`로 판정한다.
  - 디렉터리 구조와 책임은 유지되어 `index.md`는 변경하지 않았다.

- Issues encountered:
  - `/root/backend`, `/root/ai_workflow`, `/root/frontend`, `/root/validator` Spawn 이름은 확인됐지만 네 역할 모두 전용 마커를 반환하지 못해 실제 custom profile 주입은 `NOT_VERIFIED`다.
  - `codex --strict-config features list` 조합은 현재 0.144.5에서 지원되지 않아 반복하지 않고 일반 feature 조회와 Doctor·fresh exec로 대체했다.
  - Codex 실행 package와 npm update 대상 불일치 및 기존 rollout scan 경고는 재현됐으며 프로젝트 설정 문제와 분리했다.

- Validation:
  - Python `tomllib` 검사로 프로젝트 config와 Agent TOML 4개의 문법, 필수 필드, 이름·마커 유일성, `max_threads=4`, `max_depth=1`, 보호 경로와 Validator read-only 설정을 확인했다.
  - 한글·영문 무제한 반복 문구 검색 결과 위험 문구 그룹은 0개였다.
  - Round 1은 read-only 부모에서 구현 Agent 3개, Round 2는 별도 read-only 부모에서 Validator 1개를 생성했으며 하위 생성·재시도·자동 수정은 모두 0이었다.
  - 스모크 전후 변경 파일 5개와 diff hash `13e98e88530fec4932ecbe6b4cdadb85ce999195`가 동일했고 `git diff --check`가 통과해 Agent에 의한 파일 변경이 없음을 확인했다.

- Next steps:
  - custom Agent 선택 이름과 developer instruction layer를 직접 노출하는 Codex 런타임 메타데이터가 제공될 때 동일 마커 검증을 역할별 1회로 다시 수행한다.
  - 로컬 Codex 설치 경로와 rollout 경고는 프로젝트 Agent 설정과 별도의 수동 환경 정비 작업으로 처리한다.

## [2026-07-17] Session Summary (Codex 서브 에이전트 및 진행 이력 운영 표준화)

- What was done:
  - 기준 `query-forge/progress.md`를 직접 분석하고 관리 대상 기존 `progress.md` 21개의 모든 상태·결정·문제·검증·후속 기록을 표준 Session 구조로 재배치했다.
  - `AGENTS.md`, 공통 workflow와 문서 추적 규칙에 역할별 최신 5개 조회, 제한된 과거 검색, 루트 관리자 책임, 파일 소유권, 순차·병렬 위임과 구조화 Handoff 규칙을 통합했다.
  - 프로젝트 `.codex/config.toml`에 `agents.max_threads = 4`, `agents.max_depth = 1`을 추가하고 `backend`, `ai_workflow`, `frontend`, `validator` 커스텀 역할을 구성했다.
  - 사용자 전역 설정에는 이 프로젝트의 trust 항목만 추가해 프로젝트 로컬 설정과 역할을 실제 로드할 수 있게 했다. 모델·provider·인증·권한·MCP·플러그인 설정은 변경하지 않았다.

- Key decisions:
  - 기준 파일의 Session 제목과 상단 최신 기록 배치를 채택하되, 중간 Overview/Notes, 필드 누락과 날짜 역전은 복제하지 않고 사용자 요청의 엄격한 표준을 적용했다.
  - 세션 분할 근거가 없는 기존 상태 문서는 원래 수정일의 단일 초기화 Session으로 옮겨 의미를 보존했다.
  - 현재 사용자 요청을 받은 루트 Codex 스레드를 관리자이자 문서 통합 책임자로 유지하고 별도 `manager.toml`은 만들지 않았다.
  - 구현 역할은 부모 모델·권한을 상속하며 검증 역할만 read-only 기본값을 갖는다. 다만 부모의 실시간 권한 override가 우선할 수 있어 검증 전후 diff 확인을 함께 요구한다.

- Issues encountered:
  - 설치된 Codex 0.144.5에는 `codex status` 서브명령이 없어 `codex doctor`와 실제 read-only 실행으로 대체했다.
  - `codex doctor`는 프로젝트 설정 파싱과 Root를 정상 확인했지만 JetBrains/npx 실행 package와 npm global package 경로 불일치 및 기존 rollout scan 경고 때문에 전체 종료 상태는 실패다.
  - `codex exec --ephemeral`에서 첫 subagent 생성이 루트 thread ID를 찾지 못하는 오류가 재현됐다. 일반 read-only `codex exec`에서는 `backend`가 정상 로드됐고 나머지 세 역할도 실제 로드 응답을 확인했다.

- Validation:
  - Python 정적 검사로 관리 대상 `progress.md` 22개의 H1, 단일 Overview, 제목 패턴, 다섯 필드와 최신순을 확인했다. 기존 21개 문서의 168개 legacy 섹션이 현재 문서에 보존됐음을 Git HEAD와 대조했다.
  - Python `tomllib`로 프로젝트 config와 Agent TOML 4개의 구문, 필수 필드, `[agents]` 값과 validator read-only를 확인했다.
  - `codex --strict-config ... doctor --summary`에서 config와 repository Root 로드를 확인하고, read-only Codex 세션에서 루트 `AGENTS.md` 및 네 custom agent 이름을 실제로 확인했다.
  - 변경 Markdown 전체의 Prettier, 상대 링크 314개, `git diff --check`와 최종 Git 변경 범위 검사가 통과했다. 변경은 Markdown과 TOML에 한정되고 비즈니스 코드 변경은 없다.
  - 독립 검증 에이전트가 22개 문서 형식, 기존 실질 기록 405/405줄 보존, Agent 설정·실제 로드와 무수정 범위를 재확인해 `PASS WITH WARNINGS`로 판정했다. 경고는 프로젝트 밖의 Codex 로컬 환경 문제에 한정된다.

- Next steps:
  - 향후 개발 요청부터 파일 소유권을 분리해 네 전문 역할을 선택하고 루트 관리자가 결과와 추적 문서를 통합한다.
  - Codex 설치 경로 불일치와 ephemeral subagent thread 오류는 프로젝트 설정과 별개인 로컬 CLI 후속 점검 대상으로 남긴다.

## [2026-07-17] Session Summary (초기 개발 환경 및 문서 체계 구축)

- What was done:
  - 당시 구현 상태:
    - 초기 프론트엔드, 백엔드, Docker Compose, CI 환경이 구성되어 있다.
    - 제품 기능·API·DB·화면·기술 명세는 `docs/spec/`에 존재한다.
    - 실제 비즈니스 Controller, 도메인 모델, 공통 응답·예외 처리 코드는 아직 구현되지 않았다.
    - Codex 설정, 작업 규칙과 21개 관리 대상 디렉터리의 문서 계층 구성이 완료됐다.
  - 완료된 작업:
    - Java 21/Spring Boot 4.1/Gradle 백엔드 초기 환경 구성
    - Vue 3/TypeScript/Vite/pnpm 프론트엔드 초기 환경 구성
    - PostgreSQL 18/pgvector, MinIO, 선택적 Mailpit Compose 구성
    - GitHub Actions CI, Dependabot, `.gitignore`, 환경 변수 예시 구성
    - 현재 저장소와 기존 AI 에이전트 설정 파일 조사
    - 레퍼런스 `orchestrator-module-hardening`의 공통 응답·예외 처리 구조 읽기 전용 분석
    - 루트 `AGENTS.md`, `index.md`, `progress.md` 최초 생성
    - `.codex/config.toml`과 공통·문서·백엔드·응답/예외·프론트엔드·인프라 규칙 6종 생성
    - 21개 관리 대상 디렉터리에 `index.md`, `progress.md` 42개 생성
    - 레퍼런스 분석 결과와 현재 API 계약을 조정한 응답·예외 예상 package 및 적용 규칙 확정
    - Git에서 해석하지 않는 `.gitattributes` brace 패턴을 명시적 확장자별 LF 규칙으로 교체
    - 초기 worktree를 repository 정책, 제품 명세, backend, frontend, infrastructure, CI, Codex 문서의 intent별 commit으로 분리
  - 당시 진행 중인 작업:
    없음. 이번 Codex 설정·문서화 범위는 완료됐으며 비즈니스 기능은 시작하지 않았다.

- Key decisions:
  - `AGENTS.md`를 유일한 자동 로드 진입점으로 사용하고 상세 규칙은 `docs/agent-rules/`에 둔다.
  - `.codex/rules`를 코딩 지침 저장소로 오용하지 않는다. 해당 경로는 Codex 명령 실행 정책용이므로 현재는 별도 명령 정책을 추가하지 않는다.
  - Spring 응답·예외 처리는 중앙 변환 구조를 채택하되 `docs/spec/api.md`의 응답 계약과 실제 HTTP 상태를 유지한다.
  - 이 작업에서는 비즈니스 코드를 추가하지 않고 예상 패키지 구조와 적용 규칙을 먼저 문서화한다.

- Issues encountered:
  - 현재 API 명세는 성공 DTO 직접 반환과 실제 HTTP 상태 코드를 요구하지만, 레퍼런스는 모든 응답을 `BaseResponseDto`로 감싸고 오류도 기본 HTTP 200으로 반환한다. 기존 API 명세를 우선하고 구조적 패턴만 적용하기로 했다.
  - 레퍼런스 `ErrorCode`에는 중복 번호와 이름 불일치가 있어 현재 프로젝트에 그대로 복사할 수 없다.
  - 원격 GitHub Actions 실행 이력과 branch protection 상태는 로컬 저장소만으로 확인할 수 없다.
  - `src/main/resources` 아래의 추적 Markdown은 현재 빌드 시 classpath 리소스에 포함될 수 있다. 운영 패키징 전에 제외 정책을 검토해야 한다.

- Validation:
  - 문서 생성 전 기존 파일·디렉터리와 AI 에이전트 설정 후보를 `rg --files -uu`, `git status --short`로 조사했다.
  - 레퍼런스 Java 소스와 현재 `docs/spec/api.md`를 직접 대조했다.
  - PowerShell 정적 검사로 21개 디렉터리, 추적 문서 42개, 필수 섹션, 55개 Markdown 파일의 상대 링크를 확인했다. 결과: 성공.
  - 통합 정적 검사 script는 작성 중 두 차례 PowerShell 구문 오류가 있었고 수정 후 `documentation_validation=PASS`를 확인했다.
  - 전체 신규 Markdown Prettier 최초 검사에서 30개 파일의 형식 차이가 발견됐다. 신규 문서에만 `--write`를 적용한 뒤 49개 파일 재검사가 통과했다.
  - 커밋 단위 검사에서 README/Compose와 GitHub YAML의 format 차이를 추가로 발견해 해당 파일만 Prettier로 정리한 뒤 재검사를 통과했다.
  - `Set-Location backend; .\gradlew.bat check`: 성공. Java test source는 아직 없다.
  - `Set-Location frontend; corepack pnpm check`: 성공. ESLint, Prettier, TypeScript, Vitest, production build가 통과했으나 Vitest test file은 없다.
  - `docker compose config --quiet`: 성공.
  - `codex features list`: 성공. 프로젝트 TOML 구문과 현재 trusted 환경의 설정 load를 확인했다.
  - `git check-attr text eol`로 Markdown, TypeScript, Kotlin DSL에 `text/eol=lf`가 적용되는지 확인했다.
  - `git-commit` workflow에 따라 각 단위의 staged name/status, stat, 전체 whitespace를 확인하고 AngularJS-style commit message로 순차 커밋했다.
  - 실제 GitHub-hosted CI와 Playwright E2E는 실행 이력/test file이 없어 미검증 상태다.

- Next steps:
  - 공통 오류 응답 및 예외 처리 구조 실제 구현과 테스트
  - 인증, 프로필, 문서, 공고, 자기소개서, 면접, Agent Run 기능 개발
  - 운영 배포 환경과 관찰성 구성
  - 원격 저장소의 branch protection과 PR 운영 정책 확정
