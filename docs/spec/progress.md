# Progress

## Overview

- `functional.md`, `api.md`, `db.md`, `page.md`, `tech_stack.md`의 다섯 활성 명세가 유지되며 page 명세는 공개 Landing·첫 사용 흐름 계약 1.3으로 갱신됐다.
- 기능 명세는 핵심 MVP 여정과 AC-01~AC-17을, 나머지 명세는 현재 구현 기준선과 P8.5-V–P10-C의 `PLANNED` 계약을 분리해 정의한다.
- 명세는 목표 계약이며 실제 비즈니스 기능 구현 완료를 의미하지 않는다. P0–P8은 완료됐고 P8.5 Chat strict output부터 문서 finalize까지 실제 run으로 검증됐다. terminal classification 보정은 offline 검증됐지만 live 재검증 전인 `IMPLEMENTED_NOT_LIVE_VERIFIED`다.

## [2026-08-05] Session Summary (공고 분석 디자인 가이드 모바일 계약 반영)

- What was done:
  - `page.md`의 결과 surface, 공고 제목 크기, 데스크톱 2중 ring과 모바일 104px 단일 ring·meta·CTA 우선순위를 새 디자인 가이드에 맞췄다.
- Key decisions:
  - 모바일에서는 지원 가능성을 결정 문장에, 커버리지를 분석 시각과 같은 meta 행에 흡수하고 desktop 전용 요약 tile을 숨긴다.
- Issues encountered:
  - 기존 명세의 모바일 세 metric row 계약이 새 디자인 가이드의 요약 tile 삭제 지시와 충돌해 사용자 승인으로 새 가이드를 우선했다.
- Validation:
  - Vue 구현과 component test를 대조했고 Node 24 Frontend check 67 files/282 tests·production build, Job Analysis와 visual fixture Chromium 2/2가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 compact decision flow 계약)

- What was done:
  - `page.md`에 공고 제목 두 줄 clamp, OUTDATED bordered disclosure, 단일 판단 surface와 이후 flat section, desktop/mobile metric·CTA 우선순위를 반영했다.
- Key decisions:
  - API·DB·점수 계산은 변경하지 않고 화면 정보 계층과 반응형 표시 계약만 갱신한다.
- Issues encountered:
  - None.
- Validation:
  - Vue 구현과 component/E2E 계약을 대조했고 Frontend 전체 check와 1440·390px Chromium 회귀가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Landing·공고 분석 interaction 계약 보강)

- What was done:
  - `page.md`에 Landing의 decorative motion과 reduced-motion 대체, criterion 5개 pagination·filter reset, disclosure 44px target·open indicator·keyboard focus 계약을 추가했다.
- Key decisions:
  - 390px에서도 적합도를 먼저 보이는 2열 판단 요약을 유지하고 API·DB·점수 계산 계약은 변경하지 않는다.
- Issues encountered:
  - 인앱 browser 연결 부재는 Playwright CLI 실제 Chromium fallback으로 보완했다.
- Validation:
  - Vue 구현과 집중 Vitest 20건, 실제 Chromium 1440·390px·pagination·2열 geometry, Frontend 전체 67 files/282 tests·build에 명세를 대조했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 제품 화면 위계 계약)

- What was done:
  - `page.md`의 Analysis Tab을 단일 report surface, 결과 유무별 primary action, compact row와 desktop/mobile 정보 밀도 계약으로 보강했다.
- Key decisions:
  - `AI 핵심 요약`은 사용자 관점의 `핵심 요약`으로 바꾸고, 강점·보완점은 넓은 의미 색상 배경과 번호 원형 없이 구분선 목록으로 표시한다.
- Issues encountered:
  - None.
- Validation:
  - Vue 구조·component test와 명세를 대조했고 Frontend 전체 check가 통과했다.
- Next steps:
  - 변경 후 Chromium visual 확인 결과가 나오면 반응형 밀도 계약과 다시 대조한다.

## [2026-08-04] Session Summary (공고 분석 결과 UI 표시 계약)

- What was done:
  - `page.md`에 5점 단위 표시 반올림, AI 핵심 요약·접힌 원문 상세, 상태 filter와 사용자 친화적 결과 기록 계약을 추가했다.
- Key decisions:
  - API decimal과 분석 version은 보존하되 화면에서는 정수 점수와 현재 결과·분석 시각을 우선한다.
- Issues encountered:
  - None.
- Validation:
  - Vue 구현, 집중 Vitest, Chromium desktop/mobile와 page 명세를 대조했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 source·retrieval·coverage v2 계약)

- What was done:
  - 기능·API·DB·페이지·기술 명세에 server-owned source block, display/scoring 분리, criterion별 hybrid retrieval과 analysis coverage를 반영했다.
- Key decisions:
  - 이전 rubric의 coverage는 null로 유지하고 v2의 전부 `UNKNOWN` 결과는 `fitScore=null`, `analysisCoverage=0.00`으로 구분한다.
