# AI validation package 안내

## 디렉터리 목적

AI structured output의 parsing부터 domain command까지 검증 순서를 강제한다.

## 주요 파일 및 하위 디렉터리

- `StructuredOutputValidator`: JSON→schema→record→workflow→domain validation
- [`progress.md`](progress.md): 검증 상태

## 구성 요소 역할

형식 오류는 제한된 자동 retry로, domain command 오류는 비재시도로 분류한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 gateway 응답 직후 호출한다.

## 변경 시 주의사항

provider 원문과 validation 내부 예외를 safe error로 반환하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [응답·예외 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
