# Progress

## Overview

- Vue 3, TypeScript, Vite, pnpm 기반 개발 환경과 주요 plugin이 구성되어 있다.
- P1 auth부터 P8 Interview typed client·Vue Query·답변 CAS·SSE terminal invalidation까지 구현되어 있다.
- `/guide`, `/agent-runs`, `/documents`, `/jobs`, `/cover-letters`, `/interviews`와 관련 child route는 lazy route이며 responsive AppLayout에는 Progress Drawer가 연결되어 있다.
- Vitest 65 files/258 tests와 공개 Landing·UI shell, P2~P8 actual E2E, 자동 분석·전반 화면 fixture Browser 회귀가 있다.

## [2026-08-02] Session Summary (공개 Landing·Dashboard 첫 사용 흐름)

- What was done:
  - `LandingPage.vue`와 공개 route, 공통 title hook, 정확한 metadata를 추가하고 인증 form·onboarding·보호 guide 계약을 유지했다.
  - Dashboard 시작 영역을 부분 완료와 query unknown을 구분하는 3항목 체크리스트로 바꾸고 일반 제품 현황을 항상 함께 표시했다.
- Key decisions:
  - 5단계 번호·아이콘·제목·핵심 설명만 `productJourney.ts`로 공유하고 공개 페이지에는 보호 route link를 두지 않았다.
- Issues encountered:
  - Chromium의 CSS duration 직렬화와 query slash encoding 차이를 E2E assertion에서 정규화했다.
- Validation:
  - `corepack pnpm check`: 65 files/258 tests, lint·format·typecheck·build 통과.
  - `landing.spec.ts` Chromium 6/6, `ui-shell.spec.ts` 3/3과 1440·390·320px·4개 화면 캡처 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (전반 B2C UI·공고 자동 분석 UX)

- What was done:
  - 상단 journey navigation·계정 메뉴·mobile bottom navigation, page variant와 token을 도입하고 dashboard부터 profile·documents·jobs·cover letters·interviews·AI 작업·onboarding까지 문구와 계층을 통일했다.
  - 공고 document view·자동 분석 journey·결과 요약·보조 재분석 옵션과 실제 component 기반 `/guide`를 구현했다.
- Key decisions:
  - 최초 분석은 서버 BALANCED projection을 읽으며 브라우저가 분석 command를 연쇄 호출하지 않는다. 원문은 `v-html` 없이 deterministic node로 렌더링한다.
- Issues encountered:
  - mobile 공고 탭의 4px bleed를 35rem gutter token에 맞춰 제거하고 기존 E2E heading 기대값을 새 semantic hierarchy와 동기화했다.
- Validation:
  - `corepack pnpm check`: 64 files/249 tests, lint·format·typecheck·build 통과. 실행 가능한 Chromium 9/9와 변경 후 화면 캡처 30장 통과.
- Next steps:
  - 전용 환경 flag가 필요한 P4~P8 `*.actual.spec.ts` 13건은 skip됐다.

## [2026-08-01] Session Summary (Job extraction v3 UI 회귀)

- What was done:
  - Backend v3가 공개 상태·step key를 바꾸지 않음을 확인하고 관련 index를 동기화했다.
- Key decisions:
  - OCR 선택 UI를 추가하지 않고 successor Run 갱신·safe fallback·manual/retry CTA·SSE reconnect 의미를 유지한다.
- Issues encountered:
  - None.

## [2026-08-01] Session Summary (이미지형 공고 자동 처리 UX)

- Validation:
  - `corepack pnpm check`: 61 files/243 tests, lint·format·typecheck·build 통과. P5 Chromium 5/5.
- Next steps:
  - None.

- What was done:
  - v2 Job workflow step을 사용자용 `공고 페이지 불러오기/내용 확인/이미지 읽기/채용 정보 확인/결과 저장` 문구로 매핑했다.
  - 진행·자동 판독 부족 문구를 보정하고 기존 manual input/retry CTA와 SSE 복구 의미를 유지했다.
- Key decisions:
  - 공고 등록 폼에 OCR·이미지 공고 선택 control을 추가하지 않는다.
- Issues encountered:
  - 기존 문구 assertion 2건을 새 fallback 메시지로 동기화했다.
- Validation:
  - `corepack pnpm check`: 61 files/243 tests, lint·format·typecheck·build 통과. P5 actual Chromium 5/5.
- Next steps:
  - None.

## [2026-08-01] Session Summary (자료 검토·대외활동·알림 UX 보정)

