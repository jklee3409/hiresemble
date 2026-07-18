# Progress

## Overview

- `V1__enable_extensions.sql` 하나만 존재하며 pgvector `vector` extension을 idempotent하게 활성화한다.
- 사용자, 문서, 공고, 자기소개서, 면접과 Agent Run 업무 table은 아직 구현되지 않았다.

## [2026-07-17] Session Summary (pgvector 확장 V1 migration 구성)

- What was done:
  - 당시 구현 상태:
    - `V1__enable_extensions.sql` 하나만 존재하며 pgvector `vector` extension을 idempotent하게 활성화한다.
    - 사용자, 문서, 공고, 자기소개서, 면접과 Agent Run 업무 table은 아직 구현되지 않았다.
  - 완료된 작업:
    - pgvector 기능을 후속 schema에서 사용할 수 있도록 첫 Flyway migration을 추가했다.
    - migration 디렉터리의 목적, 불변 규칙과 현재 상태를 추적하는 문서를 생성했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 migration 작업은 없다. 문서 체계 초기화는 완료됐고 기존 V1 SQL은 변경하지 않았다.

- Key decisions:
  - 기존 V1은 적용 이력이 생길 수 있는 migration으로 간주해 수정하지 않는다.
  - schema 변경은 Hibernate 자동 생성이 아니라 새로운 Flyway 버전 파일로만 수행한다.
  - 로컬 DB는 pgvector 포함 PostgreSQL image를 사용한다.

- Issues encountered:
  - V1은 대상 DB에 extension 생성 권한과 pgvector 설치가 필요하다.
  - 업무 table이 전혀 없어 현재 migration만으로는 제품 데이터를 저장할 수 없다.
  - 이 위치의 추적 Markdown도 기본 Gradle resource 처리에서 classpath에 복사된다.

- Validation:
  - `Set-Location backend; .\gradlew.bat check`가 성공했다.
  - `V1__enable_extensions.sql`이 `CREATE EXTENSION IF NOT EXISTS vector;`만 포함함을 직접 확인했다.
  - 이번 문서 작업에서는 빈 DB와 기존 DB upgrade migration 실행을 새로 수행하지 않았다.

- Next steps:
  - [`../../../../../../docs/spec/db.md`](../../../../../../docs/spec/db.md)의 우선순위에 따라 V2 이상의 업무 schema migration을 설계한다.
  - 새 migration마다 빈 DB 적용, 기존 DB upgrade, JPA validate와 DB constraint를 검증한다.
  - pgvector column에는 embedding model/dimension 추적과 사용자 격리 index 전략을 함께 설계한다.
