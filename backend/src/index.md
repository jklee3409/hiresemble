# 백엔드 소스 루트 안내

## 디렉터리 목적

`backend/src/`는 Gradle source set을 구성하는 프로젝트 소스의 상위 경계다. 현재는 운영 애플리케이션용 [`main/`](main/)만 존재한다.

## 주요 파일 및 하위 디렉터리

| 경로             | 역할                                                              |
| ---------------- | ----------------------------------------------------------------- |
| [`main/`](main/) | 배포 대상 Java 소스와 classpath 리소스                            |
| `test/`          | 향후 단위·통합 테스트를 둘 표준 source set이며 현재 존재하지 않음 |

## 구성 요소 역할

- `main`은 애플리케이션 실행과 운영 설정의 실제 구현을 관리한다.
- 향후 `test`는 운영 코드와 분리된 JUnit, MockMvc, Testcontainers 테스트를 관리한다.

## 다른 디렉터리와의 의존 관계

- source set과 의존성은 상위 [`../build.gradle.kts`](../build.gradle.kts)가 정의한다.
- Java와 resources의 결합 관계는 [`main/index.md`](main/index.md)에서 설명한다.
- 제품 계약은 [`../../docs/spec/`](../../docs/spec/)에서 관리한다.

## 변경 시 주의사항

- 운영 코드와 테스트 코드를 동일 source set에 섞지 않는다.
- `build/`나 generated source를 `src/` 아래의 수동 관리 소스로 복사하지 않는다.
- 새 source set을 추가하면 Gradle 설정, CI와 이 문서의 책임을 함께 갱신한다.

## 관련 규칙 및 문서

- [백엔드 모듈 안내](../index.md)
- [Spring 백엔드 개발 규칙](../../docs/agent-rules/backend-development.md)
- [문서 추적 규칙](../../docs/agent-rules/documentation-tracking.md)
- [소스 루트 진행 상황](progress.md)
