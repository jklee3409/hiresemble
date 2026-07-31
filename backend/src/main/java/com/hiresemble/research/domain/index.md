# Research domain 안내

## 디렉터리 목적

P8 조사의 품질, 상태, topic, source type과 coverage canonical enum을 소유한다.

## 주요 파일 및 하위 디렉터리

- `ResearchQuality`, `ResearchRunStatus`
- `ResearchTopic`, `ResearchSourceType`, `SourceCoverage`
- [`progress.md`](progress.md): domain 상태

## 구성 요소 역할

DB·API·workflow가 공유하는 조사 분류와 terminal 상태 vocabulary를 제공한다.

## 다른 디렉터리와의 의존 관계

활성 계약은 [`../../../../../../../../docs/spec/api.md`](../../../../../../../../docs/spec/api.md)와 [`../../../../../../../../docs/spec/db.md`](../../../../../../../../docs/spec/db.md)다.

## 변경 시 주의사항

명세 승인 없이 enum을 추가·삭제하거나 `LIMITED|NONE`을 실패 상태로 바꾸지 않는다.

## 관련 규칙 및 문서

- [상위 Research 영역](../index.md)
- [진행 상황](progress.md)
