# AI context package 안내

## 디렉터리 목적

원문 대신 provenance reference와 hash만 담는 workflow context snapshot 계약을 정의한다.

## 주요 파일 및 하위 디렉터리

- `ContextBuilder`: resource/version/hash, upstream refs, truncation·verification·policy projection
- [`progress.md`](progress.md): 계약 상태

## 구성 요소 역할

workflow 실행 시점의 안전한 reference snapshot을 메모리에서 구성한다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 snapshot을 소비하며 실제 profile/document query port는 후속 workflow가 제공한다.

## 변경 시 주의사항

사용자 본문·문서 원문·전체 prompt를 durable snapshot이나 로그에 넣지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [시스템 설계](../../../../../../../../docs/design/system-architecture.md)
