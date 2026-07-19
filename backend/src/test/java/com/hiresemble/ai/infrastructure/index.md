# AI infrastructure 테스트 안내

## 디렉터리 목적

production disabled gateway가 network fallback 없이 안전하게 실패하는지 검증한다.

## 주요 파일 및 하위 디렉터리

- `DisabledAiGatewaysTest`
- [`progress.md`](progress.md)

## 구성 요소 역할

세 gateway의 safe configuration failure를 고정한다.

## 다른 디렉터리와의 의존 관계

[`../../../../../../main/java/com/hiresemble/ai/infrastructure/`](../../../../../../main/java/com/hiresemble/ai/infrastructure/index.md)을 검증한다.

## 변경 시 주의사항

network mock조차 필요 없는 disabled 경계를 유지한다.

## 관련 규칙 및 문서

- [상위 AI 테스트](../index.md)
