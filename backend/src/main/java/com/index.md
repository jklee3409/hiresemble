# `com` namespace 안내

## 디렉터리 목적

`backend/src/main/java/com/`은 조직·프로젝트 package를 분리하는 역도메인 namespace 중간 계층이다.

## 주요 파일 및 하위 디렉터리

| 경로                         | 역할                                          |
| ---------------------------- | --------------------------------------------- |
| [`hiresemble/`](hiresemble/) | Hiresemble 백엔드의 기본 애플리케이션 package |

## 구성 요소 역할

- `com` 자체는 실행 코드나 공통 기능을 소유하지 않는다.
- 실제 Spring component와 업무 경계는 `hiresemble` 아래에서 관리한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`../index.md`](../index.md)는 Java source root와 resources 관계를 설명한다.
- 하위 [`hiresemble/index.md`](hiresemble/index.md)는 애플리케이션 package의 실제 책임을 설명한다.
- package와 module 규칙은 [`../../../../../docs/agent-rules/backend-development.md`](../../../../../docs/agent-rules/backend-development.md)에 의존한다.

## 변경 시 주의사항

- 이 중간 namespace에 Java 클래스를 직접 두지 않는다.
- 조직 namespace를 변경하면 전체 package/import, component scan과 공개 타입 호환성에 미치는 영향을 먼저 분석한다.

## 관련 규칙 및 문서

- [Java 소스 안내](../index.md)
- [Hiresemble package 안내](hiresemble/index.md)
- [Spring 백엔드 개발 규칙](../../../../../docs/agent-rules/backend-development.md)
- [`com` namespace 진행 상황](progress.md)
