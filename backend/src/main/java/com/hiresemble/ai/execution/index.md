# AI execution package 안내

## 디렉터리 목적

workflow 실패를 retry classification과 안전한 공개 code/message로 변환한다.

## 주요 파일 및 하위 디렉터리

- `AiExecutionException`: retryable 여부와 safe projection
- [`progress.md`](progress.md): 오류 경계 상태

## 구성 요소 역할

provider나 내부 cause 대신 안정적인 분류만 orchestration에 전달한다.

## 다른 디렉터리와의 의존 관계

workflow, router, validator와 disabled gateway가 이 오류를 사용한다.

## 변경 시 주의사항

stack trace, SQL, token, key, 원문 prompt/response를 safe message에 포함하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [응답·예외 규칙](../../../../../../../../docs/agent-rules/backend-response-exception.md)
