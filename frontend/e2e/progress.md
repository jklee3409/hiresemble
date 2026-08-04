# Progress

## Overview

- `profile.spec.ts`가 실제 Chromium에서 P2 가입·온보딩·프로필·두 사용자 격리·cache cleanup을 검증한다.
- `agent-runs.spec.ts`가 test-local REST/SSE fixture로 P3 reconnect·polling·action cleanup을 검증한다.
- `ui-shell.spec.ts`가 fixture 인증으로 필수 viewport의 overflow, navigation과 drawer focus를 검증한다.
- `documents.actual.spec.ts`가 격리 Backend·PostgreSQL·MinIO·Fake AI에서 P4 실제 pipeline 4개를 검증한다.
- `jobs.actual.spec.ts`가 격리 Backend·PostgreSQL·Fake fetch/Chat에서 P5 실제 pipeline 5개를 검증한다.
- `job-analysis.spec.ts`가 fixture로 P6 결과·OUTDATED·접근성·desktop/mobile overflow를 검증한다.
- `landing.spec.ts`가 공개 진입, auth-aware `/`, 1440·390·320px와 Dashboard 0/3~3/3 fixture를 검증한다.
- `job-analysis.actual.spec.ts`가 P6 실제 Backend 분석·reuse·재분석·근거 부족·owner 격리를 검증하도록 구성되어 있다.
- `cover-letter.actual.spec.ts`가 P7 실제 생성·문항·partial AI·version·검증·finalize·restore·archive·근거 수명주기·owner 격리를 검증한다.
- `interview-preparation.actual.spec.ts`가 P8 실제 조사·coverage·질문·답변 CAS·feedback·retry·history delete·owner 격리를 검증한다.
- `playwright.config.ts`는 `corepack pnpm dev`로 Vite web server를 시작하고 Chromium project를 사용한다.
- 테스트는 외부 provider와 운영 데이터 없이 격리 DB·Object Storage 또는 Playwright route fixture를 사용한다.

## [2026-08-04] Session Summary (Landing motion·조건 결과 pagination 회귀)

- What was done:
  - Landing의 desktop·390·320px, reduced-motion과 공고 분석 결과의 6개 criterion 5/1 paging을 fixture로 검증하도록 회귀를 확장했다.
  - 390px 판단 board의 계산된 grid 열 수와 score·facts 실제 위치를 검사해 모바일 2열 계약을 고정했다.
- Key decisions:
  - 외부 API 없이 route fixture만 사용하고 표시 여부뿐 아니라 실제 geometry와 가로 overflow를 함께 검증한다.
- Issues encountered:
  - 인앱 browser 연결은 없었으나 외부 reference와 로컬 Vite 화면 모두 Playwright CLI·test의 실제 Chromium으로 확인했다.
- Validation:
  - Landing·Job Analysis Chromium 8/8, visual fixture 1/1과 수정 후 Job Analysis Chromium 1/1 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 제품 위계 회귀 갱신)

- What was done:
  - `job-analysis.spec.ts`를 새 결과 heading과 `핵심 요약` 문구에 맞추고 결과 화면의 primary action이 하나인지 확인하도록 갱신했다.
- Key decisions:
  - route tab과 결과 heading을 구분하고 기능 상태보다 사용자 판단 heading을 시나리오의 화면 진입 근거로 사용한다.
- Issues encountered:
  - 인앱 Browser에 연결 가능한 browser가 없어 Chromium 시나리오는 실행하지 못했다.
- Validation:
  - TypeScript·ESLint·Prettier를 포함한 Frontend 전체 check는 통과했다. Playwright 실행은 `NOT_VERIFIED`다.
- Next steps:
  - Browser 연결 후 `corepack pnpm exec playwright test e2e/job-analysis.spec.ts --project=chromium`을 실행한다.

## [2026-08-04] Session Summary (공고 분석 결과 반응형 UI 회귀)

- What was done:
  - `job-analysis.spec.ts`를 5점 단위 표시, AI 요약, 접힌 공고 상세·결과 기록과 새 사용자 문구에 맞췄다.
- Key decisions:
  - 1440·390px에서 의미 기반 locator와 실제 details open 상태, 가로 overflow를 함께 검증한다.
