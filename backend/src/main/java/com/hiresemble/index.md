# Hiresemble 애플리케이션 package 안내

## 디렉터리 목적

`com.hiresemble`은 Spring Boot component scan의 기준이자 Hiresemble 업무 기능을 도메인별로 구성할 기본 package다. 현재 P1 공통 HTTP·인증, P2 프로필과 P3 Agent Run·AI runtime 영역이 구현되어 있다.

## 주요 파일 및 하위 디렉터리

| 경로                                                       | 역할                                                              |
| ---------------------------------------------------------- | ----------------------------------------------------------------- |
| [`HiresembleApplication.java`](HiresembleApplication.java) | `@SpringBootApplication` 기반 실행 진입점                         |
| [`common/`](common/)                                       | 오류 DTO·예외, Request ID·Security, validation과 idempotency 기반 |
| [`auth/`](auth/)                                           | 가입·로그인·로그아웃·CSRF·현재 사용자와 사용자 영속성             |
| [`profile/`](profile/)                                     | 기본·구조화 프로필, 완료도, owner-scoped CRUD와 직접 근거 동기화  |
| [`agentrun/`](agentrun/)                                   | durable Run·Step, 비용, DB worker, API와 SSE                      |
| [`ai/`](ai/)                                               | 고정 workflow, context·router·prompt·gateway 기반                 |

## 구성 요소 역할

- `HiresembleApplication`은 Spring application context를 부트스트랩한다.
- Controller는 HTTP 변환, Service/Application은 use case와 transaction, Domain은 안정적 상태 값을 담당한다.
- `common`은 P1~P3에서 실제 사용하는 오류·보안·validation·idempotency 책임만 제공한다.
- `profile`은 HTTP 변환, use case transaction, 도메인 불변식, JDBC 영속성을 분리한다.
- `agentrun`은 PostgreSQL 상태 원천과 application port를, `ai`는 repository 비의존 고정 orchestration을 소유한다.

## 다른 디렉터리와의 의존 관계

- 설정과 migration은 [`../../../resources/`](../../../resources/)에서 제공한다.
- API·DB 계약은 [`../../../../../../docs/spec/`](../../../../../../docs/spec/)에 의존한다.
- 오류 응답과 예외 구조는 [`../../../../../../docs/agent-rules/backend-response-exception.md`](../../../../../../docs/agent-rules/backend-response-exception.md)를 따른다.

## 변경 시 주의사항

- `document`, `job`, `coverletter`, `interview` 등 P4 이후 package는 아직 없으며 명세 존재를 구현 완료로 오해하지 않는다.
- API 성공 응답은 명세 DTO를 직접 반환하고, 생성·비동기·본문 없음 상태에 실제 201·202·204를 사용한다.
- Service/Domain에서 HTTP DTO를 만들지 않으며, Security 오류도 ControllerAdvice가 처리한다고 가정하지 않는다.
- Entity나 민감한 예외 원문을 API 또는 로그로 노출하지 않는다.

## 관련 규칙 및 문서

- [Spring 백엔드 개발 규칙](../../../../../../docs/agent-rules/backend-development.md)
- [공통 응답·예외 처리 규칙](../../../../../../docs/agent-rules/backend-response-exception.md)
- [API 명세](../../../../../../docs/spec/api.md)
- [DB 명세](../../../../../../docs/spec/db.md)
- [애플리케이션 package 진행 상황](progress.md)
