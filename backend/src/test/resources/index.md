# Backend test resources 안내

## 디렉터리 목적

Backend 테스트 전용 정적 fixture를 보관한다.

## 주요 파일 및 하위 디렉터리

- [`prompts/`](prompts/index.md): P3 Fake workflow prompt
- [`progress.md`](progress.md): fixture 변경 이력

## 구성 요소 역할

production resource와 분리된 테스트 입력만 제공한다.

## 다른 디렉터리와의 의존 관계

테스트 Java source는 [`../java/`](../java/index.md)에 있다.

## 변경 시 주의사항

실제 사용자 데이터, API key와 production prompt를 넣지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../AGENTS.md)
- [Backend 개발 규칙](../../../../docs/agent-rules/backend-development.md)