- What was done:
  - 자료 목록·상세·미리보기와 소재 검토를 행동 순서 중심으로 재설계하고 개별/선택/전체 승인·제외·재검토를 추가했다.
  - 직접 등록 대외활동 CRUD 화면과 전역 토스트·접근 가능한 확인 모달을 구현하고 브라우저 기본 alert/confirm/prompt를 제거했다.
  - AI 상세를 기본 접힘과 사용자 단계명으로 바꾸고 USD 및 기술 오류를 숨겼으며 중복 결과 영역을 통합했다.
- Key decisions:
  - 성공은 toast, 필드 문제는 inline, 삭제·재분석·승인 취소는 alertdialog로 역할을 분리했다.
- Issues encountered:
  - 실제 실패 run에서 run-level safe message도 기술적으로 보일 수 있어 원문을 직접 렌더링하지 않도록 추가 보정했다.
- Validation:
  - `corepack pnpm check`: ESLint, Prettier, vue-tsc, 61 files/243 tests, production build 통과. Playwright desktop/mobile 실제 흐름과 focus/ESC/overflow를 확인했다.
- Next steps:
  - 실제 성공 분석의 복수 소재 브라우저 검증은 외부 Provider 사용 승인을 받은 환경에서 수행한다.

## [2026-07-31] Session Summary (P8 면접 준비·질문·답변 피드백 UI)

- What was done:
  - 공고 면접 tab, 질문 세트 목록·상세, 출처 coverage, immutable 답변 version과 feedback 진행·이력을 기존 shell에 추가했다.
- Key decisions:
  - 답변 409는 mutation을 자동 재시도하지 않고 최초 사용자 snapshot과 최신 server content를 비교한 뒤 명시적 재적용만 허용한다.
  - `qs*` URL namespace를 canonical API filter로 변환하며 P9 mock session UI는 만들지 않았다.
- Issues encountered:
  - P8 projection을 거부하던 과거 Job contract fixture를 현재 활성 계약으로 갱신했다.
- Validation:
  - `corepack pnpm check`: 60 files/238 tests, lint·format·typecheck·production build 통과. P8 actual은 desktop·mobile·200% scale과 overflow/focus를 검증했다.
  - 두 번째 single-agent read-only self-audit는 새 finding 없이 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (닉네임 Modal·최종 학력 UI 보정)

- What was done:
  - 상단 닉네임을 accessible Modal trigger로 바꾸고 기본 정보 Form에서는 닉네임 입력을 제거했다.
  - 대외활동 승인·거절 안내, AI 작업 선택/삭제 문구, 관심 공고 active hover와 최종 학력 단계·배지를 반영했다.
- Key decisions:
  - 최종 학력은 response의 read-only `isPrimary`만 표시하고 수동 대표 지정 control은 제공하지 않는다.
- Issues encountered:
  - production build에서 AppLayout media query 닫힘 누락을 발견해 수정했다.
- Validation:
  - `corepack pnpm check`: 53 files/216 tests, lint·format·typecheck·production build 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (대외활동 UI·AI 작업 내역 삭제)

- What was done:
  - 기본 정보 savebar를 Form 하단으로 이동하고 경험 정보를 대외활동으로 변경했으며 filter gap과 학력 상태 한국어 mapping을 적용했다.
  - 문서 AI 근거에만 승인·거절을 노출하고 직접 입력·AI confidence 의미를 구분해 안내했다.
  - Agent Run 목록에 terminal row 개별 삭제, 현재 페이지 선택·전체 선택과 일괄 삭제를 추가했다.
- Key decisions:
  - active Agent Run checkbox와 삭제 action은 비활성화하고 mutation 성공 시 detail cache 제거와 owner root invalidation을 수행한다.
  - server enum은 list subtitle과 409 conflict 양쪽에서 사용자 문구로 변환한다.
- Issues encountered:
  - 전체 check 중 수정 파일 4개와 마지막 학력 상태 mapping 1개의 Prettier 경고를 각각 대상 파일만 format해 해소했다.
- Validation:
  - `corepack pnpm check`: ESLint, Prettier, typecheck, 53 files/215 tests, production build 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (프로필 화면·닉네임 편집 보정)

- What was done:
  - 프로필 navigation을 설명 없는 7개 항목으로 축약하고 첫 route 진입 scroll과 focus를 보정했다.
  - 기본 정보 save bar 좌우 잘림과 자기소개서 filter 간격을 수정하고, 기존 저장 action에 nickname validation·API·store projection 갱신을 연결했다.
- Key decisions:
  - 새 전용 설정 화면을 복제하지 않고 프로필 기본 정보에서 profile DTO와 account display-name 요청을 조정한다.
  - route 이동 focus는 `preventScroll`을 사용하고 browser history 저장 위치 외 새 진입만 상단으로 이동한다.
- Issues encountered:
  - 실브라우저의 기존 Backend process가 변경 전 source여서 닉네임 API 결합은 unit·Spring integration으로 분리 검증했다.
