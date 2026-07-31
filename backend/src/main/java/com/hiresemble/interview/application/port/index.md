# Interview application port 안내

## 디렉터리 목적

AI workflow가 면접 persistence 구현에 접근하지 않도록 owner-scoped query와 command 계약을 정의한다.

## 주요 파일 및 하위 디렉터리

- `InterviewWorkflowQueryPort`: 준비·feedback context 조회
- `InterviewWorkflowCommandPort`: 조사·질문·feedback atomic apply와 compensation
- [`progress.md`](progress.md): port 상태

## 구성 요소 역할

AI runtime은 이 port의 typed method만 사용하며 JDBC·JPA·HTTP DTO를 참조하지 않는다.

## 다른 디렉터리와의 의존 관계

구현은 [`../../infrastructure/`](../../infrastructure/index.md)와 [`../service/`](../service/index.md)에 있다.

## 변경 시 주의사항

owner ID와 Agent Run·resource ID를 항상 함께 전달하고 원문 prompt·response를 checkpoint 입력으로 만들지 않는다.

## 관련 규칙 및 문서

- [상위 application package](../index.md)
- [진행 상황](progress.md)
