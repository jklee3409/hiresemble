# Agent Run 영역 안내

## 디렉터리 목적

PostgreSQL을 상태 원천으로 하는 Agent Run·Step 수명주기, 비용 예약·정산, worker claim·복구와 공개 조회·SSE 계약을 소유한다.

## 주요 파일 및 하위 디렉터리

- [`domain/`](domain/index.md): Run·Step 상태와 안전한 공개 값
- [`application/`](application/index.md): workflow launcher와 query/state/checkpoint/budget/apply port
- [`infrastructure/`](infrastructure/index.md): JDBC 저장, dispatcher, lease와 reconciliation
- [`api/`](api/index.md): owner-scoped 5개 HTTP operation과 SSE projection
- [`progress.md`](progress.md): P3 구현·검증 이력

## 구성 요소 역할

DB row와 application port가 실행 상태를 소유하며 AI workflow는 이 영역의 repository를 직접 사용하지 않는다. 공개 DTO는 claim, lease, hash, prompt와 provider 식별자를 노출하지 않는다.

## 다른 디렉터리와의 의존 관계

- [`../ai/`](../ai/index.md)는 application port만 소비한다.
- 인증 사용자 ID는 [`../auth/`](../auth/index.md)에서 받는다.
- V4 schema는 [`../../../../resources/db/migration/`](../../../../resources/db/migration/index.md)이 소유한다.

## 변경 시 주의사항

- terminal Run을 다시 열거나 stale RUNNING을 같은 Run의 QUEUED로 되돌리지 않는다.
- P4 전에는 document/job typed resource FK나 공개 생성 endpoint를 추가하지 않는다.
- 외부 호출은 DB transaction 밖에서 수행하고 상태 변경 event는 commit 뒤 발행한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [API 명세](../../../../../../../docs/spec/api.md)
- [DB 명세](../../../../../../../docs/spec/db.md)