- Validation:
  - `corepack pnpm check`: ESLint·Prettier·vue-tsc·53 files/214 tests·Vite build 통과.
  - Playwright CLI 1440×1000에서 profile sidebar 전체 노출, save bar 좌우 inset과 Cover Letter filter 12px gap을 확인했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 Frontend 최종 validator PASS)

- What was done:
  - 최종 read-only validator가 route 3개, canonical editor, sessionStorage draft, archived read-only, historical evidence와 작업별 409 비교·재적용을 독립 재검증했다.
- Key decisions:
  - 자기소개서 결과는 `/agent-runs`에 복제하지 않고 resource link만 유지한다.
- Issues encountered:
  - 새 finding 없음.
- Validation:
  - Validator `PASS`; Frontend 53 files/211 tests와 lint·format·typecheck·build, P7 actual Chromium 1/1 PASS.
- Next steps:
  - P8 UI는 Backend 공개 계약이 별도 단계에서 고정되기 전 추가하지 않는다.

## [2026-07-30] Session Summary (P7 409 비교·suggestion 계약 보정)

- What was done:
  - verification suggestion schema를 최대 20개·항목 1~1000자로 고정하고 20/21개·1000/1001자 경계 테스트를 추가했다.
  - TITLE·QUESTION·ORDER·ANSWER·LIFECYCLE mutation마다 최초 사용자 입력을 immutable snapshot으로 보존하고 실제 최신 server field 비교, 최신 CAS 재적용과 취소 동작을 분리했다.
  - actual E2E에 제목 외 실제 문항 충돌과 refetch 뒤 사용자 snapshot 재적용을 추가했다.
- Key decisions:
  - 충돌 refetch가 form/editor를 갱신해도 retry closure는 reactive 값을 읽지 않으며 사용자가 `최신 버전에 재적용`을 선택할 때만 저장된 snapshot을 사용한다.
- Issues encountered:
  - Vue Proxy를 직접 `structuredClone`할 수 없는 답변 취소 테스트를 plain TipTap snapshot 복제로 보정했다.
- Validation:
  - `corepack pnpm check`: 53 files/211 tests와 ESLint·Prettier·vue-tsc·Vite build 통과.
  - P7 actual Chromium 1/1에서 문항 `409`의 server field 비교와 immutable 재적용 `200`, 1440/390px overflow가 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 기다린다.

## [2026-07-30] Session Summary (P7 자기소개서 목록·공고 tab·편집 화면)

- What was done:
  - `/cover-letters`, `/jobs/:jobId/cover-letter`, `/cover-letters/:coverLetterId/edit` route와 navigation, 목록 filter·archive, 공고 상태 tab과 canonical editor를 구현했다.
  - TipTap 답변, question CRUD/order, evidence 선택, generation partial result, version save/compare/restore, verification·finalize, archive read-only와 sessionStorage draft·409 재적용을 연결했다.
- Key decisions:
  - URL query가 목록 filter 원천이고, browser draft는 user/question/base version별 sessionStorage에만 두며 server save·delete·archive·logout·401에서 제거한다.
- Issues encountered:
  - 실제 number input의 Vue runtime 값 타입 불일치를 string/number parser와 form 제출 component test로 보정했다.
- Validation:
  - `corepack pnpm check`에서 53 files/204 tests, lint·format·typecheck·production build가 통과했다.
  - P7 actual Chromium 1/1에서 전체 시나리오와 1440/390px overflow·사용자 격리가 통과했다.
- Next steps:
  - 독립 validator 판정 후 P7 상태를 확정한다.

## [2026-07-30] Session Summary (P6 final-source Frontend actual E2E 종료)

- What was done:
  - Backend 주도 wrapper에서 현재 `job-analysis.actual.spec.ts`의 정상 분석·reuse·OUTDATED·재분석·근거 부족·owner 격리를 실행했다.
- Key decisions:
  - 공개 PUT evidence endpoint를 통한 타 사용자 404 assertion을 유지하고 비계약 단건 GET을 다시 도입하지 않았다.
- Issues encountered:
  - Browser 시나리오는 첫 gate부터 exit 0이었고 실패는 wrapper DB 컬럼명에 한정됐다.
- Validation:
  - 최종 wrapper의 Playwright Chromium 2/2 통과, Vite process 정상 종료.
- Next steps:
  - P7 Frontend는 Backend DTO가 고정된 뒤 세 신규 route와 자기소개서 draft·version·verification UX를 구현한다.

## [2026-07-29] Session Summary (P6 공고 분석 화면·AI 작업 내역 연결)

