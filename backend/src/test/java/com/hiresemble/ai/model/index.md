# AI model 테스트 안내

## 디렉터리 목적

quality/tier routing, 승격 제한과 disabled provider를 검증한다.

## 주요 파일 및 하위 디렉터리

- `PolicyModelRouterTest`
- [`progress.md`](progress.md)

## 구성 요소 역할

HIGH_QUALITY gate와 LOW_COST→BALANCED 단일 승격을 고정한다.

## 다른 디렉터리와의 의존 관계

[`../../../../../../main/java/com/hiresemble/ai/model/`](../../../../../../main/java/com/hiresemble/ai/model/index.md)을 검증한다.

## 변경 시 주의사항

공개 quality와 provider product key를 혼동하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 테스트](../index.md)
