# Hiresemble 애플리케이션 package 안내

## 디렉터리 목적

`com.hiresemble`은 Spring Boot component scan의 기준이자 향후 Hiresemble 업무 기능을 도메인별로 구성할 기본 package다.

## 주요 파일 및 하위 디렉터리

| 경로                                                       | 역할                                                                                                                       |
| ---------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| [`HiresembleApplication.java`](HiresembleApplication.java) | `@SpringBootApplication` 기반 실행 진입점                                                                                  |
| `common/`                                                  | 향후 두 개 이상의 도메인이 공유하는 오류·보안·보조 기능을 둘 후보이며 현재 없음                                            |
| 도메인 package                                             | 향후 `auth`, `profile`, `document`, `job`, `coverletter`, `interview`, `agentrun` 등을 실제 기능 단위로 추가하며 현재 없음 |

## 구성 요소 역할

- `HiresembleApplication`은 Spring application context를 부트스트랩한다.
- 향후 Controller는 HTTP 변환, Service/Application은 use case와 transaction, Domain은 불변식과 상태 전이를 담당한다.
- 공통 오류 구조가 필요해지면 `common/api`, `common/exception`, `common/security` 경계를 기준으로 하되 실제 사용처와 함께 생성한다.

## 다른 디렉터리와의 의존 관계

- 설정과 migration은 [`../../../resources/`](../../../resources/)에서 제공한다.
- API·DB 계약은 [`../../../../../../docs/spec/`](../../../../../../docs/spec/)에 의존한다.
- 오류 응답과 예외 구조는 [`../../../../../../docs/agent-rules/backend-response-exception.md`](../../../../../../docs/agent-rules/backend-response-exception.md)를 따른다.

## 변경 시 주의사항

- 현재 존재하지 않는 예상 package를 구현 완료로 오해하지 않는다.
- API 성공 응답은 명세 DTO를 직접 반환하고, 생성·비동기·본문 없음 상태에 실제 201·202·204를 사용한다.
- Service/Domain에서 HTTP DTO를 만들지 않으며, Security 오류도 ControllerAdvice가 처리한다고 가정하지 않는다.
- Entity나 민감한 예외 원문을 API 또는 로그로 노출하지 않는다.

## 관련 규칙 및 문서

- [Spring 백엔드 개발 규칙](../../../../../../docs/agent-rules/backend-development.md)
- [공통 응답·예외 처리 규칙](../../../../../../docs/agent-rules/backend-response-exception.md)
- [API 명세](../../../../../../docs/spec/api.md)
- [DB 명세](../../../../../../docs/spec/db.md)
- [애플리케이션 package 진행 상황](progress.md)
