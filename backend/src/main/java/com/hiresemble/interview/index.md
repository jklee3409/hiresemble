# Interview 영역 안내

## 디렉터리 목적

P8 면접 준비 접수, 예상 질문 set, immutable 답변 version과 성공한 AI feedback 수명주기를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): P8 준비·질문·답변·feedback HTTP 계약
- [`application/`](application/index.md): prerequisite·CAS·workflow port·retry use case
- [`domain/`](domain/index.md): 질문 type과 답변 source enum
- [`infrastructure/`](infrastructure/index.md): PostgreSQL store와 AI 비용 설정
- [`progress.md`](progress.md): P8 면접 영역 구현·검증 이력

## 구성 요소 역할

준비 요청은 research run·question set·Agent Run을 한 transaction에서 만들고, AI 결과는 command port를 통해 짧은 atomic apply transaction으로 저장한다.

## 다른 디렉터리와의 의존 관계

- [`../research/`](../research/index.md)의 source·coverage와 authoritative provenance를 사용한다.
- [`../job/`](../job/index.md), [`../coverletter/`](../coverletter/index.md), [`../profile/`](../profile/index.md)의 owner-scoped 현재 context를 사용한다.
- [`../ai/`](../ai/index.md)과 [`../agentrun/`](../agentrun/index.md)이 준비·feedback workflow와 audit을 제공한다.

## 변경 시 주의사항

학력은 structured final education으로만 context에 넣고 education tombstone을 질문 근거로 연결하지 않는다. 답변·feedback row는 수정하지 않는다.

## 관련 규칙 및 문서

- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [API 명세](../../../../../../../docs/spec/api.md)
- [DB 명세](../../../../../../../docs/spec/db.md)
- [영역 진행 상황](progress.md)
