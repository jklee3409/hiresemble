# AI workflow package 안내

## 디렉터리 목적

canonical workflow metadata와 실제 실행 contribution·step executor 경계를 정의한다.

## 주요 파일 및 하위 디렉터리

- `WorkflowRegistry`: definition/contribution 검증
- `CanonicalWorkflowDefinitions`: 8개 WorkflowType 고정 definition
- `WorkflowStepExecutor`: prepare·gateway·validation·apply contract
- [`progress.md`](progress.md): registry 상태

## 구성 요소 역할

step 순서, schema, bounded fan-out, tool allowlist, call cap, retry class와 progress weight를 검증한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 definition과 contribution을 소비한다.

## 변경 시 주의사항

duplicate step key와 weight 합 오류를 거부하고 handler가 없는 canonical workflow를 executable로 등록하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [구현 계획](../../../../../../../../docs/design/implementation-plan.md)
