# Interview infrastructure 안내

## 디렉터리 목적

P8 question set·question·answer version·feedback PostgreSQL 저장을 담당한다.

## 주요 파일 및 하위 디렉터리

- `InterviewStore`: owner-scoped JDBC 조회·atomic persistence
- [`progress.md`](progress.md): infrastructure 상태

## 구성 요소 역할

V12 owner 복합 FK, current partial unique, immutable trigger와 typed provenance 제약에 맞춰 데이터를 저장한다.

## 다른 디렉터리와의 의존 관계

schema는 [`../../../../../resources/db/migration/`](../../../../../resources/db/migration/index.md)에 있다.

## 변경 시 주의사항

동적 sort는 allowlist만 받고 history soft delete가 domain row를 cascade하지 않도록 유지한다.

## 관련 규칙 및 문서

- [상위 Interview 영역](../index.md)
- [Infrastructure 규칙](../../../../../../../docs/agent-rules/infrastructure.md)
- [진행 상황](progress.md)
