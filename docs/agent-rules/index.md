# `docs/agent-rules` 디렉터리 안내

## 디렉터리 목적

이 디렉터리는 `AGENTS.md`가 작업 유형별로 라우팅하는 Codex 세부 규칙을 관리한다. 제품 요구사항을 정의하는 곳이 아니라, 요구사항을 안전하고 일관되게 구현·검증·기록하는 방법을 정의한다.

## 주요 파일 및 역할

| 파일                                                             | 역할                                                            |
| ---------------------------------------------------------------- | --------------------------------------------------------------- |
| [`workflow.md`](workflow.md)                                     | 역할별 제한 컨텍스트, 구현, 검증, 통합 완료 절차                |
| [`documentation-tracking.md`](documentation-tracking.md)         | `index.md` 책임과 Session 기반 `progress.md` 규칙               |
| [`backend-development.md`](backend-development.md)               | Spring 계층·책임별 package 경계, 코딩·OpenAPI·테스트 규칙       |
| [`backend-response-exception.md`](backend-response-exception.md) | 레퍼런스 분석과 현재 프로젝트의 응답·예외 적용 규칙             |
| [`frontend-development.md`](frontend-development.md)             | Vue/TypeScript 프론트엔드 규칙                                  |
| [`infrastructure.md`](infrastructure.md)                         | Docker Compose, 환경 변수, DB migration, CI 규칙                |
| [`progress.md`](progress.md)                                     | 에이전트 규칙의 작성·검증 상태                                  |

## 다른 디렉터리와의 의존 관계

- [`../../AGENTS.md`](../../AGENTS.md)가 이 규칙들의 필수 열람 조건과 우선순위를 정의한다.
- [`../spec/`](../spec/)은 구현할 제품 계약을 제공하며 이 규칙보다 계약상 우선한다.
- 코드·설정 디렉터리의 `index.md`는 관련 규칙에 상대 경로로 연결한다.

## 변경 시 주의사항

- 동일한 공통 규칙을 여러 파일에 복제하지 말고 `workflow.md` 또는 `documentation-tracking.md`로 연결한다.
- 작업 이력은 관련 문서의 최신 Session 5개만 기본 조회하고 과거 이력은 근거가 있는 제한 검색으로 확인한다.
- 기술 스택이 바뀌면 해당 규칙과 `docs/spec/tech_stack.md`, 모듈 문서를 함께 검토한다.
- 레퍼런스 분석 결과와 현재 프로젝트에 실제 적용할 정책을 명확히 구분한다.
- 자동 로드되는 문서는 루트 `AGENTS.md`뿐이므로 새 규칙은 반드시 그 라우팅 표에 연결한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../AGENTS.md)
- [제품 명세 안내](../spec/index.md)
- [문서 추적 규칙](documentation-tracking.md)
- [디렉터리 진행 상황](progress.md)