- Issues encountered:
  - 기존 명세의 `MISSING|UNKNOWN=0` 계약이 근거 부족을 실제 불일치와 구분하지 못했다.
- Validation:
  - 구현 DTO·migration·workflow 상수와 문서의 v2 계약을 대조했다.
- Next steps:
  - 실제 Provider 관찰 결과가 source section 사전에 새 heading을 요구하는지 확인한다.

## [2026-08-04] Session Summary (공고 분석 실패 카드 재실행 계약 명확화)

- What was done:
  - Analysis Tab의 terminal 실패 카드에 단일 `공고 분석 재실행` CTA를 제공하는 계약을 명시했다.
- Key decisions:
  - `retryable=true`는 기존 Agent Run lineage retry를 사용하고, false이면 현재 공고 version의 강제 `BALANCED` 재분석을 새 실행으로 요청한다.
- Issues encountered:
  - None.
- Validation:
  - Job Analysis component 회귀와 Frontend 전체 check에 대조했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (가입 문구·동의 Modal 표시 계약)

- What was done:
  - `page.md`에 이메일 보조 안내 비표시, 비밀번호 세 문장, 동의 상세의 사용자 용어와 새 레이아웃 계약을 반영했다.
- Key decisions:
  - 화면에서는 비밀번호 저장 방식과 AI 세부 처리의 전문 명칭을 노출하지 않고, `안전하게 저장해요`와 `OpenAI 기반으로 처리해요`처럼 쉽게 설명한다.
- Issues encountered:
  - 수정된 E2E의 최종 완주는 재검증 상한으로 `NOT_VERIFIED`이다.
- Validation:
  - 페이지 명세를 Vue 구현·component test·Frontend 전체 check 결과에 대조했다.
- Next steps:
  - 다음 검증 회차에 desktop/mobile Chromium 완주를 확인한다.

## [2026-08-04] Session Summary (credential·온보딩·마감 선택 계약 갱신)

- What was done:
  - Signup password를 전체 10자 이상·문자/숫자/특수문자 각 1개 이상·UTF-8 72바이트 이하로 변경했다.
  - Page 계약에 blur 검증, 온보딩 eligibility GET/PUT과 날짜·오전/오후·30분 단위 공고 마감 입력을 추가했다.
- Key decisions:
  - Profile eligibility DTO와 Job `deadlineAt:Instant?` 계약은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Auth/OpenAPI 통합 테스트, Frontend component·Chromium 회귀와 양쪽 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 비밀번호·동의 상세 화면 계약)

- What was done:
  - `functional.md`의 비밀번호 규칙을 API와 같은 UTF-8 10..72바이트로 정합화했다.
  - `page.md`에 이메일 형식, 비밀번호 안내와 이용약관·개인정보·AI 처리 상세 Modal 내용·접근성 계약을 추가했다.
- Key decisions:
  - 숫자·특수문자 조합은 필수가 아니며 상세 확인은 동의 checkbox 상태를 변경하지 않는다.
- Issues encountered:
  - 운영 법인·문의처·국외 이전 세부는 현재 저장소 계약에 없어 명세에 임의 확정하지 않았다.
- Validation:
  - API·Backend DTO·Frontend schema와 OpenAI 공식 API 데이터 정책을 교차 확인하고 Frontend 전체 check를 통과했다.
- Next steps:
  - 운영 정보 확정 후 법률 검토된 개인정보 처리방침을 별도 계약으로 추가한다.

## [2026-08-02] Session Summary (Dashboard 중앙 열 정렬 계약)

- What was done:
  - Dashboard 바로가기를 제외한 헤더·CTA·본문을 viewport 중심의 동일 열에 배치하고 CTA 우측 경계를 본문 우측 경계에 맞추는 page 계약을 추가했다.
- Key decisions:
  - 기존 88rem Dashboard 외곽 폭, 우측 container sticky와 좁은 화면 가로 탐색 계약은 유지한다.
- Issues encountered:
  - None.
- Validation:
  - page 명세를 Dashboard CSS grid, 1440px geometry assertion과 1024·390px 반응형 screenshot에 교차 확인했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 분석 구조화 fact 활성 계약)

- What was done:
  - 기능·API·DB·페이지 계약에 지원 자격 자기신고와 structured fact provenance, strict support compatibility를 반영했다.
- Key decisions:
  - verified evidence와 structured profile fact, 공고 section과 scoring category/support type을 구분하고 기존 공개 evidence DTO는 유지한다.
- Issues encountered:
  - OpenAPI exact test는 신규 자동 `400`·CSRF `403` 기준선을 순차 보정한 뒤 재시도 상한에 도달해 마지막 assertion을 재실행하지 못했다.
- Validation:
  - 구현·집중 테스트·migration과 문서 계약을 대조했으며 최종 OpenAPI exact assertion은 `NOT_VERIFIED`다.
