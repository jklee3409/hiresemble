# Hiresemble 프로젝트 진행 상황

## 현재 구현 상태

- 초기 프론트엔드, 백엔드, Docker Compose, CI 환경이 구성되어 있다.
- 제품 기능·API·DB·화면·기술 명세는 `docs/spec/`에 존재한다.
- 실제 비즈니스 Controller, 도메인 모델, 공통 응답·예외 처리 코드는 아직 구현되지 않았다.
- Codex 설정, 작업 규칙과 21개 관리 대상 디렉터리의 문서 계층 구성이 완료됐다.

## 완료된 작업

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

## 진행 중인 작업

없음. 이번 Codex 설정·문서화 범위는 완료됐으며 비즈니스 기능은 시작하지 않았다.

## 남은 작업

- 공통 오류 응답 및 예외 처리 구조 실제 구현과 테스트
- 인증, 프로필, 문서, 공고, 자기소개서, 면접, Agent Run 기능 개발
- 운영 배포 환경과 관찰성 구성
- 원격 저장소의 branch protection과 PR 운영 정책 확정

## 확인된 문제

- 현재 API 명세는 성공 DTO 직접 반환과 실제 HTTP 상태 코드를 요구하지만, 레퍼런스는 모든 응답을 `BaseResponseDto`로 감싸고 오류도 기본 HTTP 200으로 반환한다. 기존 API 명세를 우선하고 구조적 패턴만 적용하기로 했다.
- 레퍼런스 `ErrorCode`에는 중복 번호와 이름 불일치가 있어 현재 프로젝트에 그대로 복사할 수 없다.
- 원격 GitHub Actions 실행 이력과 branch protection 상태는 로컬 저장소만으로 확인할 수 없다.
- `src/main/resources` 아래의 추적 Markdown은 현재 빌드 시 classpath 리소스에 포함될 수 있다. 운영 패키징 전에 제외 정책을 검토해야 한다.

## 기술적 결정 사항

- `AGENTS.md`를 유일한 자동 로드 진입점으로 사용하고 상세 규칙은 `docs/agent-rules/`에 둔다.
- `.codex/rules`를 코딩 지침 저장소로 오용하지 않는다. 해당 경로는 Codex 명령 실행 정책용이므로 현재는 별도 명령 정책을 추가하지 않는다.
- Spring 응답·예외 처리는 중앙 변환 구조를 채택하되 `docs/spec/api.md`의 응답 계약과 실제 HTTP 상태를 유지한다.
- 이 작업에서는 비즈니스 코드를 추가하지 않고 예상 패키지 구조와 적용 규칙을 먼저 문서화한다.

## 테스트 및 검증 결과

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

## 마지막 수정 일자

2026-07-17
