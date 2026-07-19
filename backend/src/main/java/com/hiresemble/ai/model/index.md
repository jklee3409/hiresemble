# AI model package 안내

## 디렉터리 목적

공개 quality intent와 내부 ModelTier, provider policy routing을 분리한다.

## 주요 파일 및 하위 디렉터리

- `ModelRouter`: routing request·policy·route 계약
- `PolicyModelRouter`: 품질 allowlist와 제한된 승격
- [`progress.md`](progress.md): router 검증 상태

## 구성 요소 역할

immutable policy snapshot을 바탕으로 tier를 고르고 provider/model 식별자는 공개 API 밖에 유지한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 step별 route를 요청한다.

## 변경 시 주의사항

HIGH_QUALITY는 preference·명시 요청·reserve·workflow allowlist가 모두 필요하며 자동 승격하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [기능 명세](../../../../../../../../docs/spec/functional.md)
