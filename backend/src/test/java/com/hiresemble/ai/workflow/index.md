# AI workflow 테스트 안내

## 디렉터리 목적

canonical registry coverage와 metadata·contribution 불변식을 검증한다.

## 주요 파일 및 하위 디렉터리

- `WorkflowRegistryTest`
- [`progress.md`](progress.md)

## 구성 요소 역할

8개 type, duplicate key, weight와 executable sequence를 고정한다.

## 다른 디렉터리와의 의존 관계

[`../../../../../../main/java/com/hiresemble/ai/workflow/`](../../../../../../main/java/com/hiresemble/ai/workflow/index.md)을 검증한다.

## 변경 시 주의사항

definition 존재를 executable handler 존재로 해석하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 테스트](../index.md)
