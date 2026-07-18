# Progress

## Overview

- 초기 프론트엔드, 백엔드, Docker Compose, CI 환경이 구성되어 있다.
- 제품 기능·API·DB·화면·기술 명세는 `docs/spec/`에 존재한다.
- 실제 비즈니스 Controller, 도메인 모델, 공통 응답·예외 처리 코드는 아직 구현되지 않았다.
- Codex 관리자·전문 서브 에이전트 설정, 작업 규칙과 22개 관리 대상 디렉터리의 Session 기반 문서 계층이 구성되어 있다.

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
