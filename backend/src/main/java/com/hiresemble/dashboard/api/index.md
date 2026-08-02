# Dashboard API package 안내

## 디렉터리 목적

`GET /api/v1/dashboard`와 `GET /api/v1/career-guides`의 공개 HTTP·OpenAPI 계약을 소유한다.

## 주요 파일 및 역할

- `DashboardController`: 인증 사용자·`YYYY-MM` 요청을 application service에 전달한다.
- `DashboardDtos`: 프로필, 정확 count, 날짜별 마감과 게시 가이드 응답을 정의한다.
- [`progress.md`](progress.md): API 구현 상태

## 의존 관계와 주의사항

[`../application/`](../application/index.md)만 호출하며 내부 SQL·Entity를 노출하지 않는다. 오류를 성공 envelope로 감싸지 않고 공개 DTO와 실제 HTTP 상태를 유지한다.

## 관련 문서

- [상위 Dashboard 영역](../index.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
- [진행 상황](progress.md)