- Issues encountered:
  - None.
- Validation:
  - 집중 Chromium 1/1과 `ui-redesign.visual.spec.ts` 1/1 통과, desktop/mobile full-page 캡처 확인.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 용어·Modal 구조 회귀)

- What was done:
  - `ui-shell.spec.ts`에 이메일 보조 문구 비노출, 비밀번호 세 문장, 동의 상세의 사용자 용어·본문 scroll 구조 회귀를 추가했다.
- Key decisions:
  - 노출 문구는 role·dialog 범위에서 검증하고 없어야 할 전문 용어도 명시적으로 0건임을 확인하도록 했다.
- Issues encountered:
  - 첫 실행은 `안전하게 저장해요`, 두 번째는 `24시간 안에 삭제해요`가 각각 두 요소와 일치해 strict locator 오류로 중단됐다. 두 locator를 정확히 수정했지만 재검증 상한에 따라 세 번째 실행은 하지 않았다.
- Validation:
  - 실패 두 회 모두 화면 snapshot과 목표 문구가 존재했고 제품 오류는 아닌 locator 오류였다. 수정 후 시나리오 완주는 `NOT_VERIFIED`.
- Next steps:
  - 다음 검증 회차에 공개 인증 shell Chromium 1건을 완주한다.

## [2026-08-04] Session Summary (가입 blur·온보딩 자격·마감 시각 반응형 회귀)

- What was done:
  - Signup의 blur red 오류와 수정 즉시 해제, Onboarding 지원 자격 4개 입력, JobNew의 24개 30분 단위 시각 option을 1440·390px에서 검증했다.
  - P5 actual helper를 새 마감 control과 현재 직접 입력 summary 문구에 맞췄다.
- Key decisions:
  - actual scheduler fixture의 시각은 선택 가능한 직전 30분 단위로 내리고 API·scheduler 의미는 유지한다.
- Issues encountered:
  - 인앱 Browser runtime이 비어 있어 저장소 Playwright Chromium으로 검증했다.
- Validation:
  - `ui-shell.spec.ts` 집중 Chromium 2/2와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 동의 Modal 반응형 회귀)

- What was done:
  - 1440·390px 인증 shell 회귀에 실제 비밀번호 안내, 두 상세 Modal의 핵심 내용, ESC·확인 닫기와 trigger focus 복귀를 추가했다.
- Key decisions:
  - role·accessible name 기반 locator로 UI 구현 세부 결합을 줄였다.
- Issues encountered:
  - 인앱 Browser runtime은 사용 가능한 browser를 제공하지 않아 별도 시각 screenshot은 만들지 못했다.
- Validation:
  - `corepack pnpm exec playwright test e2e/ui-shell.spec.ts --project=chromium --grep "public authentication shell"` 1/1 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 중앙 정렬 geometry 회귀)

- What was done:
  - 1440px Dashboard에서 본문 중심과 viewport 중심, 헤더 폭, CTA·본문 우측 경계와 바로가기 우측 배치를 bounding box로 검증했다.
  - 1440·1024·390px screenshot으로 Desktop 우측 레일과 좁은 화면 가로형 바로가기를 직접 확인했다.
- Key decisions:
  - 제품 코드에 test-only selector를 추가하지 않고 기존 Dashboard class와 실제 geometry를 사용한다.
- Issues encountered:
  - `ui-shell.spec.ts` 전체 3건 병렬 실행에서는 별도 프로필 제안 테스트가 `희망 직무` 입력을 찾지 못해 30초 timeout됐고 Dashboard를 포함한 나머지 2건은 통과했다.
- Validation:
  - Dashboard 시나리오 격리 Chromium 1/1과 1920·1440·1280·1024·768·390px overflow 회귀가 통과했다.
- Next steps:
  - 프로필 제안 테스트의 병렬 timeout이 재발하면 별도 범위에서 원인을 조사한다.

## [2026-08-02] Session Summary (Dashboard sticky 바로가기 회귀)

- What was done:
  - Desktop 바로가기의 `sticky` 계산 style과 페이지 하단 스크롤 후 header 아래 추종 위치를 Browser 회귀로 고정했다.
  - 1440·390px Dashboard를 다시 캡처해 workspace 제목과 좁은 화면의 가로형 바로가기를 확인했다.
