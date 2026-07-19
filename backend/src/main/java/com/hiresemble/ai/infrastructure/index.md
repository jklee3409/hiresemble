# AI infrastructure package 안내

## 디렉터리 목적

P3 production 기본 gateway를 외부 네트워크 없는 disabled adapter로 제공한다.

## 주요 파일 및 하위 디렉터리

- `DisabledAiGateways`: Chat·Embedding·Search 공통 disabled adapter
- [`progress.md`](progress.md): adapter 상태

## 구성 요소 역할

provider가 구성되지 않은 실행을 안전한 configuration failure로 종료한다.

## 다른 디렉터리와의 의존 관계

[`../port/`](../port/index.md)의 세 gateway를 구현한다.

## 변경 시 주의사항

임의 fallback provider, API key 탐색과 network 호출을 추가하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [기술 명세](../../../../../../../../docs/spec/tech_stack.md)
