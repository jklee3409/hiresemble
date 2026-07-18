# Progress

## Overview

- `functional.md`, `api.md`, `db.md`, `page.md`, `tech_stack.md`의 다섯 기준 명세가 P0 승인 문서 버전 1.1로 동기화되어 있다.
- 기능 명세는 핵심 MVP 여정과 AC-01~AC-13을, 나머지 명세는 각각 HTTP 계약, 목표 데이터 모델, 화면 구조, 기술·품질 제약을 정의한다.
- 명세는 목표 계약이며 실제 비즈니스 기능 구현 완료를 의미하지 않는다. 현재 백엔드는 애플리케이션 부트스트랩과 pgvector 확장 migration만, 프론트엔드는 빈 route table을 포함한 초기 환경만 구성되어 있다.

## [2026-07-18] Session Summary (P0 승인 계약 다섯 기준 명세 동기화)

- What was done:
  - 제품 소유자가 승인한 8개 정책과 D-01–D-18을 기능·API·DB·페이지·기술 명세에 함께 반영하고 `index.md`에 결정·Gate 추적표를 추가했다.
  - 상태·DTO·validation·pagination·owner·idempotency·Agent Run·budget·embedding·route·draft 계약을 하나의 P0 구현 기준선으로 정규화했다.

- Key decisions:
  - 활성 제품 계약의 원천은 다섯 `docs/spec/**` 명세이며 P0 결정 기록은 계약 원천으로 사용하지 않는다.
  - 문서 계약 버전은 1.1이고 P0 계약 기준선만 완료됐다. 비즈니스 코드·Flyway migration·API·UI·설정 구현은 완료되지 않았다.

- Issues encountered:
  - 수동 공고 본문의 201/202 분기, mock terminal 실패 replay, `SOURCE_DELETED` mutation 금지, retry successor cardinality처럼 명세 사이에 숨은 경계가 있어 같은 의미로 재정렬했다.
  - 추적표의 `~` 범위 표기가 Markdown 취소선으로 바뀌어 en dash로 교체했으며 사용자 변경이나 코드 파일은 건드리지 않았다.

- Validation:
  - backend·ai_workflow·frontend 읽기 전용 분석을 통합했고 새 read-only validator가 8개 정책, D 18개, Gate 16개와 다섯 명세 matrix를 `PASS`로 판정했다.
  - Markdown 표 37개·97 endpoint·enum parity·상한·nullability·idempotency 15개·quality allowlist·상대 링크를 검사하고 Prettier와 `git diff --check`를 통과시켰다.
  - 문서 전용 변경이므로 backend/frontend build는 실행하지 않았고 외부 유료 provider도 호출하지 않았다.

- Next steps:
  - P1에서 활성 명세를 기준으로 공통 HTTP·Session·CSRF·오류·idempotency 기반부터 구현한다. V1 migration은 수정하지 않고 새 Flyway 파일은 해당 구현 단계에서 작성·검증한다.

## [2026-07-18] Session Summary (전체 구현 설계를 위한 명세 교차 검증)

- What was done:
  - 기능·DB·API·페이지·기술 명세 전부를 읽고 AC-01~13과 주요 상태·workflow를 교차 검증했다.
  - 명세를 변경하지 않고 파생 결과를 [전체 시스템 설계](../design/system-architecture.md)와 [구현 계획](../design/implementation-plan.md)에 분리했다.

- Key decisions:
  - 현재 다섯 명세를 계속 기준 계약으로 유지하고, 불일치·누락은 사용자 승인 전 확정하거나 migration·공개 DTO로 구현하지 않는다.
  - 구현은 공개 계약·데이터 수명주기·AI 운영 정책을 먼저 결정한 뒤 승인 근거→공고→자기소개서→면접 순으로 진행한다.

- Issues encountered:
  - 공고 상태 축, 품질·version·질문 enum, 사용자 소유 DB 제약, 삭제·provenance, 멱등성·비동기 복구·SSE, 자기소개서 최종화·보관과 면접 lifecycle 등 결정 항목이 확인됐다.
  - 전체 문제, 영향, 권장안과 설계 보류 범위는 전체 시스템 설계의 이슈 목록에 기록했다.

- Validation:
  - backend·AI workflow·frontend 에이전트가 각 관점의 읽기 전용 분석을 `DONE`으로 반환했다.
  - 독립 validator와 루트 정적 재검사가 AC-01~13 추적, 이슈 18개의 필수 형식, 상대 링크와 무수정 명세 범위를 확인했다.
  - 실제 API·DB·UI 구현은 없으므로 구현 test는 실행하지 않았다.

- Next steps:
  - P0 결정 게이트에서 이슈별 계약을 승인한 뒤 영향받는 다섯 명세를 함께 갱신하고 문서 version 정책을 확정한다.