- Key decisions:
  - viewport 고정 좌표가 아닌 실제 bounding box로 container sticky 동작을 검증한다.
- Issues encountered:
  - None.
- Validation:
  - Chromium `ui-shell.spec.ts` 3/3 통과.
- Next steps:
  - 생성 screenshot은 ignored `test-results`의 로컬 검수 artifact로만 유지한다.

## [2026-08-02] Session Summary (Job header·journey spacing 시각 회귀)

- What was done:
  - `job-analysis.spec.ts`에 공고 제목 35.2px 상한과 Desktop 네 단계 사이 bounding-box gap 균등 assertion을 추가했다.
- Key decisions:
  - 고정 fixture와 실제 computed style·geometry를 사용하고 제품 코드에 test-only selector는 추가하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - Job analysis Chromium 1/1과 전체 화면 visual capture Chromium 1/1 통과.
- Next steps:
  - 생성 screenshot은 ignored `output/playwright/after`의 로컬 검수 artifact로만 유지한다.

## [2026-08-02] Session Summary (Dashboard 반응형·focus·상호작용 회귀)

- What was done:
  - UI shell fixture에 Dashboard·Career Guide 응답, 캘린더 날짜 선택, guide dialog ESC·focus 복귀와 workspace focus style assertion을 추가했다.
  - 1920·1440·1280·1024·768·390px overflow와 1440·1024·390px full-page 시각 상태를 확인했다.
- Key decisions:
  - screenshot은 `output/playwright` 또는 ignored test result 아래에만 두고 운영·외부 서비스는 호출하지 않았다.
- Issues encountered:
  - 첫 1440 capture는 modal trigger focus로 scroll된 상태였고 capture 전 `scrollTo(0,0)`로 검증 artifact만 보정했다.
- Validation:
  - Chromium `landing.spec.ts`·`ui-shell.spec.ts` 10/10 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Landing 카피·무수동 control 회귀)

- What was done:
  - 새 Hero 크기와 네 section heading을 browser에서 확인하고 데모에 수동 pause/play button이 없음을 reduced-motion 회귀에 고정했다.
  - 자동 전환·offscreen 정지·재진입 재개·background 정지·route 이탈 cleanup 검증은 수동 control에 의존하지 않도록 갱신했다.
- Key decisions:
  - 대표 scene screenshot은 semantic scene 변경을 기다린 뒤 transition이 끝난 시점에 캡처하고 test-only 제품 계약은 추가하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - `UI_SCREENSHOTS=true corepack pnpm exec playwright test e2e/landing.spec.ts --project=chromium`: 7/7 통과.
- Next steps:
  - 생성 screenshot은 `output/playwright/landing`의 로컬 검수 artifact로 유지하고 commit하지 않는다.

## [2026-08-02] Session Summary (Landing Hero motion·자동 데모 회귀)

- What was done:
  - 1440px Hero의 정확한 2개 line group과 heading/copy 폭 관계, 스크롤 입력 없는 scene 전환, offscreen 정지·재진입 재개, pause 고정과 route 이탈 cleanup을 추가했다.
  - reduced motion 정적 scene·숨김 없는 section, 1440·390·320px overflow와 Hero·대표 scene screenshot을 검증했다.
- Key decisions:
  - animation millisecond 내부 구현 대신 active scene의 의미 있는 변화와 정지 상태를 기다리고, screenshot 전에는 pause control과 section 최종 reveal 상태를 사용한다.
- Issues encountered:
  - sticky header가 element crop에 겹치고 full-page capture가 observer callback 전에 실행되는 Playwright 특성을 캡처 순간 header 숨김과 `is-revealed` 대기로 안정화했다.
- Validation:
  - `corepack pnpm exec playwright test e2e/landing.spec.ts --project=chromium`: 7/7 통과.
  - `UI_SCREENSHOTS=true`로 `output/playwright/landing`의 desktop/mobile Hero, 대표 scene 2개, reduced-motion static scene을 직접 검수했다.
- Next steps:
  - 생성 screenshot은 로컬 검수 artifact로 유지하고 commit하지 않는다.

