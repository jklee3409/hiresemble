# Progress

## Overview

- `profile.spec.ts`가 실제 Chromium에서 P2 가입·온보딩·프로필·두 사용자 격리·cache cleanup을 검증한다.
- `agent-runs.spec.ts`가 test-local REST/SSE fixture로 P3 reconnect·polling·action cleanup을 검증한다.
- `documents.actual.spec.ts`가 격리 Backend·PostgreSQL·MinIO·Fake AI에서 P4 실제 pipeline 4개를 검증한다.
- `playwright.config.ts`는 `corepack pnpm dev`로 Vite web server를 시작하고 Chromium project를 사용한다.
- 테스트는 외부 provider와 운영 데이터 없이 격리 DB·Object Storage 또는 Playwright route fixture를 사용한다.

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