- What was done:
  - `/jobs/:jobId/analysis`의 실행·진행·실패·성공·OUTDATED·이력 상태와 user-scoped typed query/client를 구현했다.
  - Eligibility와 fit score, 강점·부족한 점·criterion·VERIFIED evidence를 분리해 표시하고 사용자 용어를 `AI 작업 내역`으로 통일했다.
  - 분석 당시 근거가 이후 `PENDING`, `REJECTED`, `SOURCE_DELETED`로 바뀌어도 기존 결과와 점수를 유지하고 현재 상태·재분석 제외 안내를 텍스트로 표시한다.
- Key decisions:
  - 공고 상세 tab은 `공고 정보`와 `공고 분석`만 제공하며 P7/P8 가짜 tab을 만들지 않는다.
  - `/agent-runs`에는 결과를 복제하지 않고 `JOB_ANALYSIS` Run에서 공고 분석 화면으로 가는 resource link만 표시한다.
- Issues encountered:
  - actual E2E의 중복 제목 locator는 최신 결과 ID로 제한했고, 비공개 evidence GET assertion은 공개 PUT owner 404로 교체했다. 교체 후 actual E2E는 재실행하지 않았다.
  - 1차 validator가 current evidence 상태를 이유로 historical detail 전체를 거부하는 schema를 MAJOR로 판정해 canonical 상태는 수용하되 현재 positive proof로 오해하지 않는 UI로 보정했다.
- Validation:
  - 보정 후 `corepack pnpm check`: lint·Prettier·TypeScript·42 files/169 tests·production build 통과.
  - Job Analysis 1440px/390px와 Agent Run reconnect fixture Chromium 3/3, horizontal overflow·keyboard tab 검증이 통과했다.
  - 최종 validator는 historical evidence detail·UI finding 해소를 확인했지만 final-source actual P6 wrapper 미실행으로 전체 `FAIL`을 유지했다.
- Next steps:
  - 새로 승인된 검증 주기에서 current actual P6 spec을 wrapper로 실행한다. 이 요청에서는 추가 자동 재검증하지 않는다.

## [2026-07-28] Session Summary (데이터 기반 지원 홈·프로필 편집 경험 재설계)

- What was done:
  - Sidebar·header·route meta의 `오늘의 준비`를 `지원 홈`으로 통일하고 Dashboard를 상태 카드, 동적 다음 할 일, 마감 임박 공고, 최근 활동과 신규 사용자 시작 안내로 재설계했다.
  - Profile 기본 정보는 얕은 section 구조, 일관된 저장 상태, field error, 추천 축약형 tag 입력과 반응형 내비게이션을 적용했다.
  - 공통 content 폭, sidebar 폭과 Profile navigation 폭을 조정하고 Dashboard unit test와 6개 viewport UI fixture를 보강했다.
- Key decisions:
  - 보라색 장식과 중첩 card를 늘리지 않고 기존 Hiresemble Blue를 primary CTA·현재 위치·선택 상태에 한정했다.
  - 현재 Backend에 없는 Dashboard endpoint를 만들지 않았으며 목록 길이로 전체 건수를 추정하지 않고 `totalElements`만 사용했다.
- Issues encountered:
  - 첫 1440px 시각 검사에서 지표 grid의 빈 칸을 발견해 12-column 비대칭 배치로 보정한 뒤 desktop·mobile을 다시 확인했다.
- Validation:
  - `corepack pnpm check`: ESLint, Prettier, TypeScript, Vitest 40 files/154 tests와 production build 통과.
  - `UI_SCREENSHOTS=true` Chromium UI shell 3/3 통과; Dashboard·Profile 1440px와 390px 결과를 직접 검토했다.
- Next steps:
  - 실제 Backend cross-stack E2E와 보조 기술 실기기 검증은 별도 실행이 필요하다.

## [2026-07-28] Session Summary (첨부 화면 기반 반응형·정보 계층 보정)

- What was done:
  - 인증 shell의 최대 콘텐츠 폭, Dashboard hero 제목의 반응형 줄바꿈, profile outline의 인접 상태 간격을 보정했다.
  - 기본 정보 form의 두 정보군을 명확히 분리하고 안내 surface, 자료·공고 filter grid 간격을 Hiresemble Blue 체계에 맞췄다.
- Key decisions:
  - 기능 계약과 selector는 유지하고 기존 공용 token 및 scoped CSS만 활용해 dependency를 추가하지 않았다.
- Issues encountered:
  - 390px 첫 시각 검수에서 Dashboard 제목의 과도한 줄바꿈을 발견해 의미 단위 두 줄로 다시 조정했다.
- Validation:
  - `corepack pnpm check`: ESLint·Prettier·TypeScript·Vitest 39 files/149 tests·production build 통과.
  - fixture Chromium UI shell 3/3과 2500·1600·1574·1440·390px 시각 검수, 390px horizontal overflow 검사가 통과했다.