## [2026-08-02] Session Summary (공개 Landing·첫 사용 브라우저 회귀)

- What was done:
  - anonymous Landing→login→브랜드 복귀→signup, authenticated `/`의 무깜빡임 dashboard redirect와 보호 route returnTo를 자동화했다.
  - Landing 1440·390·320px overflow·keyboard·anchor·reduced motion과 Dashboard 0/3~3/3 fixture를 추가했다.
- Key decisions:
  - 모든 API는 local route fixture로 차단하고 실제 AI·검색 Provider를 호출하지 않았다.
- Issues encountered:
  - 첫 실행의 CSS duration 표기와 query slash encoding assertion을 브라우저 표현에 독립적으로 보정했다.
- Validation:
  - Landing Chromium 6/6, 기존 UI shell 3/3, 구현 전 visual baseline 1/1과 Landing desktop/mobile·Public login·Dashboard 2/3 캡처 통과.
- Next steps:
  - 생성 screenshot은 `output/playwright/landing`의 검수 artifact로만 유지한다.

## [2026-08-02] Session Summary (자동 분석 journey·전후 화면 캡처)

- What was done:
  - 공고 등록→상세→자동 BALANCED 분석→결과→자기소개서 탭의 browser-only command 0회 흐름과 desktop/mobile keyboard·overflow 회귀를 추가했다.
  - 안전한 동일 fixture로 14개 기존 화면의 전후 28장과 `/guide`를 포함한 변경 후 30장을 캡처한다.
- Key decisions:
  - 제품 asset은 실제 shared component preview로 만들고 Playwright output은 비교 증거로만 관리한다.
- Issues encountered:
  - 390px 탭 bleed, 중복 navigation link, 새 account menu·heading·progress semantics locator를 실제 접근성 scope 기준으로 보정하고 P2 owner 격리 요청에 유효한 `educationLevel`을 명시했다.
- Validation:
  - Agent Run·자동 분석·분석 결과·P2 actual profile·UI shell·visual Chromium 9/9 통과. P4~P8 actual 13건은 전용 환경 flag 부재로 skip.
- Next steps:
  - dedicated backend 환경이 필요한 actual suites는 별도 실행한다.

## [2026-08-01] Session Summary (P5 workflow v2 actual 회귀)

- What was done:
  - P5 Fake Chat input을 v2 `sourceText`로 갱신하고 품질 fixture를 명시했다.
- Key decisions:
  - 실제 Provider/network 없이 기존 등록·추출·manual resume·owner·Scheduler 브라우저 흐름을 유지한다.
- Issues encountered:
  - 1차 URL-only fixture가 운영 품질 임계값에 걸려 test profile threshold를 fixture 길이에 맞게 고정했다.
- Validation:
  - `p5BrowserE2eTest`: Chromium 5/5 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (P5 actual 현재 UI 계약 동기화)

- What was done:
  - P5 actual의 직접 입력 완료·URL 불러오기 성공 문구를 현재 제품 UI와 맞췄다.
- Key decisions:
  - 일시 toast 대신 지속되는 화면 상태와 API 결과를 판정한다.
- Issues encountered:
  - 과거 fixture 문구가 제품 동작과 어긋나 P8.5 전체 회귀에서 발견됐다.
- Validation:
  - P5 Chromium 5/5와 Frontend 60 files/238 tests가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 actual Chromium·DB assertion)

- What was done:
  - preparation→SUFFICIENT source→question→answer v1→실제 409 비교·재적용→version 고정 feedback과 LIMITED/NONE/FAILED·retry·owner/history-delete 분기를 자동화했다.
  - 1440×1000, 390×844와 CDP page scale 200%에서 navigation·focus·Escape·overflow·핵심 action 접근성을 검증했다.
- Key decisions:
  - 격리 PostgreSQL·Spring random port·Fake Chat/Search·Vue dev server·Chromium 1 worker만 사용한다.
- Issues encountered:
  - actual이 source persist 순서와 answer history SQL 공백이라는 두 제품 결함을 재현해 수정 후 회귀로 고정했다.
