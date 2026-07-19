# AI prompt package 안내

## 디렉터리 목적

prompt version, typed input/output, schema, tool allowlist와 token/call cap metadata를 등록한다.

## 주요 파일 및 하위 디렉터리

- `PromptRegistry`: immutable prompt definition lookup
- [`progress.md`](progress.md): registry 상태

## 구성 요소 역할

workflow type+version+step key로 정확한 prompt contract를 찾는다.

## 다른 디렉터리와의 의존 관계

[`../orchestration/`](../orchestration/index.md)이 실행 전에 조회한다. P3 실제 prompt는 test resources에만 있다.

## 변경 시 주의사항

P4 이후 빈 production prompt를 미리 만들지 않고 전체 prompt 내용을 로그·DB에 저장하지 않는다.

## 관련 규칙 및 문서

- [상위 AI 영역](../index.md)
- [시스템 설계](../../../../../../../../docs/design/system-architecture.md)
