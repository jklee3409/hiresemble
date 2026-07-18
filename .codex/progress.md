# Progress

## Overview

프로젝트 문서 탐색 설정, 직접 위임 깊이와 네 전문 커스텀 에이전트가 구성되어 있다. 명령 실행 정책과 프로젝트 전용 Skill·MCP·Hook은 구성하지 않았다.

## [2026-07-17] Session Summary (프로젝트 커스텀 에이전트와 Trust 로드 구성)

- What was done:
  - 기존 문서 탐색 설정을 유지하면서 `[agents]`에 동시 thread 4개와 직접 자식 깊이 1을 통합했다.
  - 일반 백엔드, Spring AI 워크플로, Vue 프론트엔드와 읽기 전용 검증 역할을 네이티브 TOML로 정의하고 역할 경계와 결과 형식을 문서화했다.
  - 사용자 전역 config에 `E:\dev_factory\side-project\hiresemble`의 trusted 항목만 추가해 프로젝트 `.codex` layer를 활성화했다.

- Key decisions:
  - 루트 스레드가 관리자이므로 `manager.toml`은 만들지 않고 전문 역할의 재귀 위임은 `max_depth = 1`로 차단한다.
  - 프로젝트 설정은 전역 모델, reasoning, provider, 인증, approval과 개인 MCP·플러그인을 덮어쓰지 않는다.
  - `.codex/agents/*.toml`만 역할 정의이며 같은 목적의 Agent Markdown, `.codex/rules` 역할 문서와 영구 handoff 체계는 만들지 않는다.

- Issues encountered:
  - `codex status`는 0.144.5에서 지원되지 않는다.
  - Doctor 전체 결과에는 실행 package 경로 불일치와 기존 rollout scan 경고가 남아 있다.
  - ephemeral exec의 subagent thread 등록 오류가 재현되어 실제 `backend` 확인은 일반 read-only exec로 대체했다.

- Validation:
  - `codex-cli 0.144.5`, Git/Doctor Root `E:\dev_factory\side-project\hiresemble`, 전역 trust 항목과 `multi_agent` stable 상태를 확인했다.
  - Python `tomllib` 검사에서 config와 네 Agent TOML의 구문 및 필수 필드가 통과했다.
  - strict config read-only 실행에서 루트 `AGENTS.md`, `backend`, `ai_workflow`, `frontend`, `validator`의 실제 로드를 확인했다.
  - 독립 read-only 검증은 프로젝트 설정에 차단 Finding 없이 `PASS WITH WARNINGS`였으며, 경고는 기존 설치 경로·rollout scan·ephemeral thread 제약이다.

- Next steps:
  - 로컬 Codex 설치 package 경로와 ephemeral subagent 오류는 CLI 업데이트 또는 런타임 정리 시 다시 확인한다.

## [2026-07-17] Session Summary (프로젝트 Codex 기본 설정 구성)

- What was done:
  - 당시 구현 상태:
    프로젝트 범위의 최소 Codex 설정이 구성되어 있다. 명령 실행 정책과 프로젝트 전용 MCP/Hook은 구성하지 않았다.
  - 완료된 작업:
    - `config.toml` 생성
    - `AGENTS.md` 합산 허용량을 64 KiB로 설정
    - 프로젝트 루트 marker를 `.git`으로 명시
    - 대체 지침 파일 없이 `AGENTS.md`를 단일 표준 파일명으로 확정
  - 당시 진행 중인 작업:
    없음.

- Key decisions:
  - 개인 모델 선택, provider, 인증, sandbox, approval 정책은 프로젝트 설정에서 강제하지 않는다.
  - `.codex/rules`를 일반 개발 규칙 저장소로 사용하지 않는다.

- Issues encountered:
  - 프로젝트 로컬 설정은 저장소가 trusted 상태일 때만 로드된다. 신뢰하지 않은 환경에서는 Codex 기본값이 사용된다.

- Validation:
  - `codex features list`를 저장소 루트에서 실행해 TOML 구문과 현재 trusted 환경의 프로젝트 설정 load가 성공함을 확인했다.
  - `AGENTS.md` 크기는 9,081 bytes로 `project_doc_max_bytes=65,536` 이내다.

- Next steps:
  - 실제로 명령 실행 정책이 필요해질 경우 공식 rules 문법과 팀 승인 경계를 검토
  - Codex 새 세션에서 프로젝트 지침 로드 여부를 운영 중 확인
