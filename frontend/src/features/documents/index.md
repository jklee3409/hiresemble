# Documents feature 안내

## 디렉터리 목적

P4 문서 query key, URL filter, upload·manual text·reparse·download·delete와 Agent Run stream 연결 규칙을 제공한다.

## 주요 파일 및 하위 디렉터리

- document query key와 user-scoped cache helper
- list filter canonicalization과 상태 presentation
- upload·manual text validation 및 Run monitor
- [`progress.md`](progress.md): feature 구현 이력

## 구성 요소 역할

REST Document 상태를 최종 원천으로 사용하고 SSE terminal·WAITING_USER에는 document/text/evidence query를 invalidate한다.

## 다른 디렉터리와의 의존 관계

- [`../../shared/api/`](../../shared/api/index.md)의 typed Document API를 사용한다.
- route page는 [`../../pages/`](../../pages/index.md), SSE는 [`../agent-runs/`](../agent-runs/index.md)이 담당한다.

## 변경 시 주의사항

모든 key에 user ID를 포함하고 logout·401·사용자 전환·문서 삭제 시 cache와 EventSource를 정리한다.

## 관련 규칙 및 문서

- [Frontend 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [페이지 명세](../../../../docs/spec/page.md)
- [영역 진행 상황](progress.md)