- Validation:
  - `p8BrowserE2eTest`: Chromium 1/1과 typed provenance·current answer·feedback version·retry lineage·usage·history delete·education evidence DB assertions 통과.
  - 같은 final source의 P7 Chromium 1/1, P6 Chromium 2/2 회귀가 통과했다.
- Next steps:
  - trace·screenshot은 검수용 build 산출물로만 유지하고 커밋하지 않는다.

## [2026-07-30] Session Summary (P7 actual 문항 409 재적용 회귀)

- What was done:
  - 사용자가 편집 중인 문항을 API에서 먼저 변경해 실제 `PUT 409`를 만든 뒤 최신 server 내용·길이·메모와 최초 browser snapshot을 비교하고 명시적 재적용 `PUT 200`을 검증했다.
- Key decisions:
  - 제목 충돌과 별도로 nested question CAS 충돌을 actual Backend·PostgreSQL 환경에서 검증한다.
- Issues encountered:
  - 없음.
- Validation:
  - `p7BrowserE2eTest --rerun-tasks --info --no-daemon --console=plain`: Chromium 1/1, JUnit wrapper·후속 DB assertion PASS, `BUILD SUCCESSFUL in 1m 11s`.
  - 같은 final source의 P6 회귀는 Chromium 2/2와 DB assertion이 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 기다린다.

## [2026-07-30] Session Summary (P7 actual Chromium 전체 시나리오)

- What was done:
  - 가입→근거→공고 분석→자기소개서 생성→문항→partial generation/retry→사용자 저장→검증→복원→최종화·보관과 source deletion·owner isolation을 자동화했다.
- Key decisions:
  - role/label 기반 locator와 mutation별 server/UI postcondition을 사용하고 실제 유료 provider는 호출하지 않는다.
- Issues encountered:
  - number input parser TypeError와 응답 대기 race를 발견해 source 회귀와 명시적 후속 상태 대기로 보정했다.
- Validation:
  - Backend wrapper에서 Chromium 1/1과 후속 DB assertions가 통과했고 1440/390px horizontal overflow가 0이었다.
- Next steps:
  - 독립 validator 판정을 반영한다.

## [2026-07-30] Session Summary (P6 actual Chromium 최종 통과)

- What was done:
  - 수정된 공개 evidence PUT 404 assertion을 포함한 `job-analysis.actual.spec.ts` 전체를 current source에서 실행했다.
- Key decisions:
  - 실제 provider 없이 Backend test-scope Fake Chat·Embedding과 격리 PostgreSQL만 사용했다.
- Issues encountered:
  - 최종 Browser 시나리오 자체에는 문제가 없었고 최초 wrapper 실패는 후속 DB assertion 오타였다.
- Validation:
  - 정상 분석·reuse·OUTDATED·재분석·owner 격리와 근거 부족 실패 시나리오 Chromium 2/2 통과.
- Next steps:
  - P7 actual 시나리오는 P7 수직 기능 구현 뒤 별도 spec과 Backend wrapper로 추가한다.

## [2026-07-29] Session Summary (P6 Job Analysis fixture·actual E2E)

- What was done:
  - 1440px/390px 결과·OUTDATED·history fixture와 Spring/PostgreSQL/Fake AI 주도 actual 분석·reuse·재분석·근거 부족·owner 격리 시나리오를 추가했다.
- Key decisions:
  - 실제 provider·운영 DB를 사용하지 않고 direct career가 만든 VERIFIED evidence와 test-scope Fake Chat/Embedding만 사용한다.
- Issues encountered:
  - actual 1차는 중복 버전 제목 locator, 2차는 공개 계약에 없는 evidence GET assertion에서 종료됐다. assertion은 공개 PUT owner 404로 수정했으나 세 번째 실행은 하지 않았다.
- Validation:
  - P6+Agent Run fixture Chromium 3/3 통과. actual 2차에서 정상 분석·reuse·OUTDATED·재분석과 공고/분석/Run 격리, 근거 부족 실패까지 실행됐지만 wrapper는 evidence assertion 때문에 실패했다.
- Next steps:
  - 수정된 actual evidence PUT 404 assertion을 향후 단일 실행으로 확인한다.

## [2026-07-28] Session Summary (지원 Dashboard·Profile 반응형 시각 회귀)

