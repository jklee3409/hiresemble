# Research infrastructure 안내

## 디렉터리 목적

조사 run·topic·source·provenance의 PostgreSQL 조회·저장을 담당한다.

## 주요 파일 및 하위 디렉터리

- `ResearchStore`: owner-scoped JDBC query와 atomic 조사 결과 저장
- [`progress.md`](progress.md): infrastructure 상태

## 구성 요소 역할

URL dedupe, topic-source primary link, coverage와 terminal 상태를 V12 제약에 맞춰 저장한다.

## 다른 디렉터리와의 의존 관계

schema는 [`../../../../../resources/db/migration/`](../../../../../resources/db/migration/index.md)에 있다.

## 변경 시 주의사항

동적 sort는 service allowlist 결과만 받고 모든 cross-reference에 `user_id`를 포함한다.

## 관련 규칙 및 문서

- [상위 Research 영역](../index.md)
- [Infrastructure 규칙](../../../../../../../docs/agent-rules/infrastructure.md)
- [진행 상황](progress.md)
