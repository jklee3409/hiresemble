# Dashboard Infrastructure package 안내

## 디렉터리 목적

Dashboard에 필요한 owner-scoped aggregate count·마감과 전역 게시 가이드를 JDBC read query로 제공한다.

## 주요 파일 및 역할

- `DashboardReadStore`: Profile/최종 학력 projection, Document·Job·Run count, 활성 deadline과 게시 가이드 query
- [`progress.md`](progress.md): 영속 조회 구현 상태

## 의존 관계와 주의사항

기존 table의 상태와 soft-delete 계약을 존중한다. 사용자 콘텐츠 query에는 항상 `user_id`를 적용하고 가이드만 명세상 전역 예외로 조회한다.

## 관련 문서

- [상위 Dashboard 영역](../index.md)
- [DB 명세](../../../../../../../../docs/spec/db.md)
- [진행 상황](progress.md)
