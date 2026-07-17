# Java 소스 진행 상황

## 현재 구현 상태

- `com.hiresemble.HiresembleApplication`만 존재하는 최소 Java 구조다.
- 비즈니스 package, 공통 API 오류 package, Controller와 테스트 대상 Java 구현은 아직 없다.

## 완료된 작업

- `com/hiresemble` 기본 package와 Spring Boot 실행 진입점을 구성했다.
- 중간 namespace를 포함한 Java 소스 문서 계층을 생성했다.

## 진행 중인 작업

- 현재 진행 중인 Java 기능 구현은 없다. package 책임의 초기 문서화는 완료됐다.

## 남은 작업

- `auth`, `profile`, `document`, `job`, `coverletter`, `interview`, `agentrun` 등 실제 요구되는 도메인을 기능 구현 시점에 추가한다.
- 공통 오류 구조와 Security 연동은 [`../../../../docs/agent-rules/backend-response-exception.md`](../../../../docs/agent-rules/backend-response-exception.md)에 따라 구현한다.
- 도메인 및 API 테스트용 `src/test/java` 구조를 추가한다.

## 확인된 문제

- 기능 코드가 없어 package 경계와 의존 방향을 컴파일 수준 외에는 검증할 수 없다.

## 기술적 결정 사항

- 기본 package는 `com.hiresemble`로 유지한다.
- 도메인 package는 문서의 예상 목록을 한 번에 만들지 않고 실제 use case와 함께 추가한다.

## 테스트 및 검증 결과

- `Set-Location backend; .\gradlew.bat check`가 성공해 현재 `HiresembleApplication`의 컴파일을 확인했다.
- package 의존 규칙이나 API 계약 테스트는 구현 코드가 없어 미실행이다.

## 마지막 수정 일자

2026-07-17
