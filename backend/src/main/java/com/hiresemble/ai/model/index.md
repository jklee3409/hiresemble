# AI model package 안내

## 디렉터리 목적

workflow별 공개 모델 선택과 내부 ModelTier, provider policy routing을 분리한다.

## 주요 파일 및 하위 디렉터리

- `ModelRouter`: routing request·policy·route 계약
- `OpenAiChatModels`: 자기소개서·Resume·Portfolio workflow별 정확한 OpenAI model ID·표시 정보·추천 모델의 단일 catalog
- `PolicyModelRouter`: 자기소개서·Career Artifact 선택 모델의 exact route와 legacy workflow 품질 allowlist·제한된 승격
- [`progress.md`](progress.md): router 검증 상태

## 구성 요소 역할

자기소개서 v4와 두 Career Artifact workflow는 immutable Run input의 `model`을 중앙 catalog로 검증한 뒤 모든 Chat step을 동일한 provider model ID로 route한다. 다른 workflow와 자기소개서 v1~v3 재개는 기존 immutable policy snapshot 기반 tier routing을 유지한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 step별 route를 요청한다.

## 변경 시 주의사항

선택 가능 model ID를 Controller나 화면에 중복 선언하지 않는다. embedding step에는 chat model 선택을 전파하지 않으며 legacy HIGH_QUALITY 승격 조건도 그대로 유지한다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [기능 명세](../../../../../../../../docs/spec/functional.md)
