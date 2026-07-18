# Progress

## Overview

프로젝트 문서 탐색 설정, 직접 위임 깊이와 네 전문 커스텀 에이전트가 구성되어 있다. 명령 실행 정책과 프로젝트 전용 Skill·MCP·Hook은 구성하지 않았다.

## [2026-07-18] Session Summary (유한 오케스트레이션 정책과 read-only 런타임 재검증)

- What was done:
  - 기존 `max_threads=4`, `max_depth=1`을 유지하고 루트 지침에 라운드·역할별 생성·재시도·종료 상한을 추가했다.
  - 지원되는 `codex exec --sandbox read-only --strict-config`로 fresh 부모 세션 두 개를 실행해 네 역할을 총 4회 생성했다.

- Key decisions:
  - 유한 실행 정책은 지원되지 않는 임의 config 키를 만들지 않고 루트 오케스트레이션 지침으로 통제한다.
  - 프로젝트 config, Agent 파일명과 trust 설정은 유효하므로 변경하지 않았다.

- Issues encountered:
  - Spawn 메타데이터의 역할 경로는 확인됐지만 전용 마커가 반환되지 않아 custom developer instruction 주입은 확인하지 못했다.
  - Doctor의 설치 경로 불일치와 rollout 경고는 프로젝트 config load와 별개의 로컬 환경 문제로 남아 있다.

- Validation:
  - TOML 정적 파싱, 필수 필드·이름·마커 중복 검사, 위험 반복 문구 검색이 통과했다.
  - 두 부모 세션 모두 read-only를 보고했고 전체 2개 라운드, Agent 4개, 하위 생성 0, 재시도 0으로 종료했다.
  - 스모크 전후 Git 변경 목록과 diff hash가 동일했다.

- Next steps:
  - 현재 Surface가 custom Agent 활성 profile 또는 developer instruction 출처를 노출할 때 마커 기반 인식을 다시 확인한다.

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