## [2026-07-17] Session Summary (MVP 제품 계약 기준선 작성)

- What was done:
  - 당시 구현 상태:
    - `functional.md`, `api.md`, `db.md`, `page.md`, `tech_stack.md`의 다섯 기준 명세가 문서 버전 1.0으로 존재한다.
    - 기능 명세는 핵심 MVP 여정과 AC-01~AC-13을, 나머지 명세는 각각 HTTP 계약, 목표 데이터 모델, 화면 구조, 기술·품질 제약을 정의한다.
    - 명세는 목표 계약이며 실제 비즈니스 기능 구현 완료를 의미하지 않는다. 현재 백엔드는 애플리케이션 부트스트랩과 pgvector 확장 migration만, 프론트엔드는 빈 route table을 포함한 초기 환경만 구성되어 있다.
  - 완료된 작업:
    - 회원·프로필·문서·공고 분석·자기소개서·면접·Agent Run을 포함한 MVP 기능과 인수 조건을 작성했다.
    - `/api/v1` endpoint, Session Cookie/CSRF, 성공·오류 응답, HTTP 상태와 멱등성 계약을 작성했다.
    - PostgreSQL/pgvector 목표 스키마, 상태값, 관계, 트랜잭션과 데이터 보존 원칙을 작성했다.
    - Vue route·layout·화면·상태 관리·route guard와 세 핵심 E2E 시나리오를 작성했다.
    - 모듈러 모놀리스, 통제형 AI workflow, 보안·비용·테스트·배포 기술 원칙을 작성했다.
    - 작업 목적에 따라 `index.md`, `progress.md`를 생성해 다섯 명세의 책임과 구현 상태 구분 원칙을 문서화했다.
    - 커밋 전 whitespace 검사에서 발견한 `db.md`의 Markdown hard-break 공백 4곳을 문장 내용 변경 없이 정리했다.
  - 당시 진행 중인 작업:
    - 현재 명세 원문을 변경하는 작업은 없다.

- Key decisions:
  - `functional.md`를 비즈니스 요구의 중심으로 두고 API·DB·페이지 명세가 이를 각 구현 경계로 구체화하도록 역할을 분리한다.
  - `tech_stack.md`는 특정 기능의 완료 상태가 아니라 모든 구현에 적용되는 아키텍처·보안·품질 제약을 관리한다.
  - 제품의 목표 계약은 이 디렉터리에서, 현재 구현과 작업 이력은 각 `progress.md`에서 관리한다.
  - 명세와 구현이 다르면 구현을 조용히 변경하지 않고 호환성, migration과 선택지를 먼저 기록한다.

- Issues encountered:
  - 다섯 명세의 존재는 구현 완료를 뜻하지 않는다. 현재 실제 비즈니스 Controller, 도메인 테이블, 화면 route와 E2E 테스트는 구현되지 않았다.
  - DB 명세는 전체 도메인 schema를 정의하지만 현재 Flyway에는 pgvector 확장을 활성화하는 `V1__enable_extensions.sql`만 있다.
  - 페이지 명세는 전체 route와 화면 흐름을 정의하지만 현재 프론트엔드 router의 `routes`는 비어 있다.
  - API 명세의 성공 DTO 직접 반환·실제 HTTP 상태 계약은 레퍼런스 프로젝트의 일괄 envelope·기본 HTTP 200 방식과 다르므로 구조적 패턴만 선택적으로 적용해야 한다.

- Validation:
  - PowerShell 검사로 `api.md`, `db.md`, `functional.md`, `page.md`, `tech_stack.md`의 존재와 제목, 신규 문서의 필수 섹션·수정일 및 모든 상대 링크를 확인했다. 결과: 성공.
  - `corepack pnpm --dir frontend exec prettier --check ...` 최초 실행은 신규 문서의 포맷 차이로 실패했다. `prettier --write` 적용 후 재검사 결과 모두 통과했다.
  - `git diff --cached --check` 기준으로 명세 문서의 trailing whitespace가 없음을 커밋 단위에서 확인했다.
  - API, DB, UI 또는 E2E 구현 검증은 이번 문서화 범위에 포함되지 않았으며 구현 완료로 간주하지 않는다.

- Next steps:
  - 구현 단계마다 API/OpenAPI, Flyway schema, Vue route와 E2E 시나리오가 명세와 일치하는지 검증해야 한다.
  - 공통 응답·예외 처리를 구현할 때 `api.md`의 직접 성공 응답과 실제 HTTP 상태 코드 계약을 테스트로 고정해야 한다.
  - 제품 요구가 변경되면 다섯 명세의 교차 영향과 문서 버전 갱신 기준을 함께 결정해야 한다.