- Next steps:
  - 실제 Backend가 필요한 profile·document·job cross-stack E2E는 이번 시각 보정에서 재실행하지 않았다.

## [2026-07-28] Session Summary (전체 Frontend Career Workspace·Control System 재설계)

- What was done:
  - 모든 현재 route의 navigation, form, filter, loading·empty·error·status 표현을 `#3157ff` Hiresemble Blue와 공용 44px control language로 통합했다.
  - profile deep link는 유지하면서 desktop 세로 outline·mobile selector·이전/다음 action을 갖는 Career Profile Workspace로 재구성했다.
- Key decisions:
  - native select·checkbox·date semantics를 유지해 keyboard와 mobile 사용성을 보존하고 미구현 대외활동·공고 분석 route는 추가하지 않았다.
- Issues encountered:
  - 시각 재검토에서 자료 등록의 영어 eyebrow를 발견해 사용자용 한국어로 한 차례 보정했다.
  - actual Document E2E는 실행 중 Backend의 upload 일반 오류로 첫 test가 timeout됐고 Job actual은 필수 fixture URL이 없어 실행하지 않았다.
  - actual Profile E2E는 selector 보정 후 완료율 `100%` strict locator 중복으로 재검증도 실패해 추가 보정하지 않았다.
- Validation:
  - `corepack pnpm check`: ESLint·Prettier·TypeScript·Vitest 39 files/149 tests·production build 통과.
  - fixture Playwright 5/5와 1440·1024·768·390px 18개 화면 overflow·캡처 검수 통과; actual Profile 0/1·Document 0/4 완료.
- Next steps:
  - Backend 재시작·fixture URL 준비 후 actual Document·Job Playwright를 재실행한다.

## [2026-07-28] Session Summary (프로필·자료 등록 전문 UX와 추천 입력 적용)

- What was done:
  - 회원가입·로그인 이메일 형식, 닉네임·분석 기록·졸업(예정)일 문구를 사용자 화면에 적용했다.
  - 한국형 희망 직무·지역 preset과 포함 검색 combobox를 추가하고 프로필·자료 등록 정보 구조를 재설계했다.
- Key decisions:
  - 기존 API DTO·route·문자열 배열·Session/CSRF 계약과 design token을 유지했다.
- Issues encountered:
  - 변경된 제목을 참조하던 Playwright selector 한 건을 새 사용자 용어로 동기화했다.
- Validation:
  - `corepack pnpm check`, Playwright UI shell 3/3, Agent Run 2/2와 실제 Document E2E 4/4가 통과했다.
- Next steps:
  - 재시작한 실제 Backend·MinIO 환경에서 긴 파일명과 실제 데이터 밀도를 추가 검수한다.

## [2026-07-28] Session Summary (B2C UX Writing·Brand Experience 통합)

- What was done:
  - 인증, onboarding, dashboard, profile, documents, jobs, Agent Run과 404의 사용자 문구와 시각 언어를 현재 route 범위 안에서 전면 재설계했다.
  - 공용 BrandMark, cobalt 중심 token, 비대칭 인증 canvas, 연결형 dashboard와 기능적인 motion을 추가했다.
- Key decisions:
  - 내부 route·타입은 유지하되 화면에서는 `AI 작업`, `작업 종류`, `예상 사용 비용`, `경험 정보`, `이력서·자료`, `지원 상태`, `공고 불러오기`를 일관되게 사용한다.
  - 비밀번호 byte 검증은 보존하고 화면에는 길이 조정 행동만 안내한다.
- Issues encountered:
  - 실제 Backend 없이 수행한 headed 인증 검수에는 예상된 network 오류가 있었으며, 인증·AI 작업 상호작용은 fixture E2E로 별도 검증했다.
  - 최초 read-only validator가 원시 작업 단계·JSON 편집기·MIME type 노출, 인증 DOM 순서, muted 대비와 touch target을 지적해 소비자용 표현과 접근성 기준으로 한 차례 보정했다.
  - 2차 validator가 metadata key 무손실 보존과 빈 typed value 검증을 추가 지적해 임의 key 제한·trim을 제거하고 primitive round-trip 테스트를 추가했다. 저장소의 재검증 상한 때문에 이 최종 보정 뒤 세 번째 독립 검증은 실행하지 않았다.
- Validation:
  - `corepack pnpm check`가 lint·format·typecheck, Vitest 36 files/134 tests와 production build까지 통과했다.
  - fixture Chromium 4/4와 1440·1024·768·390px shell, 1440·390px 인증 화면, heading 순서·44px password toggle·reduced-motion을 검증했다.
  - main JS 377.15 kB(gzip 118.95 kB), entry CSS 61.58 kB(gzip 12.41 kB)이며 dependency 증가는 없다.
  - 최종 보정 뒤 로컬 검증은 통과했지만 마지막 독립 validator 판정은 보정 전 `NEEDS_CHANGES`이므로 독립 최종 상태는 `NOT_VERIFIED`다.
