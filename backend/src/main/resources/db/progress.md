# DB 리소스 진행 상황

## 현재 구현 상태

- Flyway 기본 경로인 `migration` 하위 디렉터리만 존재한다.
- 업무 schema나 별도 seed/fixture 리소스는 없다.

## 완료된 작업

- PostgreSQL schema 변경 파일을 `db/migration` 표준 classpath 위치에 배치했다.
- DB 리소스와 migration 계층의 책임·상태 문서를 생성했다.

## 진행 중인 작업

- 현재 진행 중인 SQL migration 작업은 없다. DB 리소스 문서 체계 초기화는 완료됐다.

## 남은 작업

- 도메인 구현과 함께 DB 명세를 검토하고 새 버전 migration을 순차적으로 추가한다.
- seed나 테스트 전용 데이터가 필요하면 운영 migration과 분리된 실행 정책을 먼저 정한다.

## 확인된 문제

- 현재 업무 table schema가 없어 도메인 데이터는 저장할 수 없다.
- 이 계층의 Markdown도 resource 처리 결과에 포함되는 문제는 상위 [`../progress.md`](../progress.md)에 기록되어 있다.

## 기술적 결정 사항

- Flyway 기본 위치를 유지하고 schema 변경을 애플리케이션 기동과 일관되게 관리한다.
- 문서만을 위해 별도 DB 리소스 유형을 만들지 않는다.

## 테스트 및 검증 결과

- `Set-Location backend; .\gradlew.bat check`가 성공했다.
- `rg --files backend/src/main/resources/db`로 migration SQL과 두 계층의 추적 문서만 존재함을 확인했다.

## 마지막 수정 일자

2026-07-17
