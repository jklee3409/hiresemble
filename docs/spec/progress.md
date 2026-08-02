# Progress

## Overview

- `functional.md`, `api.md`, `db.md`, `page.md`, `tech_stack.md`의 다섯 활성 명세가 유지되며 page 명세는 공개 Landing·첫 사용 흐름 계약 1.3으로 갱신됐다.
- 기능 명세는 핵심 MVP 여정과 AC-01~AC-17을, 나머지 명세는 현재 구현 기준선과 P8.5-V–P10-C의 `PLANNED` 계약을 분리해 정의한다.
- 명세는 목표 계약이며 실제 비즈니스 기능 구현 완료를 의미하지 않는다. P0–P8은 완료됐고 P8.5 Chat strict output부터 문서 finalize까지 실제 run으로 검증됐다. terminal classification 보정은 offline 검증됐지만 live 재검증 전인 `IMPLEMENTED_NOT_LIVE_VERIFIED`다.

## [2026-08-02] Session Summary (Dashboard·Career Guide API/DB/Page 계약)

- What was done:
  - 68 paths/92 operations, Dashboard exact projection, V17 guide schema·게시 정책과 커리어 카드·캘린더·modal·focus 페이지 계약을 동기화했다.
- Key decisions:
  - Guide는 전역 게시 콘텐츠 예외이며 Dashboard deadline은 서울 월 경계와 owner scope를 사용한다.
- Issues encountered:
  - 기존 tentative V17 이후 migration 번호를 latest V17 기준으로 한 단계 이동했다.
- Validation:
  - OpenAPI exact contract, migration upgrade, Backend/Frontend 표준 검증 통과.
- Next steps:
  - Backoffice guide mutation은 별도 계약 승인 시 추가한다.

## [2026-08-02] Session Summary (공개 Landing·첫 사용 페이지 계약)

- What was done:
  - `page.md`에 anonymous/authenticated `/`, Landing·PublicLayout·onboarding·guide 역할과 Dashboard 3항목 체크리스트를 동기화했다.
  - 현재 owner-scoped 목록 조합과 P10-A 목표 `GET /dashboard` 계약을 명시적으로 구분했다.
- Key decisions:
  - API·DB·AI workflow 공개 계약은 변경하지 않고 페이지 route·표시 정책만 갱신했다.
- Issues encountered:
  - None.
- Validation:
  - Router·page 구현, 65 files/258 tests와 Playwright Landing 6/6을 명세와 대조했다.
- Next steps:
  - P10-A에서 canonical Dashboard API를 구현할 때 현재 조합 query를 교체한다.

## [2026-08-02] Session Summary (자동 분석·제품 UI 계약 동기화)

- What was done:
  - functional·page·api·db·tech stack에 등록→추출→BALANCED 분석, projection 상태·실패·재조정, document view, 상단/mobile navigation과 `/guide`를 반영했다.
- Key decisions:
  - 분석 endpoint와 `AiQualityMode` enum은 유지하고 최초 제품 정책만 BALANCED로 고정한다.
- Issues encountered:
  - 기존의 “자동 연쇄하지 않는다” 계약을 durable backend orchestration 계약으로 교체했다.
- Validation:
  - V16 schema, Spring OpenAPI 63 paths/84 operations와 Frontend type·route 대조 완료.
- Next steps:
  - None.

## [2026-08-01] Session Summary (이미지형 공고 v3 후속 계약)

- What was done:
  - functional/page/tech_stack에 imageRef·adapter·retry·WebP·aggregate 기준을 동기화했다.
- Key decisions:
  - 공개 API/DB schema와 migration은 변경하지 않고 정적 WebP만 지원한다.
- Issues encountered:
  - None.

## [2026-08-01] Session Summary (이미지형 공고 자동 추출 계약)

- Validation:
  - 구현·테스트·명세 대조와 전체 표준 검증 통과.
- Next steps:
  - None.

- What was done:
  - 기능·페이지·기술 명세에 charset, DOM 품질, 자동 image branch, 최종 manual fallback과 v2 step을 반영했다.
- Key decisions:
  - uploaded image PDF OCR 제외는 유지하고 공개 공고 페이지 내부 JPEG·PNG image text만 지원 범위로 구분한다.
