# Dashboard 테스트 안내

## 디렉터리 목적

Dashboard와 Career Guide read API의 인증, owner 격리, 정확 집계, 서울 월 경계와 게시 정렬을 PostgreSQL 통합 테스트로 검증한다.

## 주요 파일 및 역할

- `DashboardIntegrationTest.java`: 두 사용자 격리, `CLOSED` 제외, 월 경계·count·프로필·가이드 공개 조건 검증
- [`progress.md`](progress.md): 테스트 구현·실행 이력

## 의존 관계와 주의사항

[`../support/`](../support/index.md)의 격리 PostgreSQL 기반을 사용하며 운영 DB와 외부 Provider를 호출하지 않는다.

## 관련 문서

- [상위 테스트 영역](../index.md)
- [API 명세](../../../../../../../docs/spec/api.md)
- [진행 상황](progress.md)