- Next steps:
  - 다음 검증 회차에서 69 paths/94 operations OpenAPI exact assertion을 확인한다.

## [2026-08-02] Session Summary (AI 결과 한국어·내부 경로 비노출 계약)

- What was done:
  - DOC-003에 문서 추출 소재의 한국어 제목·내용·경고 계약을 추가했다.
  - JOB-004와 Analysis Tab에 한국어 분석 문장, 한국어 출처 구역명, 결과 hero 문구와 내부 경로 치환을 명시했다.
- Key decisions:
  - 분석 버전 저장·이력, API DTO와 DB source provenance는 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 기능·페이지 명세와 구현 prompt·structured validation·Vue 회귀를 교차 확인했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (embedding capability route 계약 명확화)

- What was done:
  - 기능·기술 명세에 verified evidence retrieval이 Chat tier가 아닌 active embedding policy tuple을 사용하고 route identity를 checkpoint hash에 포함하는 계약을 반영했다.
- Key decisions:
  - 공개 품질 모드와 내부 Chat route는 embedding product 선택에 사용하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 구현 port·workflow·price policy와 명세를 교차 확인했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (이미지 reference Provider 전달 계약 명확화)

- What was done:
  - local image reference text와 해당 이미지 하나를 같은 Provider-visible message로 전달하는 기능·기술 계약을 명시했다.
- Key decisions:
  - Spring AI `Media.id/name`은 전달 계약이 아니며 server allowlist·중복·순서 검증은 유지한다.
- Issues encountered:
  - None.
- Validation:
  - 실제 SDK request capture 회귀와 활성 기능·기술 명세를 교차 확인했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 바로가기·문구 계약 보정)

- What was done:
  - Desktop 바로가기는 Dashboard container 안에서 스크롤을 따라오는 sticky이고 좁은 화면은 일반 가로 흐름이라는 계약을 명확히 했다.
  - 준비 workspace 제목은 단어 중간을 자르지 않는 의미 단위로 표시하도록 page 명세를 보강했다.
- Key decisions:
  - viewport `fixed` sidebar와 API·DB 변경 없이 presentation 계약만 갱신했다.
- Issues encountered:
  - None.
- Validation:
  - page 명세와 Dashboard 구현·unit·Chromium 회귀 결과를 교차 확인했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 제목 크기·journey 간격 계약 보정)

- What was done:
  - 공고 resource 제목의 `1.4–2.2rem` 범위와 Desktop 4단계 사이 균등 여백을 page 명세에 반영했다.
- Key decisions:
  - 분석 상태·API·한 줄 slide와 responsive 단계 전환 계약은 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - 1440·390px visual capture, geometry regression과 Frontend 표준 check 결과를 명세와 대조했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 탐색·Job Analysis 표시 계약 정리)

- What was done:
  - Dashboard의 비고정 섹션 바로가기, 시각적으로 숨긴 중복 요약 제목, 캘린더 이동 control과 self-hosted variable Noto Sans KR 제목 계약을 실제 구현에 맞췄다.
  - 공고 긴 제목 한 줄 slide, Frontend `BALANCED` 고정·품질 옵션 미노출과 사용자 실패 문구 계약을 반영했다.
- Key decisions:
  - Backend API의 `ECONOMY|BALANCED` 입력 계약은 유지하고 Frontend 선택 노출만 현재 범위에서 제거한다.
- Issues encountered:
  - 기존 page 명세에 오늘 복귀와 접힌 `ECONOMY` 옵션이 남아 있어 현재 제품 결정으로 교정했다.
- Validation:
  - page 명세와 Frontend component·unit·Chromium 결과를 교차 확인했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 캘린더 시각 상태 계약 보강)

- What was done:
  - 캘린더 summary·month toolbar, cell gap·soft border, today·selected·deadline event 상태와 hover 비침범 조건을 page 명세에 반영했다.
- Key decisions:
  - Backend/API·상세 목록 계약은 유지하고 presentation 계약만 구체화했다.
- Issues encountered:
  - None.
- Validation:
  - Dashboard 구현, component test와 Chromium 1440·390px 결과를 page 명세와 교차 확인했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 세부 UI·V18 Guide 계약 동기화)

- What was done:
  - 이름 단독 강조, 사람 SVG, 주말·`N건` 캘린더, workspace CTA와 장문 modal 계약 및 V18 콘텐츠 보강을 page·DB 명세에 반영했다.
- Key decisions:
  - V18은 schema 변경 없이 미수정 seed만 version 2로 올리고 미래 migration은 V19 이후로 이동한다.
- Issues encountered:
  - None.
- Validation:
  - 구현·migration test·Frontend Browser 결과와 page/db 명세를 교차 확인했다.
- Next steps:
  - P8.6 착수 시 latest V18을 기준으로 tentative V19를 재확인한다.

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
