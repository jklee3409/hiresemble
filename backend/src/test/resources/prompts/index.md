# AI test prompt 안내

## 디렉터리 목적

P3 test-only Fake workflow의 최소 prompt fixture를 보관한다.

## 주요 파일 및 하위 디렉터리

- `p3-fake-fixture.txt`: 3-step structured output 테스트 지시문
- [`progress.md`](progress.md): fixture 상태

## 구성 요소 역할

PromptRegistry version·schema·allowlist 테스트에만 사용한다.

## 다른 디렉터리와의 의존 관계

[`../../java/com/hiresemble/ai/orchestration/`](../../java/com/hiresemble/ai/orchestration/index.md) integration test가 읽는다.

## 변경 시 주의사항

production bundle에 포함하거나 실제 비즈니스 결과를 흉내 내지 않는다.

## 관련 규칙 및 문서

- [상위 test resources](../index.md)
