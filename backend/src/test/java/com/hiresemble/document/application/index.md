# Document application 테스트 안내

## 디렉터리 목적

normalization·masking·chunking과 Object deletion outbox 상태·retry를 검증한다.

## 주요 파일 및 하위 디렉터리

- [service/](service/index.md): use case·transaction 조정
- [progress.md](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

raw/masked parity와 outbox 경쟁·복구 불변식을 고정한다.

## 다른 디렉터리와의 의존 관계

[`../../../../../../main/java/com/hiresemble/document/application/`](../../../../../../main/java/com/hiresemble/document/application/index.md)을 검증한다.

## 변경 시 주의사항

원문·storage key·provider exception을 assertion failure나 로그에 노출하지 않는다.

## 관련 규칙 및 문서

- [Document 테스트](../index.md)
- [진행 상황](progress.md)
