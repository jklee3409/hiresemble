# Cover Letter 영역 안내

## 디렉터리 목적

P7 자기소개서의 owner-scoped 생성·목록·편집, 문항, immutable 답변 version, 근거 provenance, 검증, 최종화와 보관 수명주기를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): 공개 자기소개서 HTTP 계약과 DTO mapping
- [`application/`](application/index.md): 상태·version·검증·Agent Run use case와 AI port
- [`domain/`](domain/index.md): canonical enum, 상태 전이와 TipTap canonicalization
- [`infrastructure/`](infrastructure/index.md): PostgreSQL store와 AI 비용 설정
- [`progress.md`](progress.md): P7 구현·검증 이력

## 구성 요소 역할

하나의 공고에 active 자기소개서를 최대 하나로 제한하고, 답변·검증·근거는 immutable history로 보존한다. AI workflow는 application query/command port만 사용하며 JDBC row나 공개 DTO에 의존하지 않는다.

## 다른 디렉터리와의 의존 관계

- [`../job/`](../job/index.md)의 최신 공고·분석 projection을 생성·검증 context에 사용한다.
- [`../profile/`](../profile/index.md)과 [`../document/`](../document/index.md)의 현재 `VERIFIED` 근거와 masked chunk 검색 경계를 사용한다.
- [`../agentrun/`](../agentrun/index.md)과 cover letter·answer version typed resource를 연결한다.
- [`../ai/`](../ai/index.md)이 고정 generation·verification workflow를 실행한다.

## 변경 시 주의사항

모든 조회·변경은 `user_id`를 포함하고 `ARCHIVED`는 unarchive 외 읽기 전용으로 유지한다. 답변·검증 row를 수정하지 않으며 current 교체, FINALIZED→DRAFT와 optimistic CAS를 같은 transaction에서 처리한다.

## 관련 규칙 및 문서

- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [API 명세](../../../../../../../docs/spec/api.md)
- [DB 명세](../../../../../../../docs/spec/db.md)
- [영역 진행 상황](progress.md)