- What was done:
  - UI shell fixture에 Profile·Document·Job·Agent Run Dashboard 데이터를 추가하고 기존 사용자 Dashboard의 집계·다음 할 일·최근 활동을 검증했다.
  - 1920·1440·1280·1024·768·390px viewport를 고정 검증하고 선택적으로 Dashboard·Profile 1440·390px screenshot을 남기도록 했다.
- Key decisions:
  - 스크린샷은 `test-results`의 비추적 artifact로 유지하고 role·label 기반 assertion을 회귀 기준으로 사용했다.
- Issues encountered:
  - in-app browser 연결은 사용할 수 없어 Playwright Chromium과 생성 screenshot 직접 검토로 대체했다.
- Validation:
  - `UI_SCREENSHOTS=true corepack pnpm exec playwright test e2e/ui-shell.spec.ts --project=chromium --workers=1`: 3/3 통과.
- Next steps:
  - 실제 Backend를 포함하는 Profile·Document·Job actual spec은 이번 UI fixture 변경 뒤 재실행하지 않았다.

## [2026-07-28] Session Summary (통합 프로필·Viewport 시각 회귀)

- What was done:
  - UI shell fixture에 desktop profile outline, mobile selector deep link와 저장 후 다음 section 흐름을 추가했다.
  - 1440×1000, 1024×900, 768×1024, 390×844에서 인증·onboarding·dashboard·profile·documents·jobs·분석 기록·404를 캡처했다.
- Key decisions:
  - 생성 screenshot은 `output/playwright/after` 로컬 artifact로 유지하고 테스트 source에는 안정적인 role·label selector만 남겼다.
- Issues encountered:
  - 실제 P4 E2E는 Backend upload 일반 오류로 첫 test가 240초 timeout됐고 나머지 직렬 test 3개는 실행되지 않았다.
  - Profile actual은 구 온보딩 selector를 한 차례 동기화한 뒤 `100%` text와 progressbar strict locator 중복으로 재검증도 실패했다.
  - P5 actual은 필수 success/empty fixture URL이 없어 실행하지 않았다.
- Validation:
  - fixture `ui-shell` 3/3·`agent-runs` 2/2와 네 viewport overflow 0건; actual Profile 0/1·P4 0/4 완료.
- Next steps:
  - Profile strict locator를 한정하고 Backend 재시작 후 P4 4개, fixture URL 준비 후 P5 5개를 재실행한다.

## [2026-07-28] Session Summary (추천 입력·자료 등록 반응형 회귀)

- What was done:
  - 희망 직무 추천의 키보드 선택과 390px 프로필·자료 등록 overflow, 파일 선택 상태를 UI shell fixture에 추가했다.
  - `AI 작업` selector를 `진행 중인 분석`·`분석 기록` 용어로 갱신했다.
- Key decisions:
  - 외부 추천 API 없이 실제 component interaction과 접근성 role을 검증한다.
- Issues encountered:
  - 실제 P4 E2E의 구 상태·action selector를 새 사용자 용어와 동기화한 뒤 한 차례 재실행했다.
- Validation:
  - UI shell 3/3, Agent Run fixture 2/2와 실제 P4 pipeline 4/4가 Chromium에서 통과했다.
- Next steps:
  - 실제 장문 파일명 조합은 별도 시각 회귀에서 추가 확인한다.

## [2026-07-28] Session Summary (B2C Brand·Copy 반응형 브라우저 검증)

- What was done:
  - UI shell fixture의 accessible name을 새 소비자 용어로 갱신하고 password 표시, reduced-motion, 인증 1440·390px 검증을 보강했다.
  - Playwright CLI로 Before/After 로그인·dashboard를 1440·390px 캡처하고 모든 보호 route와 404를 두 폭에서 직접 진입했다.
- Key decisions:
  - 캡처와 감사 표는 `output/playwright` 로컬 산출물로 보관하고 commit 대상에서 제외한다.
- Issues encountered:
  - 기존 browser session의 auth 상태가 route smoke와 충돌해 새 격리 session에서 mock route를 먼저 설치한 뒤 재실행했다.
