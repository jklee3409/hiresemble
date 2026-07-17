# `com` namespace 진행 상황

## 현재 구현 상태

- `hiresemble` 하위 package 하나만 존재한다.
- 이 namespace 계층에는 Java 클래스나 비즈니스 기능이 직접 존재하지 않는다.

## 완료된 작업

- Java 역도메인 namespace를 `com/hiresemble`로 구성했다.
- 중간 namespace의 책임과 변경 위험을 설명하는 추적 문서를 생성했다.

## 진행 중인 작업

- 현재 진행 중인 구현 작업은 없다. namespace 문서 계층 초기화는 완료됐다.

## 남은 작업

- 별도 조직 package가 실제로 필요해지기 전까지 현재 단일 하위 경계를 유지한다.
- 업무 구현은 모두 `hiresemble` 하위의 적절한 도메인 package에 추가한다.

## 확인된 문제

- 현재 확인된 namespace 충돌은 없다.
- 하위 package에는 실행 진입점 외 비즈니스 구현이 없다.

## 기술적 결정 사항

- 중간 `com` 계층은 namespace 역할만 갖고 코드 소유권을 부여하지 않는다.
- 애플리케이션 기본 package를 변경하지 않는다.

## 테스트 및 검증 결과

- `rg --files backend/src/main/java/com`으로 `hiresemble/HiresembleApplication.java`만 존재함을 확인했다.
- `Set-Location backend; .\gradlew.bat check`가 성공했다.

## 마지막 수정 일자

2026-07-17
