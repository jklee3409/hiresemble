# AI budget package 안내

## 디렉터리 목적

AI 호출 전 reserve coverage와 terminal settle·release를 Backend budget port에 위임한다.

## 주요 파일 및 하위 디렉터리

- `BudgetGuard`: top-up, settle, release 해석
- [`progress.md`](progress.md): 구현 상태

## 구성 요소 역할

금액 원자성은 Agent Run persistence가 소유하고 이 package는 orchestration 순서만 표현한다.

## 다른 디렉터리와의 의존 관계

[`../../agentrun/application/`](../../agentrun/application/index.md)의 `BudgetReservationPort`를 사용한다.

## 변경 시 주의사항

정책 금액을 Java 상수로 넣지 않고 실제 유료 호출은 price version과 reserve가 없으면 시작하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [DB 명세](../../../../../../../../docs/spec/db.md)