- Next steps:
  - 실제 Backend 환경의 cross-stack E2E와 screen reader 실기 검수는 후속 수행한다.

## [2026-07-27] Session Summary (현재 구현 화면 제품 UI/UX 통합)

- What was done:
  - router에 존재하는 18개 사용자 route와 전용 404를 하나의 graphite·blue-teal 제품 언어로 개선했다.
  - token·공용 UI, desktop sidebar·mobile drawer, 2열 인증 shell, 업무별 정보 구조와 loading·empty·error·status 표현을 연결했다.
- Key decisions:
  - Dashboard는 가상 KPI 없이 profile·documents·job 등록·Agent Run으로 이동하는 실제 작업 공간만 제공한다.
  - 문서 parse/근거 추출, 공고 업무/추출, Run business/연결 상태를 계속 분리하고 query·mutation·cleanup 순서는 유지했다.
- Issues encountered:
  - 직접 캡처에서 미사용 PrimeVue 전역 초기화가 라이선스 배지를 렌더링해 초기화만 제거하고 dependency·lockfile은 유지했다.
  - full E2E 선택 실행이 실제 Backend 의존 spec까지 포함해 timeout되어 fixture 두 파일을 직접 지정했다.
- Validation:
  - `corepack pnpm check`가 35 files/128 tests와 production build까지 통과했고 fixture Chromium 4/4가 통과했다.
  - 1440·1024·768·390px overflow와 mobile drawer focus를 자동 검증하고 1440·390px 화면을 직접 시각 검수했다.
  - 실제 Backend·PostgreSQL·MinIO/Fake gateway가 필요한 profile·Document·Job actual E2E와 screen reader 실기 검수는 현재 환경에서 실행하지 않았다.
  - read-only validator가 미구현 기능·계약·접근성·반응형·문서 검토를 `PASS WITH WARNINGS`로 완료했다.
- Next steps:
  - cross-stack actual E2E와 screen reader 수동 검수는 필요한 서비스가 준비된 환경에서 수행한다.

## [2026-07-27] Session Summary (P5 Jobs 화면·실제 E2E 구현)

- What was done:
  - strict Zod Job 계약·API·Vue Query와 `/jobs`, `/jobs/new`, `/jobs/:jobId/overview`를 구현했다.
  - URL query filter, 201/202 생성, 상태·retry·manual·delete, 409 비교·재적용과 기존 Agent Run stream invalidation을 연결했다.
- Key decisions:
  - 업무/추출 상태 badge를 분리하고 NEEDS_MANUAL_INPUT은 수동 입력, FAILED는 retry와 수동 입력을 제공한다.
  - P5 projection은 `null`, `false`, `[]`, `0`으로 엄격히 검증하고 P6 분석 DTO·route를 만들지 않는다.
- Issues encountered:
  - 최초 validator가 P6 DTO 선행 구현과 NEEDS_MANUAL_INPUT retry 노출을 찾아 한 차례 보정했다.
- Validation:
  - `corepack pnpm check`가 32 files/122 tests와 production build까지 통과했고 P5 실제 Chromium E2E 5/5가 통과했다.
- Next steps:
  - P6 계약 구현 시 Job detail child route 아래 analysis 화면을 별도 추가한다.

## [2026-07-19] Session Summary (P4 Documents 화면·실제 E2E 구현)

- What was done:
  - Document DTO/API, user-scoped query key, lazy 목록·상세 route와 upload·manual·reparse·download·delete UI를 구현했다.
  - 두 상태 축과 partial success, evidence 편집·승인·거절·SOURCE_DELETED read-only, P2 증빙 문서 selector를 연결했다.

- Key decisions:
  - SSE는 invalidation 신호이며 REST Document 상태가 최종 원천이다. logout·401·사용자 전환·delete에는 cache와 EventSource를 정리한다.

- Issues encountered:
  - 빠른 WAITING_USER 전이에서 detail이 stale일 수 있어 해당 SSE event에도 document query invalidation을 추가했다.

- Validation:
  - `corepack pnpm check`가 26 test files/95 tests와 production build를 통과했다.
  - main bundle은 517.24 kB(gzip 143.50 kB), 기준선 대비 +8.77 kB(+2.67 kB gzip)이고 Document route는 lazy chunk다.
  - P3 Playwright 2/2와 실제 Backend P4 Playwright 4/4가 통과했다.
  - 최종 read-only Validator가 상태 UI·owner cache·SSE cleanup과 실제 E2E 근거를 포함해 `PASS`했다.

- Next steps:
  - P4 Frontend는 완료됐으며 P5 이후 화면과 Dashboard·AI settings는 추가하지 않는다.