- Issues encountered:
  - API/DB 공개 계약 변경이 없어 `api.md`, `db.md`와 migration은 수정하지 않았다.
- Validation:
  - 구현·테스트·명세 상호 검토와 Markdown diff check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (자료 검토·직접 대외활동 추가 계약)

- What was done:
  - 기능·API·DB·페이지 명세에 문서 소재 batch review/re-review, 원본 파일명, 직접 대외활동 CRUD·소재 정책, 알림 접근성, AI 사용량 표현을 반영했다.
- Key decisions:
  - V15는 기존 단계가 아닌 추가 UX/데이터 구조 보정으로 기록하고 이후 tentative migration을 V16~V19로 이동했다.
- Issues encountered:
  - 월간 누적 한도 데이터는 현재 API에 없어 구현된 작업 단위 비율과 향후 집계 계약을 분리했다.
- Validation:
  - OpenAPI 66 paths/90 operations와 V15 구현, Frontend route·문구를 명세와 대조했다.
- Next steps:
  - P8.7에서 월간 사용량 API와 화면 계약을 확정한다.

## [2026-08-01] Session Summary (candidate filtering과 failed scope 의미 분리)

- What was done:
  - 문서 candidate 일부·전체 rejection 성공 계약, safe reason count와 실제 independent scope 전용 `failedScopeKeys` 계약을 명세했다.
- Key decisions:
  - public Evidence DTO·JSONB와 기존 migration은 변경하지 않는다.
- Issues encountered:
  - 과거 rejection의 개별 reason은 저장되지 않아 미확정이다.
- Validation:
  - 구현·통합 테스트와 기능·DB·기술 명세를 대조했다.
- Next steps:
  - bounded live 문서 실행에서 terminal success를 확인한다.

## [2026-08-01] Session Summary (output 소유권·phase retry 활성 계약)

- What was done:
  - 기능·기술·DB 명세에 문서 Provider output v2, trusted local ref mapper, phase safe code와 reason별 retry를 반영했다.
- Key decisions:
  - `EvidenceDto.metadata`, profile evidence JSONB와 migration은 변경하지 않는다.
- Issues encountered:
  - 과거 단일 safe code로 exact invalid field와 truncation은 확정할 수 없다.
- Validation:
  - runtime policy/schema/test와 명세를 대조하고 전체 Backend check를 통과했다.
- Next steps:
  - live 성공 뒤에만 capability/vertical 상태를 갱신한다.

## [2026-08-01] Session Summary (strict Structured Output 기술 계약)

- What was done:
  - Provider output record, runtime schema 중앙 검증, nullable union, schema fingerprint와 domain/public 분리 원칙을 기술 명세에 반영했다.
- Key decisions:
  - 공개 API·DB·Frontend 계약과 migration은 변경하지 않는다.
- Issues encountered:
  - 당시 raw Provider 오류가 없어 live 원인 직접 확정은 불가능하다.
- Validation:
  - 코드·schema 전수 test와 기술 명세를 대조했다.
- Next steps:
  - live Chat/문서 검증 뒤 상태만 갱신한다.

## [2026-08-01] Session Summary (V14 embedding 정책 명세 반영)

- What was done:
  - DB latest를 V14로 갱신하고 embedding provider key canonicalization과 이후 tentative migration 번호를 반영했다.
- Key decisions:
  - version 1은 history로 보존하고 version 2 `openai`를 활성 정책으로 사용한다.
- Issues encountered:
  - None.
- Validation:
  - V1–V14 불변 경계와 P8.6–P9 migration 표를 대조했다.
- Next steps:
  - P8.6 시작 시 latest migration을 다시 확인한다.

## [2026-08-01] Session Summary (운영 기반 활성 명세 1.2 동기화)

- What was done:
  - 기능·DB·API·페이지·기술 명세에 기능 한도, usage accounting, 공통 실패 UX, ADMIN Backoffice와 P9/P10 소비 계약을 반영했다.
  - AC-14~AC-17과 구현 상태·phase·선행 API가 명시된 미래 route/API/table을 추가했다.
- Key decisions:
  - 내부 Provider 원가는 사용자 청구액으로 노출하지 않고, 과금 가능 usage는 0원 정책 snapshot으로 보존한다.
  - 현재 OpenAPI 기준선은 63 paths/84 operations로 유지하며 모든 미래 endpoint와 migration은 `PLANNED` 또는 tentative로 표시한다.
