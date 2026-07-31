# Interview domain 안내

## 디렉터리 목적

P8 질문 type과 immutable 답변 source canonical enum을 소유한다.

## 주요 파일 및 하위 디렉터리

- `InterviewQuestionType`
- `InterviewAnswerVersionSource`
- [`progress.md`](progress.md): domain 상태

## 구성 요소 역할

API·DB·workflow가 공유하는 질문 분류와 서버 지정 답변 source vocabulary를 제공한다.

## 다른 디렉터리와의 의존 관계

활성 계약은 [`../../../../../../../../docs/spec/api.md`](../../../../../../../../docs/spec/api.md)다.

## 변경 시 주의사항

request에서 `FOLLOW_UP`을 허용하지 않되 생성 결과의 후속 질문은 보존한다. client가 답변 source를 지정하지 못하게 한다.

## 관련 규칙 및 문서

- [상위 Interview 영역](../index.md)
- [진행 상황](progress.md)
