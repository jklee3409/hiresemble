# 프론트엔드 기능 영역 안내

## 디렉터리 목적

사용자 기능별 form·상호작용 규칙을 page와 공용 기반에서 분리한다.

## 주요 파일 및 하위 디렉터리

- [`auth/`](auth/index.md): P1 인증 Form 입력 schema와 byte validation
- [`profile/`](profile/index.md): P2 프로필 Zod·query key·version 충돌·공용 입력 UI
- [`agent-runs/`](agent-runs/index.md): P3 목록·상세 projection, SSE 복구와 Progress Drawer
- [`documents/`](documents/index.md): P4 문서 upload·query·상태·SSE invalidation과 delete cleanup
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 실제 구현된 기능 규칙만 하위 feature에 두고 빈 미래 기능 계층을 만들지 않는다.
- profile feature는 서버 상태를 소유하지 않고 page가 Vue Query로 사용하는 schema·key·상호작용만 제공한다.
- Agent Run feature는 DB snapshot을 반영한 Vue Query cache와 연결 복구 상태를 분리한다.
- Documents feature는 user-scoped REST 상태를 원천으로 삼고 Agent Run stream을 query invalidation 신호로만 사용한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`src/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- server 권한·도메인 규칙을 UI에 중복 구현하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../AGENTS.md)
- [공통 작업 절차](../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