- Validation:
  - `agent-runs.spec.ts`, `ui-shell.spec.ts` Chromium 4/4와 전체 route 1440·390px overflow smoke가 통과했다.
  - 인증 DOM의 H1→H2 순서와 password 표시 버튼 44px touch target, AI 작업 화면의 원시 단계 key 미노출을 검증했다.
- Next steps:
  - profile·Document·Job actual spec은 Backend·PostgreSQL·Object Storage가 준비된 환경에서 재실행한다.

## [2026-07-27] Session Summary (Responsive UI Shell 브라우저 검증)

- What was done:
  - 보호 dashboard를 1440·1024·768·390px에서 검사하고 desktop sidebar, mobile drawer, Run drawer와 focus 복원을 자동화했다.
  - login을 1440·390px에서 검사하고 별도 Playwright CLI 캡처로 desktop 인증, desktop app shell, 390px dashboard·drawer를 직접 검수했다.
- Key decisions:
  - fixture는 `/auth/me`와 active Run 목록만 가로채며 제품에 mock 데이터나 fixture 분기를 추가하지 않는다.
  - viewport 검증은 pixel snapshot 대신 overflow·visibility·focus·dialog 경계를 assertion한다.
- Issues encountered:
  - `pnpm test:e2e -- ...` 선택 인자가 실제 spec까지 포함해 timeout되어 `pnpm exec playwright test`에 두 fixture 파일을 직접 전달했다.
- Validation:
  - `agent-runs.spec.ts`와 `ui-shell.spec.ts` Chromium 4/4가 20.3초에 통과했다.
- Next steps:
  - Backend·PostgreSQL·MinIO가 준비된 격리 환경에서 기존 actual spec을 다시 실행한다.

## [2026-07-27] Session Summary (P5 실제 Job pipeline 브라우저 검증)

- What was done:
  - 수동 201 상태 전이·submittedAt 보존, 자동 202 추출, WAITING_USER same-run resume, owner 404와 Scheduler 마감을 추가했다.
- Key decisions:
  - `P5_E2E_ENABLED=true`인 Backend 주도 격리 환경에서만 actual spec을 실행한다.
- Issues encountered:
  - mutation pending 종료와 생성 후 canonical URL query를 명시적으로 기다리도록 locator·wait를 안정화했다.
- Validation:
  - Chromium 5/5가 47초에 통과하고 Backend wrapper DB assertion도 통과했다.
- Next steps:
  - P6 전까지 분석 시나리오를 추가하지 않는다.

## [2026-07-19] Session Summary (P4 실제 Document pipeline 브라우저 검증)

- What was done:
  - 실제 Backend 202·SSE·parse·mask·chunk·Fake embedding·evidence·검토·download·delete를 연결한 4개 시나리오를 추가했다.
- Key decisions:
  - `P4_E2E_ENABLED=true`와 Backend 주도 격리 환경에서만 actual spec을 실행한다.
- Issues encountered:
  - Frontend port 충돌을 validated random port와 `strictPort`로 제거했다.
- Validation:
  - P4 Chromium 4/4, 기존 P3 Chromium 2/2와 전체 7 scenario discovery가 통과했다.
- Next steps:
  - CI remote 실행 결과는 첫 push/PR에서 확인한다.

## [2026-07-19] Session Summary (P3 Agent Run REST·SSE 브라우저 fixture 검증)

- What was done:
  - RUNNING snapshot·progress·step 뒤 강제 단절, 1/2/5초 재연결 실패와 polling terminal 복구를 구현했다.
  - WAITING deep link, FAILED retry header, active cancel version과 logout EventSource 종료를 검증했다.

- Key decisions:
  - fixture는 Playwright route interception에만 있고 production endpoint·bundle에는 포함되지 않는다.

- Issues encountered:
  - 중복 progress text locator를 `progressbar[value]` assertion으로 좁혔다.

- Validation:
  - Chromium workers=1 실행에서 2/2 scenarios가 통과했다.

- Next steps:
  - P4 typed resource가 준비되면 실제 Backend cross-stack Agent Run 여정을 추가한다.

## [2026-07-19] Session Summary (P2 실제 브라우저 Cookie·CSRF 통합 검증)

