# Agent Run 영역 안내

## 디렉터리 목적

PostgreSQL을 상태 원천으로 하는 Agent Run·Step 수명주기, 비용 예약·정산, worker claim·복구와 공개 조회·SSE 계약 및 typed resource 연결을 소유한다.

## 주요 파일 및 하위 디렉터리

- [api/](api/index.md): HTTP 전송 계층
- [application/](application/index.md): use case와 application 경계
- [domain/](domain/index.md): 도메인 모델과 규칙
- [infrastructure/](infrastructure/index.md): 영속성·외부 연동 구현
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

DB row와 application port가 실행 상태를 소유하며 AI workflow는 이 영역의 repository를 직접 사용하지 않는다. 공개 DTO는 claim, lease, hash, prompt와 provider 식별자를 노출하지 않는다. P7 generation은 자기소개서를, verification은 immutable answer version을 typed resource로 연결하고 결과 본문은 복제하지 않는다.

## 다른 디렉터리와의 의존 관계

- [`../ai/`](../ai/index.md)는 application port만 소비한다.
- 인증 사용자 ID는 [`../auth/`](../auth/index.md)에서 받는다.
- V4 schema는 [`../../../../resources/db/migration/`](../../../../resources/db/migration/index.md)이 소유한다.

## 변경 시 주의사항

- terminal Run을 다시 열거나 stale RUNNING을 같은 Run의 QUEUED로 되돌리지 않는다.
- typed resource는 owner composite FK를 유지하고 실행 결과 본문을 Agent Run DTO에 복제하지 않는다.
- 외부 호출은 DB transaction 밖에서 수행하고 상태 변경 event는 commit 뒤 발행한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../AGENTS.md)
- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [API 명세](../../../../../../../docs/spec/api.md)
- [DB 명세](../../../../../../../docs/spec/db.md)
