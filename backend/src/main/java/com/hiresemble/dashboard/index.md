# Dashboard 영역 안내

## 디렉터리 목적

로그인 사용자의 지원 준비 요약과 `Asia/Seoul` 기준 월별 활성 마감, 전역 게시 Career Guide를 읽기 전용 projection으로 제공한다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): Dashboard·Career Guide HTTP 응답과 validation
- [`application/`](application/index.md): 월 경계·프로필 준비도·마감 grouping use case
- [`infrastructure/`](infrastructure/index.md): owner-scoped 집계와 게시 가이드 JDBC query
- [`progress.md`](progress.md): 구현·검증 이력

## 구성 요소 역할

기존 Profile·Document·Job·Agent Run aggregate를 변경하지 않고 화면에 필요한 정확 집계를 한 번의 read 경계로 조합한다. Career Guide는 사용자 소유 데이터가 아닌 전역 게시 콘텐츠다.

## 다른 디렉터리와의 의존 관계

- [`../profile/`](../profile/index.md), [`../document/`](../document/index.md), [`../job/`](../job/index.md), [`../agentrun/`](../agentrun/index.md)의 현재 저장 계약을 읽는다.
- 공개 계약은 [`../../../../../../../docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`../../../../../../../docs/spec/db.md`](../../../../../../../docs/spec/db.md)를 따른다.

## 변경 시 주의사항

모든 사용자 집계는 인증 user ID로 제한하고 `CLOSED` 공고를 활성 마감에 포함하지 않는다. 날짜 경계는 JVM·DB 기본 timezone이 아니라 명시적인 서울 기준으로 계산한다.

## 관련 규칙 및 문서

- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [영역 진행 상황](progress.md)
