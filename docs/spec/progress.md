# `docs/spec` 진행 상황

## 현재 구현 상태

- `functional.md`, `api.md`, `db.md`, `page.md`, `tech_stack.md`의 다섯 기준 명세가 문서 버전 1.0으로 존재한다.
- 기능 명세는 핵심 MVP 여정과 AC-01~AC-13을, 나머지 명세는 각각 HTTP 계약, 목표 데이터 모델, 화면 구조, 기술·품질 제약을 정의한다.
- 명세는 목표 계약이며 실제 비즈니스 기능 구현 완료를 의미하지 않는다. 현재 백엔드는 애플리케이션 부트스트랩과 pgvector 확장 migration만, 프론트엔드는 빈 route table을 포함한 초기 환경만 구성되어 있다.

## 완료된 작업

- 회원·프로필·문서·공고 분석·자기소개서·면접·Agent Run을 포함한 MVP 기능과 인수 조건을 작성했다.
- `/api/v1` endpoint, Session Cookie/CSRF, 성공·오류 응답, HTTP 상태와 멱등성 계약을 작성했다.
- PostgreSQL/pgvector 목표 스키마, 상태값, 관계, 트랜잭션과 데이터 보존 원칙을 작성했다.
- Vue route·layout·화면·상태 관리·route guard와 세 핵심 E2E 시나리오를 작성했다.
- 모듈러 모놀리스, 통제형 AI workflow, 보안·비용·테스트·배포 기술 원칙을 작성했다.
- 작업 목적에 따라 `index.md`, `progress.md`를 생성해 다섯 명세의 책임과 구현 상태 구분 원칙을 문서화했다.
- 커밋 전 whitespace 검사에서 발견한 `db.md`의 Markdown hard-break 공백 4곳을 문장 내용 변경 없이 정리했다.

## 진행 중인 작업

- 현재 명세 원문을 변경하는 작업은 없다.

## 남은 작업

- 구현 단계마다 API/OpenAPI, Flyway schema, Vue route와 E2E 시나리오가 명세와 일치하는지 검증해야 한다.
- 공통 응답·예외 처리를 구현할 때 `api.md`의 직접 성공 응답과 실제 HTTP 상태 코드 계약을 테스트로 고정해야 한다.
- 제품 요구가 변경되면 다섯 명세의 교차 영향과 문서 버전 갱신 기준을 함께 결정해야 한다.

## 확인된 문제

- 다섯 명세의 존재는 구현 완료를 뜻하지 않는다. 현재 실제 비즈니스 Controller, 도메인 테이블, 화면 route와 E2E 테스트는 구현되지 않았다.
- DB 명세는 전체 도메인 schema를 정의하지만 현재 Flyway에는 pgvector 확장을 활성화하는 `V1__enable_extensions.sql`만 있다.
- 페이지 명세는 전체 route와 화면 흐름을 정의하지만 현재 프론트엔드 router의 `routes`는 비어 있다.
- API 명세의 성공 DTO 직접 반환·실제 HTTP 상태 계약은 레퍼런스 프로젝트의 일괄 envelope·기본 HTTP 200 방식과 다르므로 구조적 패턴만 선택적으로 적용해야 한다.

## 기술적 결정 사항

- `functional.md`를 비즈니스 요구의 중심으로 두고 API·DB·페이지 명세가 이를 각 구현 경계로 구체화하도록 역할을 분리한다.
- `tech_stack.md`는 특정 기능의 완료 상태가 아니라 모든 구현에 적용되는 아키텍처·보안·품질 제약을 관리한다.
- 제품의 목표 계약은 이 디렉터리에서, 현재 구현과 작업 이력은 각 `progress.md`에서 관리한다.
- 명세와 구현이 다르면 구현을 조용히 변경하지 않고 호환성, migration과 선택지를 먼저 기록한다.

## 테스트 및 검증 결과

- PowerShell 검사로 `api.md`, `db.md`, `functional.md`, `page.md`, `tech_stack.md`의 존재와 제목, 신규 문서의 필수 섹션·수정일 및 모든 상대 링크를 확인했다. 결과: 성공.
- `corepack pnpm --dir frontend exec prettier --check ...` 최초 실행은 신규 문서의 포맷 차이로 실패했다. `prettier --write` 적용 후 재검사 결과 모두 통과했다.
- `git diff --cached --check` 기준으로 명세 문서의 trailing whitespace가 없음을 커밋 단위에서 확인했다.
- API, DB, UI 또는 E2E 구현 검증은 이번 문서화 범위에 포함되지 않았으며 구현 완료로 간주하지 않는다.

## 마지막 수정 일자

2026-07-17
