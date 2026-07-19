# 프로필 도메인 테스트 안내

## 디렉터리 목적

P2 완료도·날짜·GPA·대표 학력·direct evidence 정책을 DB 없이 빠르게 검증한다.

## 주요 파일 및 하위 디렉터리

- [`ProfileDomainTest.java`](ProfileDomainTest.java): 완료 항목, validation, source mapping과 evidence 재생성
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- application·HTTP와 분리된 순수 정책 회귀를 고정한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`profile/`](../index.md)의 테스트 책임을 구체화한다.
- 기능 계약은 [`../../../../../../../../docs/spec/functional.md`](../../../../../../../../docs/spec/functional.md)를 따른다.

## 변경 시 주의사항

- DB CHECK와 중복되더라도 사용자 입력에 가까운 domain validation을 제거하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../docs/agent-rules/backend-development.md)
- [영역 진행 상황](progress.md)