- What was done:
  - 가입→onboarding→기본 프로필·대표 학력·희망 조건→완료도→새로고침→학력 수정→두 사용자 owner 404→로그아웃·재로그인 흐름을 추가했다.
  - 같은 browser context에서 사용자 전환 뒤 이전 profile cache가 노출되지 않음을 확인했다.

- Key decisions:
  - 기존 개발 DB를 보존하기 위해 V1→V2→V3를 적용한 P2 전용 임시 DB를 만들고 실행 뒤 제거했다.
  - 실제 Cookie·CSRF 실패 순서를 검증해 두 번째 사용자의 CSRF 없는 mutation은 403, 유효 token의 타 사용자 UUID는 404로 확인했다.
  - Playwright spec은 Vitest unit/component 수집 대상에서 명시적으로 제외한다.

- Issues encountered:
  - 첫 실행 전제에서 Windows child process가 `pnpm`을 찾지 못해 webServer 명령을 `corepack pnpm dev`로 보정했다.
  - 첫 실제 E2E는 성공 메시지와 heading의 중복 text locator 때문에 실패해 정확한 heading role로 좁혔다.

- Validation:
  - `corepack pnpm exec playwright test e2e/profile.spec.ts --project=chromium --workers=1 --reporter=line`이 1개 test 통과로 종료됐다.
  - 임시 backend port와 DB 제거를 재확인했으며 실제 외부 AI·검색 provider는 호출하지 않았다.

- Next steps:
  - 후속 phase 핵심 여정은 동일한 격리·실제 브라우저 원칙으로 추가한다.

## [2026-07-17] Session Summary (Playwright E2E 테스트 기반 구성)

- What was done:
  - 당시 구현 상태:
    - `e2e/`에는 `.gitkeep`만 존재하고 Playwright test file은 없다.
    - `playwright.config.ts`는 이 디렉터리를 test directory로 지정하고 Chromium과 Vite web server를 구성한다.
    - 애플리케이션 route와 제품 화면도 아직 구현되지 않아 검증 가능한 사용자 여정이 없다.
  - 완료된 작업:
    - 향후 E2E 테스트를 위한 디렉터리와 Playwright 실행 설정을 준비했다.
    - 작업 목적에 따라 `index.md`와 이 문서를 생성해 빈 테스트 상태와 향후 책임을 명시했다.
  - 당시 진행 중인 작업:
    - 현재 작성 중인 E2E scenario는 없다.
    - E2E 상태를 프론트엔드 문서 계층에 연결하는 작업은 이번 작업에서 완료했다.

- Key decisions:
  - 구현되지 않은 화면을 위한 형식적 placeholder test는 추가하지 않고 실제 사용자 가치가 있는 흐름부터 작성한다.
  - 운영 데이터와 실제 외부 유료 API 대신 격리된 test data와 Fake/Mock을 사용한다.
  - 브라우저 테스트는 `corepack pnpm test:e2e`로 명시적으로 실행하고 unit test 결과와 구분한다.

- Issues encountered:
  - route가 비어 있고 `App.vue`가 `RouterView`만 제공하므로 현재 실행할 실질적인 E2E 시나리오가 없다.
  - Playwright browser binary 설치 여부와 CI E2E 실행은 아직 확인되지 않았다.
  - 기본 `pnpm check`는 `test:e2e`를 호출하지 않는다.

- Validation:
  - 문서 포함 기본 검증 명령: `Set-Location frontend; corepack pnpm check`
  - 향후 E2E 검증 명령: `Set-Location frontend; corepack pnpm test:e2e`
  - 기본 검증은 성공했으며 Markdown format, TypeScript와 production build가 통과했다. Vitest는 test file이 없어 종료 코드 0을 반환했다.
  - E2E 명령은 test file이 없으므로 실행하지 않았다. 따라서 Playwright browser와 실제 사용자 여정은 미검증 상태다.

- Next steps:
  - 인증부터 핵심 취업 준비 흐름까지 페이지 명세의 우선순위에 따른 Playwright scenario 작성
  - 격리된 test account와 seed/cleanup 또는 API mocking 전략 수립
  - CI에서 사용할 browser 설치와 E2E 실행 환경 구성·검증
  - 실패 시 trace, screenshot, report를 활용하는 진단 절차 문서화
