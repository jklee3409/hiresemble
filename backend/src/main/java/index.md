# Java 소스 안내

## 디렉터리 목적

`backend/src/main/java/`는 백엔드 운영 Java 소스의 package root다. 현재 최상위 namespace는 [`com/`](com/) 하나다.

## 주요 파일 및 하위 디렉터리

| 경로                                 | 역할                                   |
| ------------------------------------ | -------------------------------------- |
| [`com/`](com/)                       | 역도메인 기반 Java namespace           |
| [`com/hiresemble/`](com/hiresemble/) | Hiresemble 애플리케이션의 기본 package |

## 구성 요소 역할

- 이 계층은 package namespace 경계만 제공하고 업무 구현은 `com.hiresemble` 이하에서 소유한다.
- Spring component scan은 `HiresembleApplication`이 위치한 `com.hiresemble`을 기준으로 동작한다.

## 다른 디렉터리와의 의존 관계

- 런타임 설정과 migration은 형제 [`../resources/`](../resources/)에 있다.
- Java 버전과 compile/test 설정은 [`../../../build.gradle.kts`](../../../build.gradle.kts)가 정의한다.
- package 설계는 [`../../../../docs/agent-rules/backend-development.md`](../../../../docs/agent-rules/backend-development.md)를 따른다.

## 변경 시 주의사항

- 기본 package `com.hiresemble` 바깥에 애플리케이션 component를 두어 component scan에서 누락시키지 않는다.
- `api/application/domain/infrastructure` 안에는 실제 파일이 있는 책임별 package만 만들고 업무 책임 없이 namespace만 깊게 만들지 않는다.
- package 이동 시 import, component scan, JPA entity/repository scan과 테스트 영향을 함께 확인한다.

## 관련 규칙 및 문서

- [운영 소스 안내](../index.md)
- [Spring 백엔드 개발 규칙](../../../../docs/agent-rules/backend-development.md)
- [공통 작업 절차](../../../../docs/agent-rules/workflow.md)
- [Java 소스 진행 상황](progress.md)
