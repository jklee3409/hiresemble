# Cover Letter Infrastructure package 안내

## 디렉터리 목적

P7 자기소개서 PostgreSQL 영속성, owner-scoped 조회·CAS·immutable history와 AI 비용 설정을 소유한다.

## 주요 파일 및 하위 디렉터리

- `CoverLetterStore`: 자기소개서·문항·답변·근거·검증·acknowledgement SQL과 USER_EDITED exact excerpt 일치 provenance 복사
- `CoverLetterAiCostProperties`: generation·verification 비용 예약 설정
- `CoverLetterInfrastructureConfiguration`: 검증된 infrastructure bean 조립
- [`progress.md`](progress.md): infrastructure 구현 상태

## 구성 요소 역할

V8 owner 복합 FK·partial unique·immutable trigger와 일치하는 조건부 SQL로 active/current/fresh 상태를 조회·변경한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)에 저장 경계를 제공하고 [`../../../../resources/db/migration/V8__create_cover_letters_versions_and_verifications.sql`](../../../../resources/db/migration/V8__create_cover_letters_versions_and_verifications.sql)에 의존한다.

## 변경 시 주의사항

모든 ID 조회·mutation에 `user_id`를 포함하고 archived mutation, cross-owner join과 stale current 교체를 SQL 경계에서도 거부한다.

## 관련 규칙 및 문서

- [상위 Cover Letter 영역](../index.md)
- [Infrastructure 규칙](../../../../../../../../docs/agent-rules/infrastructure.md)
- [진행 상황](progress.md)