## [2026-07-19] Session Summary (P3 Agent Run 목록·상세·SSE Frontend 구현)

- What was done:
  - exact enum·nullable DTO, repeatable filter·pagination·sort client와 retry·cancel mutation을 구현했다.
  - lazy list/detail page, 안전한 단계·비용 projection과 최근 active Run Drawer를 구현했다.
  - snapshot-first SSE, reconnect·polling과 session boundary cleanup을 구현했다.

- Key decisions:
  - reconnect는 1/2/5초 총 3회 뒤 5초 REST polling이며 10/30초 값은 이번 threshold에서 사용하지 않는다.
  - 실제 비용은 고정 catalog 기반 billable estimate로 안내하며 provider/model/prompt 내부값을 표시하지 않는다.
  - Header count는 같은 owner-scoped 목록 query의 `totalElements`를 사용하고 Drawer 목록만 최근 5개로 제한해 Dashboard 집계처럼 추정하지 않는다.

- Issues encountered:
  - 첫 E2E의 중복 progress locator와 첫 전체 check의 repeatable query expectation을 보정했다.

- Validation:
  - `corepack pnpm check`에서 78 tests와 production build가 통과했고 Chromium Agent Run fixture 2/2가 통과했다.
  - main JS는 508.47 kB/gzip 140.83 kB, CSS는 18.52/4.44 kB이며 Agent Run UI는 별도 lazy chunk다.
  - 최종 read-only Validator가 reconnect·polling·session cleanup과 lazy route 회귀를 포함해 `PASS`로 판정했다.

- Next steps:
  - P4 이후 typed resource deep link·query invalidation을 실제 domain route와 연결한다.

## [2026-07-19] Session Summary (P2 프로필·온보딩·evidence Frontend 구현)

- What was done:
  - Backend 25개 profile operation의 TypeScript 계약·client와 모든 user-scoped query key를 구현했다.
  - 기본 form, 프로필 5종 CRUD, 대표 학력, evidence filter·편집·검토, `SOURCE_DELETED` read-only와 409 비교·재적용 UI를 구현했다.
  - P2 onboarding과 profile route·returnTo·404 회귀, document 기능 비활성 안내를 구현했다.

- Key decisions:
  - 서버 상태는 Vue Query, 인증 사용자·전역 reset만 Pinia, form draft는 component local state로 유지한다.
  - profile incomplete는 경고·진행률 표시이며 route hard gate가 아니다.
  - document 선택과 filter control은 P4 전까지 활성화하지 않는다.

- Issues encountered:
  - GPA conflict snapshot 변환과 onboarding fetch 오류 진행을 unit/component test로 보정했다.
  - Playwright webServer의 Windows pnpm 탐색을 `corepack pnpm dev`로 고치고 중복 text locator를 정확한 heading으로 좁혔다.
  - 첫 최종 `pnpm check`에서 Vitest가 `e2e/profile.spec.ts`를 수집해 실패했으므로 기본 exclude에 `e2e/**`를 추가했다.

- Validation:
  - `Set-Location frontend; corepack pnpm check`에서 lint·format·typecheck, 13개 파일 57개 Vitest와 production build가 통과했다.
  - Playwright Chromium 1개 E2E가 가입→프로필→두 사용자 403/404→cache cleanup→재로그인 흐름으로 통과했다.
  - 최종 read-only validator가 Frontend parity·cache·E2E 근거를 BLOCKER·MAJOR·MINOR 없이 `PASS`로 판정했다.

- Next steps:
  - P2는 완료 상태다.
  - P4 Backend 문서 계약이 확정될 때 document 연결 UI를 활성화한다.

## [2026-07-19] Session Summary (P1 프론트엔드 인증·route 기반 구현)

- What was done:
  - Axios `/api/v1` client, Cookie·CSRF bootstrap/교체, typed 오류·field mapping과 QueryClient를 구현했다.
  - unknown/authenticated/anonymous auth store, me bootstrap, signup·login·logout과 401·logout cleanup을 구현했다.
  - 두 Form, PublicLayout·AppLayout, 안전한 `returnTo`, route guard, onboarding/dashboard shell과 404를 구현했다.

- Key decisions:
  - 백엔드 직접 성공 DTO를 소비하고 성공 envelope를 가정하지 않는다.
  - logout·401 시 EventSource cleanup port, query 취소·cache clear, Pinia reset과 현재 사용자 draft purge 순서를 보장한다.
  - P1에는 resource별 draft와 프로필 Form·Dashboard 카드·문서 UI를 만들지 않는다.

