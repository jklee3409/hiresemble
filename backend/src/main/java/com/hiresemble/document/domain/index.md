# Document domain 안내

## 디렉터리 목적

문서 유형, parse 상태, evidence extraction 상태, 안전 오류와 snapshot 불변식을 정의한다.

## 주요 파일 및 하위 디렉터리

- `Document`, `DocumentSnapshot`: aggregate 상태와 optimistic version
- document/parse/evidence/outbox enum
- [`progress.md`](progress.md): domain 구현 이력

## 구성 요소 역할

`UPLOADED→PARSING→PARSED|NEEDS_MANUAL_TEXT|FAILED`와 독립 evidence 상태 축을 유지한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)이 유일한 mutation 조정자이며 DB CHECK는 V5와 parity를 유지한다.

## 변경 시 주의사항

parse 성공을 embedding·evidence 성공과 합치거나 삭제 문서를 active 상태로 되돌리지 않는다.

## 관련 규칙 및 문서

- [Document 영역](../index.md)
- [DB 명세](../../../../../../../../docs/spec/db.md)
- [기능 명세](../../../../../../../../docs/spec/functional.md)