- Issues encountered:
  - 현재 router에는 settings, backoffice, mock-interviews route가 없어 모두 미래 계약으로만 기록했다.
- Validation:
  - 명세 간 AC·상태·phase·API/page 링크와 Markdown 형식을 검사했다.
- Next steps:
  - P8.6 구현 시 최신 migration과 실제 API 계약을 다시 확인한 뒤 계획을 구현 상태로 전환한다.

## [2026-08-01] Session Summary (외부 Provider 기술·DB 계약)

- What was done:
  - local 실제 Provider, offline/test 격리, V13 price version과 다중 usage ledger를 기술·DB 명세에 반영했다.
- Key decisions:
  - Spring AI VectorStore는 활성화하지 않고 기존 owner-scoped JDBC/pgvector 경계를 유지한다.
- Issues encountered:
  - None.
- Validation:
  - OpenAPI 비변경, V1~V12 불변과 profile 설정을 코드와 대조했다.
- Next steps:
  - P9 명세 구현은 별도 요청에서 수행한다.

## [2026-07-31] Session Summary (닉네임 Modal·최종 학력 계산 계약)

- What was done:
  - 기능·API·DB·페이지 명세에 `EducationLevel`, read-only 최종 학력 flag, 단계별 자동 판정과 legacy backfill을 반영했다.
  - 기본 정보 닉네임 제거·상단 Modal, 승인·거절 전용 안내, AI 작업 간소화 문구와 관심 공고 active hover를 동기화했다.
- Key decisions:
  - `isPrimary`는 write 계약에서 제거하고 서버 계산 projection으로만 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Backend·Frontend 전체 check와 개발 DB V11 결과를 명세와 대조했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (학력 근거 제외·AI 작업 내역 삭제 명세)

- What was done:
  - `functional.md`, `api.md`, `db.md`, `page.md`에 대외활동 명칭·UI, 학력 근거 생성/추출 금지, 문서 AI 근거 전용 승인·거절·confidence 의미와 Agent Run history delete를 동기화했다.
- Key decisions:
  - provenance 관계가 깊은 기존 학력 근거와 Agent Run은 ID·audit을 보존한 tombstone/soft delete로 처리하고 owner-visible API에서 제외한다.
- Issues encountered:
  - None.
- Validation:
  - Backend 전체 385 tests, Frontend 전체 215 tests와 개발 DB V9~V10 결과를 계약과 대조했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (프로필 닉네임·내비게이션 화면 계약)

- What was done:
  - `/profile/basic` Form과 API 목록에 닉네임 및 `PATCH /account/display-name`을 연결했다.
  - Desktop 프로필 내비게이션은 부가 설명 없이 항목명만 표시하고 첫 진입 화면에 전체 항목을 노출하도록 명시했다.
- Key decisions:
  - `/settings/account`의 목표 계약은 유지하면서 현재 구현된 프로필 기본 정보에서도 같은 account endpoint를 재사용한다.
- Issues encountered:
  - 기존 API 명세에는 닉네임 endpoint가 있었지만 profile page 명세의 Form·API 연결이 누락되어 있었다.
- Validation:
  - `page.md`와 `api.md`의 endpoint·field 명칭 정합성을 검색하고 Frontend 전체 check를 통과했다.
- Next steps:
  - 비밀번호 변경·회원 탈퇴는 별도 요청과 구현 단계 전까지 목표 계약으로만 유지한다.

## [2026-07-28] Session Summary (닉네임·졸업 예정/완료 사용자 의미 보정)

- What was done:
  - Profile 기능 필드를 졸업(예정)일로 표현해 이미 졸업한 사용자도 포함하도록 의미를 보정했다.
  - Signup·Account 페이지 구성의 사용자 용어를 닉네임으로 변경했다.
- Key decisions:
  - 공개 `displayName`, `expectedGraduationDate` DTO와 DB column은 호환성을 위해 유지했다.
- Issues encountered:
  - None.
- Validation:
  - Frontend check의 Markdown format과 코드 사용자 노출 용어 검색이 통과했다.
- Next steps:
  - API field rename이 필요해지는 별도 major 계약 변경 전까지 내부 이름은 유지한다.

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