- Issues encountered:
  - server field error 시 disabled 입력을 focus할 수 없던 문제를 component test로 재현해 submitting 해제 후 focus하도록 수정했다.
  - 401 또는 logout 뒤 보호 route에 남지 않도록 auth store 변화를 router가 관찰해 안전한 login returnTo로 이동하게 했다.

- Validation:
  - 구현 에이전트와 루트에서 `Set-Location frontend; corepack pnpm check`를 각각 실행해 ESLint, Prettier, vue-tsc, 7개 파일 35개 Vitest, 361 module production build가 통과했다.
  - auth 상태·Form·returnTo·guard·401 cleanup·두 사용자 cache 분리·shell·404를 unit/component test로 검증했다.
  - 실제 브라우저 cross-stack Playwright는 실행하지 않았고 외부 provider 호출도 없었다.

- Next steps:
  - P2 프로필·dashboard 실제 UI는 새 backend 계약이 고정된 뒤 typed client와 함께 추가한다.
  - 브라우저 기반 통합 환경이 준비되면 실제 Cookie·CSRF signup→login→logout smoke flow를 추가한다.

## [2026-07-17] Session Summary (Vue 프론트엔드 초기 환경 구성)

- What was done:
  - 당시 구현 상태:
    - Vue 3, TypeScript, Vite, pnpm 기반 개발 환경과 주요 plugin이 구성되어 있다.
    - `src/main.ts`가 Pinia, Vue Router, Vue Query, PrimeVue Aura theme을 등록한다.
    - `App.vue`는 `RouterView`만 제공하고 router의 `routes`는 비어 있어 제품 화면은 아직 없다.
    - unit/component test와 E2E test 파일은 아직 없으며 실제 비즈니스 API client도 구현되지 않았다.
  - 완료된 작업:
    - 프론트엔드 초기 개발 환경과 고정된 pnpm 의존성 구성을 마련했다.
    - Vite 개발 서버의 `/api` proxy, TypeScript strict 설정, ESLint, Prettier, Vitest, Playwright 설정을 추가했다.
    - 애플리케이션 진입점과 전역 plugin 등록, Tailwind와 PrimeVue theme 연결을 구성했다.
    - 작업 목적에 따라 `frontend/index.md`와 이 문서를 생성해 모듈 책임, 주요 파일, 실제 미구현 범위를 문서화했다.
  - 당시 진행 중인 작업:
    - 현재 진행 중인 프론트엔드 비즈니스 화면이나 사용자 여정 구현은 없다.
    - 프론트엔드와 하위 영역의 초기 문서 계층 구성은 이번 작업에서 완료했다.

- Key decisions:
  - 서버 상태는 TanStack Vue Query, 최소한의 전역 클라이언트 상태는 Pinia로 분리한다.
  - 기능 구조가 확정되기 전 빈 page/component/store 계층을 미리 대량 생성하지 않는다.
  - `package.json`의 통합 `check`를 프론트엔드 기본 검증으로 사용하며, 이 명령의 Prettier 단계가 모듈 내 Markdown도 검사하도록 유지한다.
  - API는 `/api/v1` 직접 성공 DTO와 표준 오류 응답 계약을 기준으로 typed client를 구성할 예정이다.

- Issues encountered:
  - router route가 비어 있고 `App.vue`에 `RouterView` 외 UI가 없어 현재 앱은 실제 제품 화면을 제공하지 않는다.
  - `pnpm check`는 `--passWithNoTests`로 unit test가 없어도 통과할 수 있으므로 check 성공을 기능 테스트 존재로 해석하면 안 된다.
  - `pnpm check`에는 Playwright 실행이 포함되지 않으므로 E2E 검증은 별도로 수행해야 한다.

- Validation:
  - 기본 검증 명령: `Set-Location frontend; corepack pnpm check`
  - 문서 링크·형식 확인 명령: `corepack pnpm exec prettier --check index.md progress.md e2e/index.md e2e/progress.md src/index.md src/progress.md src/router/index.md src/router/progress.md src/styles/index.md src/styles/progress.md`
  - 두 명령 모두 성공했다. 통합 check에서 ESLint, Prettier, TypeScript 검사와 production build가 통과했고, Vitest는 test file이 없어 `--passWithNoTests`로 종료 코드 0을 반환했다.
  - 10개 문서의 필수 섹션과 상대 링크도 PowerShell 정적 검사로 확인했다. E2E는 테스트가 없어 이번 문서 작업에서 실행하지 않았다.

- Next steps:
  - 페이지 명세에 따른 layout, 인증·온보딩, 대시보드와 도메인별 route/page 구현
  - typed API client, 공통 오류 정규화, Vue Query query/mutation과 필요한 Pinia store 구현
  - loading, empty, error, success 상태와 접근성·반응형 UI 구현
  - Vitest/Vue Test Utils 테스트와 핵심 사용자 여정 Playwright 테스트 추가
