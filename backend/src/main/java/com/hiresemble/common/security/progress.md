# Progress

## Overview

Request ID, Spring Security Session·CSRF 정책과 Security 오류 JSON 작성을 관리한다. 현재 P1 구현과 검증 상태만 기록한다.

## [2026-07-19] Session Summary (Swagger 문서 책임을 공통 API 설정으로 분리)

- What was done:
  - `SecurityConfiguration`에 있던 Swagger security scheme annotation을 공통 OpenAPI 설정으로 이동했다.

- Key decisions:
  - runtime SecurityFilterChain의 Swagger/OpenAPI 익명 허용과 Session·CSRF 동작은 그대로 유지한다.

- Issues encountered:
  - None

- Validation:
  - Swagger UI 익명 접근과 기존 인증·CSRF 회귀를 포함한 Backend check 33개 테스트가 통과했다.

- Next steps:
  - 운영 Swagger 노출 제어가 필요하면 runtime Security와 springdoc enabled 설정을 같은 배포 정책으로 검토한다.

## [2026-07-19] Session Summary (Request ID와 Session Security 기반 구현)

- What was done:
  - 서버 Request ID, BCrypt cost 12, transaction 참여 JDBC Session, CSRF와 운영 Cookie override를 구성했다.

- Key decisions:
  - csrf/signup/login과 OpenAPI·health만 익명 허용하며 나머지 요청은 Session 인증을 요구한다.
  - Spring Session의 named transaction operations는 JPA manager와 `REQUIRED` propagation을 사용한다.

- Issues encountered:
  - Spring Security 기본 XOR token과 API가 반환한 raw Session token 불일치를 명시적 request handler로 해소했다.
  - Spring Session 4.1 기본 `REQUIRES_NEW`가 signup 원자성을 끊어 `IMMEDIATE` flush와 `REQUIRED` extension으로 보정했다.

- Validation:
  - 익명 CSRF, mutation 403, Session rotation·logout, Security 오류 parity와 MDC log test가 통과했다.
  - Session SQL 실패와 transaction commit 실패에서도 user/profile과 인증 principal이 함께 rollback됨을 검증했다.

- Next steps:
  - 운영에서는 Secure=true와 Reverse Proxy 동일 Site 구성을 환경 설정으로 적용한다.
