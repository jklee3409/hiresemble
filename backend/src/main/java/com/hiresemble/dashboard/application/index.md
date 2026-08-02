# Dashboard Application package 안내

## 디렉터리 목적

Dashboard 준비도·정확 count·서울 월 경계와 날짜별 마감 grouping을 조합한다.

## 주요 파일 및 역할

- `DashboardQueryService`: read transaction과 서울 월 경계, 기존 Profile 완료도 정책을 적용한다.
- `DashboardModels`: API와 JDBC 사이의 typed snapshot을 정의한다.
- [`progress.md`](progress.md): application 구현 상태

## 의존 관계와 주의사항

[`../infrastructure/`](../infrastructure/index.md)의 read store를 사용한다. 날짜는 `Clock`과 `Asia/Seoul`을 명시하고 기존 업무 상태 전이를 변경하지 않는다.

## 관련 문서

- [상위 Dashboard 영역](../index.md)
- [진행 상황](progress.md)
