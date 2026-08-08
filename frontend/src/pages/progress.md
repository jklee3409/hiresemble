# Progress

## Overview

공개 Landing과 P1 인증부터 P8 Interview preparation·question set·answer feedback, Gate 2 GitHub Source, `/guide`, 현재 route 기반 dashboard와 전용 404를 일관된 제품 UI로 관리한다.

## [2026-08-08] Session Summary (GitHub Source 페이지와 경험 provenance)

- What was done:
  - 공개 URL 등록·참여 확인, source 상태·결과 집계, account repository server 검색·pagination·선택, version 충돌 보존·재확인, focused Run, refresh/delete 화면을 구현했다.
  - 경험 보관함에 GitHub badge·repository count/name·short SHA·captured time·sanitized excerpt·내부/안전한 외부 링크·삭제 tombstone을 추가했다.
- Key decisions:
  - 전체 repository를 memory에 적재하거나 자동 선택하지 않고 선택은 1~10개로 제한한다. raw code·전체 SHA·내부 path는 표시하지 않는다.
- Issues encountered:
  - 기존 경험 submit test의 비동기 button 탐색을 form submit으로 안정화했다.
- Validation:
  - page/component targeted test와 전체 `corepack pnpm check` 80 files/369 tests 통과. focused Playwright는 최종 delete locator 재확인이 남았다.
- Next steps:
  - 수정된 `alertdialog` locator로 browser delete 완료를 재확인한다.

## [2026-08-07] Session Summary (경험 보관함 카드에서 바로 처리하기)

- What was done:
  - 페이지 설명 줄이 아래 섹션과 같은 폭까지 늘어나도록 `page-header__body`·`page-description`의 `max-width`를 이 화면에서만 풀었다.
  - "같은 경험은 새 카드로 반복하지 않아요" 안내 aside를 없애고, 이어지는 요소들의 위 여백을 `--layout-heading-content-gap`(2rem)에서 `--space-5`(1.25rem)로 줄였다.
  - 카드의 "상세와 출처" 버튼을 없애고 그 자리에 활용 승인·활용 제외(또는 다시 검토)·수정 버튼을 뒀다. 수정은 별도 영역을 열지 않고 카드 본문이 그대로 입력 폼으로 바뀐다.
  - 문서 출처 칸이 개수 대신 실제 문서 이름을 보여 준다. 여러 문서에서 나온 경험은 `가장 먼저 추출한 문서 외 N곳`으로 적고, 이름 칸만 두 배 폭을 줘 한 줄로 줄인다.
  - 목록을 10개에서 5개씩으로 바꿨다.
- Key decisions:
  - 상세 패널은 지우지 않고 역할만 좁혔다. 유사 경험 비교·합치기와 출처 목록은 여기에만 있으므로, `reviewRequired` 카드의 안내 줄에 "비교해서 확인" 링크를 남겨 진입로를 유지했다. 버튼만 지우면 V26 중복 판정 흐름에 도달할 수 없다.
  - 편집 상태는 카드별 `editingId` 하나로 관리하고, 편집 중에는 제목 heading과 상단 동작 버튼을 감춰 같은 값이 두 번 보이지 않게 했다.
  - 문서 이름은 프런트에서 문서 목록을 다시 부르지 않고 서버가 목록 DTO에 실어 준 `primaryDocumentName`을 그대로 쓴다.
- Issues encountered:
  - Node 20 환경이라 `vitest`를 실행하지 못했다. 변경 전 다른 test 파일에서도 같은 오류라 이번 변경과 무관한 환경 제약이다.
- Validation:
  - `eslint .`, `prettier --check .`, `vue-tsc -b --force`, `vite build` 통과.
  - fixture로 1440·390px 화면과 카드 내 편집 상태를 직접 확인했다. 좌우 넘침 없음.
  - `ExperienceLibraryPage.test.ts`에 카드 내 승인·편집과 문서 이름 표시 검증을 추가했지만 위 환경 제약으로 실행하지 못했다.
- Next steps:
  - Node 22 이상에서 `corepack pnpm check`를 재실행한다.

## [2026-08-07] Session Summary (경험 보관함 목록·상세·유사 경험 검토)

- What was done:
  - `ExperienceLibraryPage.vue`에 canonical 경험 목록, 상태·유사도 filter, pagination, inline 상세·편집·검증과 최초/보강 출처 목록을 구현했다.
  - `RELATED_DIFFERENT|CONFLICT`는 제안된 기존 경험과 나란히 비교하고 별도 유지 또는 병합하도록 했다.
  - page/component 회귀로 canonical 카드 1개, 비교·별도 유지, 문서 보강 출처의 비검토 표현을 고정했다.
- Key decisions:
  - 기존 profile 화면처럼 desktop outline/mobile selector와 scoped soft-surface card를 사용하고 새 전역 style token은 추가하지 않았다.
- Issues encountered:
  - 브라우저 CLI fixture 주입이 syntax error로 중단되어 실제 viewport 확인은 미완료다.
- Validation:
  - 집중 Vitest와 Frontend 전체 `pnpm check` 70 files/317 tests·production build 통과.
- Next steps:
  - 실제 인증 fixture에서 desktop/mobile 핵심 흐름을 확인한다.

## [2026-08-07] Session Summary (요건 매칭 현황 막대 파스텔·직사각형화)

- What was done:
  - 요건 매칭 현황의 알약 막대를 `border-radius: 0.25rem` 직사각형에 가깝게 바꾸고, 윗면 흰 gradient(`::after`)를 없앴다.
  - 막대 채움을 새 파스텔 `--chart-*`로 바꾸고 `--chart-*-line` 1px 안쪽 윤곽을 넣어 흰 면 위에서 경계가 유지되게 했다.
  - 파스텔 위에서 보이지 않는 흰 아이콘을 없애고, 범례 dot과 조건별 결과 mark의 아이콘 색을 `--analysis-match-strong`으로 바꿨다. 두 mark에도 같은 1px 윤곽을 넣고 모서리를 `--radius-sm`으로 줄였다.
  - 0건 상태의 범례 dot은 채움면·윤곽·아이콘을 모두 muted 계열로 눌렀다.
- Key decisions:
  - 파스텔은 채움만으로 대비 3:1을 못 넘기므로 색을 옅게 두는 대신 윤곽선이 형태를 지탱하게 했다. 무늬를 추가하지 않는 기존 결정은 유지한다.
  - 조건별 결과 mark는 같은 색 토큰을 공유하므로 함께 고쳤다. 안 고치면 파스텔 위 흰 아이콘이 사라진다.
- Issues encountered:
  - None.
- Validation:
  - `eslint .`, `prettier --check .`, `vue-tsc -b --force`, `vite build`와 `job-analysis.spec.ts` 포함 Chromium 4건 통과. 병행 중인 experience library 작업 파일을 stash한 상태에서 검증해 이번 변경만 확인했다.
  - 네 상태를 모두 담은 fixture로 1440px 화면을 직접 확인했다.
- Next steps:
  - None.

## [2026-08-07] Session Summary (작성 도움 접기 버튼 제거)

- What was done:
  - `CoverLetterEditPage`에서 "작성 도움 접기" 버튼과 `assistCollapsed` 상태를 없앴다. 작성 도움은 항상 보이며 노출 여부를 사용자가 고르지 않는다.
  - 버튼이 차지하던 줄만큼 tab과 내용이 위로 올라가 편집기 상단과 같은 높이에서 시작한다.
- Key decisions:
  - `assistLayout`은 이제 tab만 보고 `normal`/`wide`를 정한다. `collapsed` 상태는 남기지 않았다.
- Issues encountered:
  - None.
- Validation:
  - `eslint .`, `prettier --check .`, `vue-tsc -b --force`, `vite build` 통과. `cover-letter-review.spec.ts`가 접기 버튼이 없고 작성 도움 열 상단이 편집기 상단과 4px 이내로 맞는지 검증한다.
- Next steps:
  - None.

## [2026-08-07] Session Summary (자기소개서 AI 검토 열 넓히기)

- What was done:
  - `CoverLetterEditPage`의 작업 영역이 `data-assist-layout`으로 오른쪽 열 폭을 바꾸도록 했다. "AI 검토 결과" tab에서는 `minmax(16rem, 19rem)`에서 `minmax(22rem, 28rem)`으로 넓어지고, "공고 요구사항"으로 돌아가면 다시 좁아진다.
  - 목록 화면의 검토 상태 badge tone을 공용 `VERIFICATION_STATUS_TONES`로 바꿔 편집 화면과 색을 맞췄다.
- Key decisions:
  - 별도 modal을 띄우지 않는다. 편집기를 가리면 검토 내용을 보면서 고칠 수 없기 때문이다. 대신 같은 자리의 열만 넓혀 편집기와 나란히 둔다.
  - 폭은 tab에 따라 자동으로 바뀐다. 공고 요구사항은 짧은 목록이라 좁은 열로 충분하고, 검토 결과만 인용문·제안이 붙어 넓은 폭이 필요하다.
  - 상한을 28rem으로 둔 이유는 1200px에서도 편집기가 약 600px를 유지해 그대로 쓸 수 있기 때문이다. 74rem 미만에서는 기존대로 열이 sheet로 내려간다.
- Issues encountered:
  - None.
- Validation:
  - `eslint .`, `prettier --check .`, `vue-tsc -b --force`, `vite build`와 신규 `cover-letter-review.spec.ts` Chromium 1건 통과. 1200·1440·1920px에서 좌우 넘침 없이 편집기와 검토 결과가 함께 보이는 것을 직접 확인했다.
- Next steps:
  - None.

## [2026-08-07] Session Summary (자료 미리보기를 원본 페이지 단위로 전환)

- What was done:
  - `DocumentDetailPage`의 "자료 미리보기"가 PDF일 때 추출 텍스트 대신 `DocumentPagePreview`로 원본을 한 페이지씩 보여 주게 했다. 비PDF와 렌더링 실패는 기존 텍스트 미리보기로 되돌리고 이유를 한 줄로 알린다.
- Key decisions:
  - 되돌아갈 조건을 `mimeType`과 자식이 올린 `unavailable` 이벤트 두 가지로만 두고, 문서를 바꿔 이동하면 상태를 초기화한다.
- Issues encountered:
  - None.
- Validation:
  - `eslint .`, `prettier --check .`, `vue-tsc -b --force`, `vite build`와 `document-preview.spec.ts` Chromium 2건 통과. `pdfjs-dist`는 임시로 풀어 검증했고 정식 설치는 후속 작업이다.
- Next steps:
  - Node 22 이상에서 `corepack pnpm install`로 `pnpm-lock.yaml`을 갱신한다.

## [2026-08-07] Session Summary (취업 준비 가이드 포스트 재설계)

- What was done:
  - Dashboard 취업 준비 가이드 카드에서 아이콘 타일과 모서리 번호 배지를 걷어내고, 분류 태그 + 분량 → 큰 제목 → 요약 → 얇은 선 아래 "읽어보기" 순으로 읽히는 글 중심 표지로 바꿨다. 카드 오른쪽 위에는 순번을 큰 배경 숫자로 흐리게 깔았다.
  - 카드 배경을 흰 면 + 그림자에서 `--color-fill` 채움면으로 바꾸고, hover에서 흰 면 + `--shadow-lift`로 떠오르게 했다. 카드 격자는 `grid` 5칸에서 `flex-wrap`으로 바꿔 마지막 줄에 빈칸이 남지 않게 했다.
  - 가이드 카드와 요약 카드의 hover 상단 색 띠(`::before`·`::after`의 `scaleX` 전개)를 모두 제거했다.
  - 가이드 본문에서 문단마다 붙던 점과 문단 사이 구분선을 없애고 읽는 글의 여백만 남겼다. 모든 줄이 `- `로 시작하는 문단은 `ul` 체크리스트 상자로 렌더링한다.
  - 본문 글자 수로 대략적인 분량(분)을 계산해 카드와 modal 하단에 표시하고, modal 하단의 내부값 "콘텐츠 v{version}" 노출을 게시 날짜로 바꿨다.
  - `GUIDE_CATEGORY_ICONS`에 실제 사용 중인 분류(경험 정리·강점 선택·최종 점검)를 추가해 index 기반 fallback 아이콘이 뜨지 않게 했다.
- Key decisions:
  - 카드 개수가 격자 열 수로 나누어떨어지지 않아도 빈칸이 생기지 않도록 `flex: 1 1 16rem`으로 마지막 줄을 늘리고, 대신 제목·요약에 `max-width`를 줘 넓어진 카드에서 글줄이 과하게 길어지지 않게 했다.
  - 분량 표시는 공백 제외 350자/분 기준의 어림값이며, 서버 계약을 늘리지 않고 기존 `body`로만 계산한다.
- Issues encountered:
  - Node 20 환경이라 `vitest`가 `html-encoding-sniffer`의 `require(ESM)`에서 실패해 실행하지 못했다. 변경 전 다른 test 파일에서도 같은 오류가 나 이번 변경과 무관한 환경 제약임을 확인했다.
- Validation:
  - `eslint .`, `prettier --check .`, `vue-tsc -b --force`, `vite build` 통과.
  - `ui-shell.spec.ts`의 protected app shell Chromium 1건 통과. 1440·1024·390px Dashboard와 가이드 modal 화면을 직접 확인했다.
  - `DashboardPage.test.ts`의 modal 검증을 체크리스트 `li` 3개와 분량 문구 기준으로 갱신했지만 위 환경 제약으로 실행하지 못했다.
- Next steps:
  - Node 22 이상에서 `corepack pnpm check`를 재실행한다.

## [2026-08-07] Session Summary (현재 화면 기준 공고·Dashboard 회귀 계약 보정)

- What was done:
  - 공고 Overview unit test가 header Teleport 안의 즉시 상태 변경·편집·재시도·삭제 동작을 검증하도록 mount를 보정했다.
  - Dashboard quick entry와 1px 캘린더 격자 geometry에 맞춰 UI shell E2E locator·간격 기대를 갱신했다.
- Key decisions:
  - 제거된 제출 이력 보조 배지 대신 기본 정보의 `최초 서류 제출` 시각을 검증하고 현재 UI를 page 명세의 기준으로 확정했다.
- Issues encountered:
  - 프로필·자료 Chromium 흐름은 `/profile/basic` 진입 후 인증 fixture가 `/login`으로 이동해 combobox 대기에서 timeout됐다.
- Validation:
  - `jobPages.test.ts` 9건과 전체 Frontend check 69 files/313 tests·production build 통과.
  - 격리 Dashboard Chromium은 통과했고 프로필·자료 Chromium 1건은 위 인증 timeout으로 미통과했다.
- Next steps:
  - 인증 fixture 안정화 후 프로필·자료 Chromium 흐름을 재실행한다.

## [2026-08-06] Session Summary (캘린더 마감 표시를 알약으로 교체)

- What was done:
  - 캘린더 칸의 마감 건수 표시에서 왼쪽 색 띠(`border-left: 3px`)와 칸 전체를 채우던 가로 막대를 없앴다.
  - 대신 칸 왼쪽 아래에 작은 알약 하나를 두고, 앞의 점이 긴급도를 색으로, 숫자가 건수를 글자로 전하게 했다. 선택한 날짜에서는 알약이 brand 채움으로 바뀐다.
- Key decisions:
  - 색 띠를 없애도 긴급도가 사라지지 않도록 점과 알약 배경에 기존 tone(위험·주의·기본)을 그대로 이어 썼다.
  - 알약은 칸 폭을 채우지 않고 내용 크기만 차지하게 해 격자가 비어 보이도록 두었다.
- Issues encountered:
  - None.
- Validation:
  - `vite build`, `eslint .`, `prettier --check` 통과. fixture로 렌더한 캘린더에서 일반·임박·선택 상태를 직접 확인했다.
- Next steps:
  - None.

## [2026-08-06] Session Summary (Dashboard 마감 보정과 공고 카드 재설계)

- What was done:
  - 프로필 카드 재작업 때 함께 지워졌던 Dashboard 공용 카드 표면 규칙(`.career-card`·`.deadline-section`·`.dashboard-section`·`.workspace-note`·`.guide-section`의 모서리·배경·그림자)을 되살렸다. 마감 캘린더·최근 활동·취업 준비 가이드·준비 워크스페이스가 각지고 배경이 사라져 보이던 원인이다.
  - 프로필 카드 본문에 `position: relative; z-index: 1`을 줘 아바타가 그라데이션 띠에 가려 잘리던 문제를 고쳤다. 띠 높이를 낮추고(최대 9rem → 6.5rem) 아바타 크기·겹침도 함께 줄였다.
  - 세 칸 구분선이 들쭉날쭉하지 않도록 카드 본문을 `align-items: stretch`로 바꾸고, 중복 선언된 CTA 규칙을 정리했다.
  - 캘린더 칸 높이를 5.25rem → 4.5rem으로 낮추고, 선택 날짜 패널이 캘린더와 같은 높이를 쓰도록 `align-items: stretch`로 바꿨다. 마감이 없는 날의 안내는 남는 면 한가운데로 모았다.
  - 캘린더 우측 공고 카드에서 왼쪽 색 띠(`::before`)를 없앴다. 흰 면 + 옅은 그림자 카드로 바꾸고, 마감 임박은 D-day 알약 색이 알리며, "공고 상세" 링크는 얇은 구분선 아래 한 줄로 내렸다.
- Key decisions:
  - 색 띠를 없애도 정보가 사라지지 않도록 D-day 알약(색+숫자)과 상태 칩을 카드 위쪽에 남겼다.
  - 카드 배경을 채움면에서 흰 면 + 그림자로 바꿔 다른 화면의 카드와 같은 표면 언어를 쓰게 했다.
- Issues encountered:
  - `pnpm check`를 돌릴 수 없어 Playwright를 임시 설정으로 직접 실행해 Dashboard를 렌더하고 화면을 눈으로 확인한 뒤 여백·높이를 조정했다. 확인용 파일은 작업 후 삭제했다.
- Validation:
  - `vite build`, `eslint .`, `prettier --check` 통과. fixture로 렌더한 Dashboard 화면을 직접 확인했다. Node 20 환경이라 `vitest`와 저장소 Playwright suite는 실행하지 못했다.
- Next steps:
  - Node 22 이상에서 `corepack pnpm check`와 Playwright E2E를 재실행한다.

## [2026-08-06] Session Summary (프로필 카드·마감 캘린더·요건 매칭 그래프 재설계)

- What was done:
  - Dashboard hero에서 "다음 할 일" 카드를 없애고 프로필 카드가 그 폭까지 차지하게 했다. 카드는 위쪽 aurora 띠 + 걸친 아바타 + 세 칸 본문(누구인가 / 무엇을 원하는가 / 얼마나 준비됐는가) 구조다. 띠는 brand blue를 중심에 두고 보라·시안으로 번지는 다중 radial gradient로 만들었고, 아바타는 사진 대신 기존 `person-card` 아이콘을 쓴다.
  - 카드 안쪽 글자를 흰색 계열에서 ink 계열로 전부 바꾸고, 준비도 막대는 brand→시안 gradient, 완료 항목은 success tone으로 정리했다.
  - 마감 캘린더를 레퍼런스의 격자형으로 바꿨다. 컨테이너 배경 + 1px gap으로 격자선을 만들고, 날짜 숫자는 칸 오른쪽 위, 마감 건수는 칸 아래 가로 막대(왼쪽 색 띠로 긴급도 표시), 다른 달 칸은 빗금으로 눌렀다. 월 이동 버튼은 달 이름 양옆 원형 버튼으로 옮겼다. 날짜 클릭 → 우측 목록 동작과 건수 표시는 그대로다.
  - 공고 분석의 "요건 매칭 현황"을 요건 1개 = 캡슐 1개인 알약 띠로 바꿨다. 캡슐은 항상 일치 → 일부 일치 → 확인되지 않음 → 판단 정보 부족 순으로 정렬하고, 무늬(빗금·점)는 모두 뺐다. 위에 "N / M개 요건이 내 정보와 일치해요 + 비율"을 큰 숫자로 얹고, 범례는 아이콘·라벨·개수를 담은 칩 4개로 다시 만들었다.
- Key decisions:
  - 캡슐에서 무늬를 뺀 대신 고정 정렬 순서를 2차 인코딩으로 삼았다. 색을 구분하지 못해도 위치와 범례(아이콘·한글 라벨·개수)로 네 상태를 읽을 수 있다.
  - 요건 색은 기존 CVD 검증을 통과한 네 hue를 유지하고 명도만 나눠 진한 톤(텍스트)·옅은 톤(칩 배경)을 추가했다. 레퍼런스의 라임·핑크·주황을 그대로 쓰면 검증된 색 구분과 blue 테마가 함께 깨지므로 채택하지 않았다.
  - "다음 할 일"을 없애며 `nextTasks`와 그 계산에만 쓰이던 `documentNeedsAction`·`waitingRuns`도 함께 지웠다.
- Issues encountered:
  - None.
- Validation:
  - `vite build`, `eslint .`, `prettier --check` 통과. Node 20 환경이라 `vitest`와 Playwright는 실행하지 못했다.
- Next steps:
  - Node 22 이상에서 `corepack pnpm check`와 Playwright E2E를 재실행한다.

## [2026-08-06] Session Summary (화면 제목 줄 정리와 공고·분석 화면 조정)

- What was done:
  - Dashboard, 프로필 기본 정보, 이력서·자료, 관심 공고, 자기소개서 목록, 면접 준비 목록에서 제목·설명 줄을 화면에서 걷어내고 `sr-only` 제목만 남겼다. 상단 탐색이 이미 같은 이름을 보여 주기 때문이다. Dashboard의 자료·공고 등록 동작은 `.dashboard-quick-entry`, 관심 공고의 공고 등록은 상태 tab과 같은 줄(`.jobs-page__bar`)로 옮겼다.
  - 목록 화면 컨테이너를 grid + gap으로 바꿔 필터와 목록·빈 상태 사이 여백을 일정하게 만들었다. 면접 준비에서 필터와 목록이 붙어 보이던 문제도 여기서 해결된다.
  - Dashboard 세로 리듬을 줄였다(전체 gap 2~~3.5rem → 1.25~~1.75rem, 섹션 gap 2~~3.5rem → 1.5~~2.25rem).
  - 공고 정보 화면에서 "공고 본문 · 불러오기 완료" 배지 줄과 항상 떠 있던 안내 알림을 없앴다. 배지는 공고 본문 카드 머리로 옮기고, 안내는 `NEEDS_MANUAL_INPUT`·`FAILED`처럼 조치가 필요할 때만 띄운다.
  - 지원 상태 변경을 별도 form에서 빼내 편집 버튼 왼쪽의 select 하나로 바꾸고, 고르는 즉시 저장하도록 했다.
  - 공고 분석의 "분석 결과 기록"에서 좌측 목록과 이력 pagination을 없애고 추이 그래프와 현재 분석 결과만 남겼다. 링크 문구는 "이 결과가 만들어진 과정 보기" → "분석 과정"으로 줄였다.
  - 이력서·자료의 등록 안내 제목을 "경험을 보여줄 자료를 등록해 주세요."로 바꾸고, 설명 문구는 64rem 이상에서 한 줄로 읽히게 했다.
- Key decisions:
  - 제목을 삭제하지 않고 `sr-only`로 남겨 `aria-labelledby` 계약과 낭독기 사용자의 영역 인식을 유지했다.
  - 공고 본문 상태 배지는 `data-testid="job-extraction-status"`를 유지한 채 위치만 옮겨 기존 e2e 기대를 깨지 않았다.
  - 분석 이력 목록 제거는 과거 결과 열람을 줄이는 변경이라 추이 그래프에 과거 점수가 남는지 확인한 뒤 진행했다.
- Issues encountered:
  - 화면이 바뀌면서 기대가 어긋난 test를 함께 고쳤다. `DashboardPage.test.ts`(제목 가시성), `JobAnalysisPage.test.ts`(이력 선택), `landing.spec.ts`·`ui-shell.spec.ts`(제목 가시성), `jobs.actual.spec.ts`(상태 변경 절차), `cover-letter.actual.spec.ts`(버튼 문구).
- Validation:
  - `vite build`, `eslint .`, `prettier --check` 통과. Node 20 환경이라 `vitest`와 Playwright는 실행하지 못했다.
- Next steps:
  - Node 22 이상에서 `corepack pnpm check`와 Playwright E2E를 재실행한다.

## [2026-08-06] Session Summary (공고·자기소개서 화면 중복 제목 제거)

- What was done:
  - `JobOverviewPage`에서 "공고 정보" 제목과 설명 줄을 없애고, 편집·본문 직접 입력·다시 불러오기·삭제 버튼을 `Teleport`로 공고 상세 header의 `#job-detail-actions`(원본 공고 보기 아래)로 옮겼다. 제목 줄이 쓰던 세로 공간도 함께 제거했다.
  - `JobCoverLetterPage`에서 "자기소개서" 제목과 설명 줄을 없앴다. 탭이 이미 같은 이름을 보여 주므로 접근성용 제목만 `sr-only`로 남기고, 카드 간격은 컨테이너 grid gap으로 대체했다.
- Key decisions:
  - 제목을 완전히 삭제하지 않고 `aria-labelledby`가 가리키는 `sr-only` 제목으로 남겨 화면 낭독기 사용자에게는 영역 이름이 계속 전달되게 했다.
  - 동작 버튼을 layout으로 옮기지 않고 Teleport만 사용해 편집 form의 상태 관리 위치를 바꾸지 않았다.
- Issues encountered:
  - None.
- Validation:
  - `vite build`, `eslint .`, `prettier --check` 통과.
- Next steps:
  - None.

## [2026-08-06] Session Summary (주요 화면 soft surface 적용)

- What was done:
  - Dashboard: career/priority/deadline/activity/guide 카드에서 외곽 테두리를 없애고 `--radius-xl` + `--shadow-panel`로 띄웠다. 캘린더는 칸 테두리를 없애고 마감 tone을 채움면으로, 선택 날짜를 inset ring으로 표현했다. 월 이동 control, 캘린더 요일 줄, 바로가기 목록을 알약 형태로 정리했다.
  - 이력서·자료: `DocumentListPage`의 dropzone을 큰 점선 사각형과 가운데 떠 있는 원형 아이콘(호버 시 확대)으로 바꾸고, 선택한 파일 카드를 아이콘 tile + 그림자 카드로 다시 그렸다. `DocumentDetailPage`의 상태 상자와 본문 미리보기를 채움면으로 바꿨다.
  - 공고: `JobNewPage`의 단계 카드를 그림자 카드로 바꾸고 1단계에만 brand 띠와 채워진 번호 tile을 남겼다. `JobListPage`의 tab 묶음·기간 선택 trigger를 알약으로, dropdown menu를 무테두리 부동 panel로 바꿨다.
  - 자기소개서: 목록 카드에 hover elevation을 주고, 편집 화면의 소재 선택 panel과 TipTap editor 외곽을 무테두리 + 큰 모서리로 정리했다.
  - 그 외 Guide, Interview, AgentRun, Onboarding, Profile 화면의 카드·경고 패널·입력을 같은 규칙(외곽 테두리 제거, 경고류는 inset ring)으로 맞췄다.
  - `.data-list`가 이제 카드 사이에 간격을 두므로 `StructuredProfilePage`의 경력 timeline rail을 간격만큼 연장해 끊기지 않게 했다.
- Key decisions:
  - 마크업과 class 이름은 유지하고 style만 바꿔 기존 단위·컴포넌트 테스트 selector를 보존했다.
  - 이미 채움면 체계로 만들어져 있던 공고 분석 화면(`JobAnalysisPage`)은 내부 구분선 위주라 구조를 건드리지 않고 전역 token 변경만 반영되게 두었다.
- Issues encountered:
  - None.
- Validation:
  - `vite build` 성공, `prettier --check` 통과. Node 20 환경이라 `vitest`는 실행하지 못했다.
- Next steps:
  - None.

## [2026-08-06] Session Summary (완료 배너 숨김·소재 펼침 영역·작성 도움 높이)

- What was done:
  - 끝난 AI 작업의 상태 bar를 편집 화면에서 제거했다. 진행 중일 때만 한 줄로 노출하고 실패 결과는 toast로 알린다. 결과 알림은 이 화면에서 진행을 지켜본 작업에만 띄운다.
  - 상단 주요 행동 button(`AI 초안 만들기`·`AI 검토 받기` 등) 바로 아래에 `답변에 사용할 소재` button과 겹쳐 펼쳐지는 선택 영역을 추가했다. 열려도 아래 내용이 밀리지 않고 바깥 클릭·Escape·문항 이동으로 닫힌다.
  - 우측 작성 도움에서 소재 tab을 없애고 `공고 요구사항`·`AI 검토 결과`만 남겼으며, 열 높이를 편집 영역과 같게 맞춰 아래로 더 내려가지 않게 했다.
- Key decisions:
  - 작성 도움 열은 내용을 흐름에서 빼고 `align-self: stretch`로 채워 grid 행 높이를 늘리지 않는다.
- Issues encountered:
  - jsdom은 scoped style을 적용하지 않아 겹침 여부는 DOM 구조로 검증했다.
- Validation:
  - 컨테이너(node:24)에서 eslint, prettier, `vue-tsc -b --force`, Vitest 69 files/310 tests, `vite build` 통과.
- Next steps:
  - 브라우저에서 펼침 영역의 겹침·스크롤을 확인한다.

## [2026-08-06] Session Summary (편집 페이지 model 선택 연결)

- What was done: 자기소개서 편집 페이지가 model catalog를 조회해 추천값을 초기화하고 선택 모델로 생성·검증을 접수하도록 연결했다.
- Key decisions: 선택 모델은 요청마다 독립적으로 전달하고 화면의 품질 모드 표현을 제거한다.
- Issues encountered: None.
- Validation: page test를 포함한 Frontend `pnpm check` 69 files·308 tests 통과.
- Next steps: 실제 Chromium 반응형 회귀를 실행한다.

## [2026-08-06] Session Summary (편집 화면 한 화면 배치와 번호 전용 문항 rail)

- What was done:
  - 답변 편집 본문 높이를 `clamp(12rem, calc(100dvh - 28rem), 40rem)`로 두고 내부 스크롤을 사용해, 문항·글자 수·저장 button이 한 화면에 남도록 했다. 본문 글자 크기는 1rem에서 0.8125rem으로 줄였다.
  - 좌측 문항 rail을 번호만 남긴 2.75rem strip으로 바꾸고 상태는 색 점과 접근 가능한 이름으로 전달한다. 좁은 화면 sheet는 질문 preview까지 보여 주는 `list` variant를 쓴다.
  - 답변 영역 상단 질문 제목을 `1.` 번호 + 본문 크기(0.875rem)로 낮추고 메모도 작게 줄였다.
  - 동시에 진행된 `qualityMode → model` API 계약 변경에 맞춰 page component test의 생성·검토 요청 assertion과 AI 모델 query mock을 갱신했다.
- Key decisions:
  - rail은 번호만 남기되 상태 정보를 잃지 않도록 색 점 + `aria-label`·`title`에 상태 문구를 함께 넣는다.
- Issues encountered:
  - 재렌더 뒤 오래된 DOMWrapper로 `setValue`를 호출하면 조용히 무시돼, 모델 select는 매번 다시 조회하도록 test를 고쳤다.
- Validation:
  - 컨테이너(node:24)에서 eslint, prettier, Vitest 69 files/307 tests, `vite build` 통과. `vue-tsc`는 동시 변경 중인 `shared/api/coverLetterApi.test.ts`의 `qualityMode` 잔여 3건만 실패한다.
- Next steps:
  - 위 typecheck 잔여 항목은 API 계약을 바꾼 작업에서 정리해야 한다.

## [2026-08-06] Session Summary (편집 화면 진행 표시·스크롤·소재 tab 보정)

- What was done:
  - AI 작업 진행 표시를 한 줄 요약 disclosure로 줄이고 연결 상태·문항 결과·상세 link는 펼쳤을 때만 보이게 했다. 재시도가 필요한 문항이 있으면 기본 펼침이다.
  - 상단 상태·행동 영역의 `position: sticky`를 없애 본문과 함께 스크롤되도록 하고, 문항 목록·작성 도움의 sticky 기준을 전역 header 바로 아래로 낮췄다.
  - 작성 도움을 `쓸 소재 / 공고 요구사항 / AI 검토 결과` 3 tab으로 나눴다. 소재 tab은 선택 개수·모두 해제·아직 쓰지 않은 소재·이미 쓴 소재를 구분하고, 확인한 경험이 없으면 자료 등록 경로를 준다.
- Key decisions:
  - 기본 tab은 작성 중 가장 자주 쓰는 `쓸 소재`로 둔다. 공고 요구사항은 읽기 정보라 선택 UI와 섞지 않는다.
- Issues encountered:
  - None.
- Validation:
  - 컨테이너(node:24)에서 eslint, prettier, `vue-tsc -b --force`, Vitest 69 files/307 tests, `vite build`가 통과했다.
- Next steps:
  - 브라우저 시각 확인은 여전히 미수행이다.

## [2026-08-06] Session Summary (자기소개서 편집 페이지 UI/UX 재설계)

- What was done:
  - `CoverLetterEditPage.vue`를 상단 고정 상태 영역과 문항 목록·답변 편집기·작성 도움 3열 작업 화면으로 다시 구성했다. AI 코치 5단계 panel, 참고 자료 3열 dropdown, 상시 노출 AI 설정, 하단 저장 기록·마지막 점검 section을 제거했다.
  - 상태에서 유도한 단일 primary 행동만 강조하고, 미저장 상태에서는 강조를 편집기 옆 `답변 저장`으로 넘긴다. 나머지 행동은 secondary·tertiary로 낮췄다.
  - AI 설정·버전 기록·작성 완료 점검·문항 추가·문항 수정을 focus 가둠과 Escape 닫기를 갖춘 sheet로 옮겼다.
  - 완료 안내를 toast로 바꾸고, 저장하지 않은 답변이 있을 때 문항 이동과 페이지 이탈에 확인을 받도록 했다.
  - page component test를 새 구조·흐름 기준으로 다시 쓰고 P7 actual E2E 선택자와 문구를 맞췄다.
- Key decisions:
  - API·DTO·mutation 계약과 409 비교·복구 흐름은 그대로 두고 표시 위치와 강조만 바꿨다.
  - 상태 판정은 `features/cover-letters/editorFlow.ts`가 소유하고 page는 결과만 사용한다.
- Issues encountered:
  - `onBeforeRouteLeave`는 page를 직접 mount하는 component test에서 경고만 남기고 동작하지 않아 문항 이동 확인으로 검증했다.
- Validation:
  - 컨테이너(node:24)에서 eslint, prettier, `vue-tsc -b --force`, Vitest 69 files/307 tests, `vite build`가 통과했다.
- Next steps:
  - 브라우저 시각 회귀(`ui-redesign.visual.spec.ts`)와 P7 actual E2E는 재실행하지 않았다.

## [2026-08-06] Session Summary (자기소개서 참고 자료 드롭다운 복원)

- What was done:
  - `CoverLetterEditPage.vue`의 공고 요구사항·강점과 보완점·쓸 경험 영역을 각각 기본 접힘 `details/summary`로 복원했다.
  - summary의 번호·제목·한 줄 미리보기·개수와 펼침 indicator를 유지하고, 펼친 본문은 최대 22rem 내부 scroll을 사용한다.
  - page component test가 세 dropdown의 기본 접힘과 summary 클릭 펼침을 검증하도록 갱신했다.
- Key decisions:
  - 참고 자료는 문항보다 먼저 배치하되 사용자가 필요한 항목만 펼쳐 화면 높이를 제어한다.
- Issues encountered:
  - None.
- Validation:
  - 집중 Vitest와 Frontend 전체 check, Chromium 1440px·390px fixture에서 기본 접힘·반응형 배치를 확인했다.
- Next steps:
  - None.

## [2026-08-06] Session Summary (자기소개서 소재 우선 작성 흐름)

- What was done:
  - 참고 자료 세 영역을 문항 bar와 작성 workspace 위로 옮기고 접힘 없이 직접 보이되 내부 scroll로 높이를 제한했다.
  - `AI에게 맡길 문항과 방식`을 `AI 설정`으로 바꾸고 대상 문항·작성 방식·문항별 별도 경험 선택을 compact checkbox/radio로 항상 표시했다. 문항 tab의 화면 text는 `N번`만 남겼다.
- Key decisions:
  - 전체 질문은 `aria-label`에 유지하고 질문 선택·초안 생성·저장 동작은 변경하지 않는다.
- Issues encountered:
  - 모바일에서 tab이 세로로 과도하게 늘어나 flex container 폭 규칙을 추가했다.
- Validation:
  - component test와 Frontend 전체 `check` 통과. Chromium에서 참고 자료가 문항보다 먼저 나오고 375px에서 horizontal overflow 없이 약 56×40px tab으로 표시됨을 확인했다.
- Next steps:
  - None.

## [2026-08-06] Session Summary (자기소개서 편집 page test 정합성 보정)

- What was done:
  - `CoverLetterEditPage.test.ts`의 미저장 안내 기대값을 현재 사용자 문구로 바꾸고, 과거 저장본 선택을 화면의 `1번째` label에 맞췄다.
- Key decisions:
  - 사용자 문구와 저장 기록 순번을 기준으로 검증하되 답변 복원·최종 완료 동작 계약은 유지한다.
- Issues encountered:
  - 최초 Frontend 전체 검사에서 과거 `서버 미저장`·`v1` selector 때문에 test 2개가 실패했다.
- Validation:
  - 집중 Vitest와 최종 `corepack pnpm check`가 통과했다.
- Next steps:
  - 실제 Backend를 사용하는 `cover-letter.actual.spec.ts`는 이번 commit 준비 과정에서 재실행하지 않았다.

## [2026-08-05] Session Summary (자기소개서 작성 화면 tab 폭·참고 자료 표현 보정)

- What was done:
  - 선택 문항 제목이 오른쪽 여백을 남기지 않도록 `max-width: 48rem`을 없애고 제목 열에 `flex: 1 1 20rem`과 `min-width: 0`을 줬다.
  - `공고가 원하는 것` 목록을 disc bullet(`rail-list`)에서 강점 목록과 같은 칸 디자인(`insight-list`)으로 바꾸고 brand tone check icon을 붙였다. 사용하지 않게 된 `.rail-list`는 제거했다.
  - `쓸 경험 고르기`의 `공고와 맞는 경험 N개 담기` 일괄 담기 button과 관련 computed·handler를 제거했다. 선택 해제만 남는다.
  - 문항 tab을 `width: 15.5rem` 고정에서 `flex: 1 1 15.5rem` + `min-width: 15.5rem`으로 바꿔 문항 수가 적으면 남는 폭을 나눠 갖고, 많아지면 기존처럼 가로 scroll한다. 좁은 화면 기준값도 `min-width`·`flex-basis` 13.5rem으로 맞췄다.
- Key decisions:
  - 추천 경험은 목록 정렬과 `공고와 맞아요` 배지로만 알려 주고 일괄 선택은 제공하지 않는다.
  - 참고 자료 세 dropdown은 같은 목록 표현을 공유해 시각 규칙을 하나로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - `eslint src`, `prettier`, `vue-tsc -b --force`, `vite build` 통과.
  - `vite preview` + Playwright Chromium fixture로 1440px 캡처해 제목 폭과 단일 문항 tab 확장을 확인했다. 토큰 예산 때문에 이번에는 추가 상태 캡처와 Vitest는 실행하지 않았다.
- Next steps:
  - Node 24 환경에서 `corepack pnpm check`와 `cover-letter.actual.spec.ts` 실행.

## [2026-08-05] Session Summary (자기소개서 작성 화면 가로 문항 tab 전환과 문구 검수)

- What was done:
  - 좁아서 읽기 어렵던 좌측 문항 rail을 없애고 상단 가로 문항 tab(`role="tablist"`, roving tabindex, Arrow·Home·End 이동)으로 바꿨다. 선택 문항 작업대가 `role="tabpanel"`이 되고 답변 영역은 전체 폭을 쓴다.
  - `AI 초안 받기`·`이 답변 검토받기`를 tab bar 우측으로 올리고, 대상 문항·작성 방식·경험 중복 최소화는 같은 줄의 접힌 `AI에게 맡길 문항과 방식` disclosure로 옮겼다.
  - 세로로 길던 오른쪽 rail을 `1 공고가 원하는 것 · 2 내 강점과 보완할 점 · 3 쓸 경험 고르기` 3열 dropdown으로 바꾸고 각 summary에 한 줄 미리보기와 개수를 붙였다. 검증 결과는 편집기 아래 전체 폭 `AI 코치의 검토 결과`로 옮겼다.
  - 문항 순서 변경을 선택 문항의 `앞으로 이동`·`뒤로 이동`으로 바꾸고, 문항 목록 대신 tab에서 상태 badge와 글자 수를 보여 준다.
  - `COVER_LETTER_GENERATION` run 진행 중에는 편집기를 읽기 전용으로 두고 저장·제안 적용을 막으며 안내 문구를 표시하는 `generationInProgress`·`answerLocked`를 추가했다.
  - 화면 문구 전반을 사용자 언어로 고쳤다. `최종화 확인 → 제출 전 마지막 점검`, `자기소개서 최종화 → 작성 완료로 표시하기`, `답변 대기 → 작성 전`, `새 버전 저장 → 저장하기`, `ARCHIVED · 읽기 전용 → 보관된 자기소개서예요 · 읽기 전용`, `DRAFT로 복구 → 다시 쓰기`, `승인한/승인된 경험 → 확인한 경험`, `409 버전 충돌 → 다른 곳에서 먼저 저장됐어요`로 바꾸고 임시 저장 안내를 `아직 저장하지 않았어요` 계열로 통일했다.
  - 공용 label(`presentation.ts`)의 `최종화 → 작성 완료`, `검증 중 → 검토 중`, `통과 → 문제없음`, `검증 실패 → 수정 필요`, `사용자 저장 → 내가 쓴 글`, `과거 버전 복원 → 되돌린 내용`과 issue code 문구를 고치고 `CoverLetterListPage.vue`의 `DRAFT로 복구`도 `다시 쓰기`로 맞췄다.
- Key decisions:
  - 문항 원문 가독성이 목록 밀도보다 중요하므로 tab 폭을 15.5rem로 두고 넘치면 가로 scroll한다. 좁은 화면에서는 tab만 가로 scroll하고 실행 button은 2열 grid로 접는다.
  - 참고 자료는 항상 펼쳐 두지 않고 summary의 미리보기·개수로 요약한 뒤 필요할 때만 펼친다.
  - 편집 잠금은 workflow type이 초안 생성일 때만 적용하고, 검토 run은 편집을 막지 않는다.
  - enum 이름과 내부 용어는 사용자 문구에서 제거하되 API 계약·상태 전이는 그대로 둔다.
- Issues encountered:
  - `.question-bar`가 grid container라 자식의 기본 `min-width: auto`가 tab 목록의 max-content를 그대로 밀어 올려 1440·390px 모두에서 카드가 넘쳤다. 실제 Chromium에서 `scrollWidth` 680 / 카드 366을 측정해 원인을 찾고 `min-width: 0`으로 고쳤다.
  - 좁은 화면에서 `flex-direction: column`에 `flex-wrap: wrap`이 남아 항목이 옆 column으로 넘어가 가로 scroll이 생겨 `nowrap`으로 고정했다.
  - `getByText('쓸 경험 고르기')`가 코치 단계 label과 dropdown summary 양쪽에 걸려 실제 E2E helper를 `.reference-card:has(.evidence-options) > summary`처럼 범위를 좁힌 selector로 바꿨다.
  - 편집 중이 아닐 때 brief 글자 수가 편집기 footer와 1자 어긋나 `displayedCharacterCount`로 서버 값을 함께 쓰도록 맞췄다.
- Validation:
  - `eslint .`, `prettier --check .`, `vue-tsc -b --force`, `vite build` 통과.
  - `vite preview` + Playwright Chromium fixture로 `ui-redesign.visual.spec.ts`·`landing.spec.ts`·`ui-shell.spec.ts` 12건 중 10건 통과. 실패 2건은 이 변경과 무관하다. `ui-shell`의 `profile suggestions…`는 기존 기록에 있는 실패이고, `landing`의 skip link Tab focus는 `pnpm dev` 대신 production preview에서 실행한 환경 차이로 재현된다.
  - 임시 fixture spec으로 문항 0개·문항 추가 form·문항 3개 혼합·참고 자료 펼침·초안 생성 중 잠금·390px 화면을 캡처해 확인한 뒤 임시 spec을 삭제했다.
  - Vitest는 로컬 Node 20.18.0 제약으로 여전히 미실행이다. `CoverLetterEditPage.test.ts`의 selector(`.question-tab`)와 문구 단언, `CoverLetterListPage.test.ts`의 `다시 쓰기`를 함께 고쳤으나 실행 확인은 Node 24 환경에서 필요하다.
- Next steps:
  - Node 24 환경에서 `corepack pnpm check`와 `cover-letter.actual.spec.ts`를 실행해 단위·실제 흐름 회귀를 확인한다.

## [2026-08-05] Session Summary (자기소개서 작성 화면 협업 시나리오 개편)

- What was done:
  - `CoverLetterEditPage.vue`의 `PageHeader`를 공고·제목·상태·답변/검증 진행률을 함께 보여 주는 전용 작업 header로 바꾸고 제목 편집을 `제목 수정` disclosure form으로 분리했다.
  - 상태에서 유도한 단일 다음 행동(`첫 문항 등록하기`·`새 버전으로 저장`·`남은 문항 초안 만들기`·`이 문항 검증하기`·`자기소개서 최종화`·`DRAFT로 되돌리기`)과 `문항 등록 → 강점·소재 고르기 → AI 초안 받기 → 내 문장으로 다듬기 → 검증하고 최종화` 5단계를 가진 AI 코치 panel을 추가했다.
  - 문항 등록 form을 좁은 좌측 rail에서 중앙 열의 `새 문항 등록` panel로 옮기고 500/700/1000/1500자 preset과 안내 문구를 붙였다. 문항 내용·글자 수·메모 수정은 선택 문항 Brief 안의 `question-meta` disclosure로 접었다.
  - 오른쪽 rail을 `1 공고가 원하는 것 → 2 내 강점과 보완점 → 3 쓸 소재 고르기 → 4 AI에게 초안 맡기기 → 5 검증 결과` 번호 순서로 재구성하고, 강점·보완점은 최신 공고 분석의 `strengths`·`gaps`, 소재는 `matchedEvidenceRefs` 기준 추천 정렬·`공고와 연결됨` 배지·본문 미리보기·일괄 담기/해제를 사용한다.
  - 문항 목록에 검증·작성 출처 기반 상태 badge와 글자 수를, 선택 문항에 메모·글자 수 meter를, 최종화 section에 문항별 확인 목록을 추가했다.
  - `CoverLetterRunMonitor.vue`를 상태별 코치 문장과 avatar를 가진 panel로, `CoverLetterTipTapEditor.vue`를 읽기 폭 46rem·1.85 행간 문서 면으로 다듬고 `--color-focus-ring`(미정의) focus box-shadow를 `--focus-ring`으로 고쳤다.
- Key decisions:
  - 코치 문장·단계·추천은 `GET /cover-letters/:id`, 최신 공고 분석, 확인한 경험, Agent Run 응답에서만 유도하고 새 API·새 서버 상태를 만들지 않는다.
  - 화면의 primary button은 코치 panel 하나만 갖고 기존 생성·검증·최종화 button은 보조 위계와 기존 `data-testid`를 유지한다.
  - 5단계는 앞 단계를 건너뛴 완료 표시가 나오지 않도록 순서대로 누적 판정한다. 소재 선택은 선택 0개여도 초안이 있으면 완료로 본다.
  - 오른쪽 rail은 section이 길어 내부 scroll을 만들지 않고 페이지 흐름을 따르며 문항 Navigator만 sticky로 둔다.
  - 기존 DOM 계약(`.question-add`, `.question-meta`, `.question-meta__form`, `.question-list__select`, `.evidence-options`, `.generation-questions`, `.finalization__warnings`, 답변 버전 listbox, 6개 `data-testid`, `ARCHIVED · 읽기 전용`·`브라우저 임시 저장됨 · 서버 미저장` 문구)을 유지했다.
- Issues encountered:
  - 문항 등록 form이 13rem 좌측 rail에서 잘려 실제 Chromium 캡처로 확인한 뒤 중앙 열 panel로 옮겼다.
  - 문항 설정을 disclosure로 접으면서 실제 backend E2E(`cover-letter.actual.spec.ts`)의 `.question-meta` 입력과 제목 입력이 보이지 않게 되어 `openQuestionSettings` helper와 `제목 수정` 클릭을 추가했다.
  - 로컬 Node가 20.18.0이라 `pnpm`과 `vitest`(jsdom `html-encoding-sniffer`의 `ERR_REQUIRE_ESM`)를 실행할 수 없어 Vitest 단언은 이번 세션에서 미검증이다. `CoverLetterEditPage.test.ts` fixture에 `strengths`·`gaps`·`matchedEvidenceRefs`·evidence `content`를 추가했으나 실행 확인은 다음 Node 24 환경에서 필요하다.
- Validation:
  - `eslint src e2e`, `prettier --check .`, `vue-tsc -b --force`, `vite build` 통과.
  - `vite preview` + Playwright Chromium fixture로 `ui-redesign.visual.spec.ts`를 1440·390px에서 실행해 회귀 없이 캡처했고, 임시 fixture spec으로 문항 0개·등록 form·문항 3개 혼합 상태·ARCHIVED 읽기 전용을 확인한 뒤 임시 spec을 삭제했다.
  - Vitest와 실제 backend E2E는 미실행이다.
- Next steps:
  - Node 24 환경에서 `corepack pnpm check`와 `cover-letter.actual.spec.ts`를 실행해 단위·실제 흐름 회귀를 확인한다.

## [2026-08-05] Session Summary (공고 기간 filter summary 말줄임 보정)

- What was done:
  - `JobListPage.vue`의 기간 filter와 summary에 `min-width: 0`·가변 너비를 적용해 좁은 화면에서 선택 label이 filter 영역을 밀어내지 않고 말줄임되도록 했다.
- Key decisions:
  - 기간 선택 동작과 표시 문구는 유지하고 layout 제약만 보정한다.
- Issues encountered:
  - summary 내부 icon의 `span`까지 기존 말줄임 selector가 적용될 수 있어 직접 자식 text `span`으로 범위를 좁혔다.
- Validation:
  - Node 24에서 Frontend `corepack pnpm check`: lint·format·typecheck, Vitest 67 files/284 tests, production build 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (내 지원 정보 목록·기본 정보 화면 정보 위계 개편)

- What was done:
  - `StructuredProfilePage.vue`에 `resourceIcon`·`resourceBadges`·`resourceFacts`·`resourceNotes`와 `formatDay`·`periodText`·`isExpired`를 추가하고, 제목·부제만 있던 행을 icon tile, 상태 배지, 사실 목록(기간·학점·자격번호·유효기간), 서술형 설명(역할·성과·설명)을 가진 카드로 재구성했다.
  - `resourceSubtitle`에서 날짜와 상태를 빼 배지·사실 목록과의 중복을 없앴다.
  - 경력 timeline rail과 marker를 목록 바깥 `border-left`에서 카드 안쪽 icon 열로 옮겨 `.data-list`의 `overflow: hidden`에 잘려 반쪽 원(`)`)으로 보이던 문제를 없앴다.
  - `ol` 기본 marker를 제거하고 도구 영역을 `등록 N건`(서버 `totalElements`)과 정렬로 나누며 `정렬` label이 글자 단위로 접히지 않게 고정했다.
  - `ProfileBasicPage.vue`의 미완료 안내를 warning surface 카드로 바꾸고 남은 항목 배지에 `남은 항목` label을 붙였으며, 네 form section 제목에 icon tile을 추가했다.
- Key decisions:
  - 표시 값은 모두 기존 DTO 필드에서만 유도하고 새 API·계약·추정 상태를 만들지 않는다.
  - `유효기간 지남` 판정은 서울 자정 기준 `expiresAt` 비교로만 하고 서버가 주지 않는 상태를 만들지 않는다.
  - 기존 class·DOM 계약(`.structured-item`, `.structured-list--timeline`, `.profile-outline__link` 텍스트, `최종 학력` 문구)을 유지했다.
- Issues encountered:
  - 임시 fixture 초안이 실제 DTO와 다른 field명을 써서 목록이 비어 보였다. `contracts.ts` 기준으로 `admissionDate`·`isPrimary`·`organization`·`isCurrent`·`acquiredDate`·`testedAt`·`organizer`와 `/profile/language-scores` 경로로 맞춘 뒤 재확인했다.
  - `e2e/ui-shell.spec.ts`의 `profile suggestions...`는 stash 후 clean tree에서도 같은 `희망 직무` combobox 지점에서 실패해 기존 실패로 재확인했다.
- Validation:
  - Node 24에서 `corepack pnpm check`: lint·format·typecheck, Vitest 67 files/284 tests, production build 통과.
  - Chromium `e2e/profile.spec.ts` 통과, `e2e/ui-shell.spec.ts`는 위의 기존 실패 1건 외 통과.
  - 임시 fixture spec으로 7개 프로필 화면을 1440·390px에서 캡처해 배치를 확인한 뒤 spec을 삭제했다.
- Next steps:
  - 기존 `profile suggestions` E2E 실패 원인 조사.

## [2026-08-05] Session Summary (Dashboard 시각 위계·D-day·동적 효과 개편)

- What was done:
  - 요약 4장을 `summaryCards` computed 기반 `v-for`로 바꾸고 서버 값에서 유도한 보조 문구(`등록한 공고 N건 중`, `분석 중 N건`)와 tone별 아이콘 면, hover elevation, 상단 accent bar, `requestAnimationFrame` count-up을 추가했다.
  - 마감 캘린더에 D-day 계산(`daysUntil`·`ddayLabel`·`deadlineTone`)을 추가해 셀과 선택 날짜 항목에 3일 이내 danger, 7일 이내 warning, 그 밖 brand tone을 적용하고 grid 아래 legend를 붙였다.
  - `다음 할 일` 카드 하단에 공고 등록·자료 등록·자기소개서·면접 준비 `빠른 실행` 4개 링크를 고정해 할 일이 적을 때 생기던 빈 면을 없앴다.
  - 최근 활동 행에 자료·공고·AI 작업 아이콘 면과 hover 배경을 추가하고, 가이드 카드 아이콘을 `index` 하드코딩에서 `post.category` 매핑으로 바꿨다.
  - 커리어 카드 sheen, 준비도 track fill, 섹션 진입 stagger(0~240ms)를 추가하고 관련 hover·animation을 `prefers-reduced-motion: reduce`에서 모두 정지시켰다.
- Key decisions:
  - 점핏·커리어마이징의 D-day 배지와 카드 hover 관용구를 참고하되 색은 기존 Hiresemble Blue와 semantic token만 사용하고 새 hue를 만들지 않는다.
  - 조회 실패는 그대로 `—`와 확인 실패 문구를 유지하고 count-up 대상에서 제외해 실패를 0으로 계산하지 않는다.
  - 기존 DOM 계약(`.dashboard-title__name`, `.summary-grid`, `.calendar-day--sunday|saturday`, `.deadline-detail--desktop`, `.guide-card`, 바로가기 anchor 4개)을 유지해 화면 계약과 단언 의미를 바꾸지 않았다.
- Issues encountered:
  - 할 일 항목을 남는 높이만큼 늘리자 내용 대비 빈 상자로 보여, 목록은 자연 높이를 유지하고 `빠른 실행`만 카드 하단에 고정하는 방식으로 되돌렸다.
  - 로컬 Node가 20.18.0이라 `pnpm`과 `vitest`가 실행되지 않는다(`ERR_REQUIRE_ESM`, `node:sqlite`). Vitest 단언은 이번 세션에서 미검증이다.
  - `e2e/ui-shell.spec.ts`의 `profile suggestions...` 1건은 stash 후 clean tree에서도 같은 지점에서 실패해 이번 변경과 무관한 기존 실패로 확인했다.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `prettier --check`, `vite build` 통과.
  - Chromium `e2e/ui-shell.spec.ts`·`e2e/landing.spec.ts` 11건 중 10건 통과, 1건은 위의 기존 실패.
  - 임시 fixture spec으로 1440·390px Dashboard와 마감 섹션을 캡처해 배치·D-day 배지를 확인한 뒤 spec을 삭제했다.
- Next steps:
  - Node 22 이상 환경에서 `corepack pnpm check`로 Vitest 단언을 재확인한다.

## [2026-08-05] Session Summary (공고 기간 반기 label 색상 보정)

- What was done:
  - `JobListPage` 기간 filter의 `상반기`, `하반기` label에서 brand 색상을 제거했다.
- Key decisions:
  - 기본 text 색상을 사용하면서 기존 글자 크기와 800 굵기는 유지한다.
- Issues encountered:
  - None.
- Validation:
  - `corepack pnpm check`
  - `corepack pnpm exec prettier --check src/pages/JobListPage.vue progress.md src/pages/progress.md`
  - `git diff --check`
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 기간 dropdown compact 보정)

- What was done:
  - dropdown 폭·행 높이·padding을 줄이고 custom 기간을 grid로 바꿔 `~ 오늘` 기준선을 맞췄다.
- Key decisions:
  - 상반기·하반기만 brand 강조하고 날짜 범위는 작은 보조 정보로 유지한다.
- Issues encountered:
  - 기존 flex 배치에서는 date control 최소 폭 때문에 `오늘`이 글자 단위로 줄바꿈됐다.
- Validation:
  - page component 9 tests와 Frontend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 목록 기간 dropdown)

- What was done:
  - 첨부 이미지 형태의 pill summary·기간 menu를 구현하고 추출 상태·마감 범위·임박 filter를 제거했다.
- Key decisions:
  - 목록 응답에 존재하는 owner 기간만 행으로 표시하며 직접 설정은 date 하나와 `~ 오늘`로 표현한다.
- Issues encountered:
  - 마감 임박순 정렬은 필터 삭제 범위가 아니므로 유지하고 테스트는 filter label만 검사한다.
- Validation:
  - Job page component test와 Frontend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석 모바일 판단·차트 재우선순위)

- What was done:
  - 모바일 hero를 104px 단일 적합도 gauge와 결정 문장, 커버리지·분석 시각 meta로 압축하고 desktop 지원 가능성·커버리지 tile을 숨겼다.
  - primary CTA를 첫 viewport 전폭 action으로 유지하고 category chart 기본 접힘, 상태 legend 4행, 강점·보완 첫 항목 disclosure를 적용했다.
- Key decisions:
  - desktop의 216px 2중 ring과 전체 비교 정보는 유지하고 48rem 이하에서만 정보 우선순위를 바꾼다.
- Issues encountered:
  - 기존 모바일 E2E의 세 metric 844px 계약이 가이드와 충돌했다.
- Validation:
  - Frontend check 67 files/282 tests·production build와 관련 Chromium 2/2 통과.
- Next steps:
  - None.

## [2026-08-05] Session Summary (공고 분석 결과 화면 디자인 가이드 구현)

- What was done:
  - `JobAnalysisPage.vue`의 결과 영역을 카드 적층에서 단일 report surface로 재구성했다. 내부 블록은 1px 구분선과 여백으로만 나누고 그림자는 패널에 1회만 적용한다.
  - 적합도를 270° 2중 링 게이지(외부 `fitScore`, 내부 `analysisCoverage`)로 표현하고, 요건 분포는 2px 간격·직접 개수 라벨·상태별 텍스처를 가진 100% 누적 막대로, 카테고리 충족도는 단일 hue 가로 막대로, 분석 이력은 `analysisVersion` 정수 축 추이 라인 차트로 바꿨다.
  - 조건 행에 상태 marker와 score/weight meter를 추가하고, 강점·보완 항목에 의미 있는 icon을 붙였다. filter는 채움 pill로 교체했다.
  - `<style scoped>` 블록에 누적돼 있던 3세대 중복 CSS(약 3,260줄)를 단일 구현(약 1,260줄)으로 대체했다.
- Key decisions:
  - 기존 class 이름과 DOM 계약(`analysis-result__metrics > div` 3개, `abbr[title]`, `analysis-insight li > p`, `analysis-criterion`, pagination `aria-label` 등)을 유지해 화면 계약과 테스트 단언의 의미를 바꾸지 않았다. script setup의 상태·query·mutation·watch 로직은 그대로 두고 게이지·추이 계산 computed만 추가했다.
  - `fitScore`가 `null`이면 게이지를 렌더하지 않고 "산정하지 못함" 문구만 남긴다. 0점으로 그리지 않는다.
  - 이력이 1건이면 추이 차트를 렌더하지 않는다.
  - 막대 길이는 반올림하지 않은 비율을 쓰고 라벨만 기존 `roundToFive` 계약을 따른다.
- Issues encountered:
  - `animation-fill-mode: both`와 `from` 키프레임만 선언한 조합에서 게이지 아크와 카테고리 막대가 종료 후에도 0 상태로 고정됐다. `backwards`로 바꾸고 아크 전체 길이를 element별 CSS 변수로 분리해 해결했다.
  - `.analysis-breakdown__filter--active`가 `.analysis-breakdown__filters button`보다 specificity가 낮아 선택 상태가 적용되지 않았다. 선택자를 결합해 해결했다.
  - `minmax(19rem, 1fr)` grid가 320px에서 컨테이너를 넘어 가로 스크롤을 만들었다. `minmax(min(19rem, 100%), 1fr)`로 해결했다.
- Validation:
  - `vue-tsc -b --force`, `eslint .`, `prettier --check .`, `vite build` 통과.
  - build 산출 CSS로 결과 화면 DOM을 렌더해 computed style을 검증했다. panel radius 20px·shadow 1회, 게이지 dashoffset 107.44/43.83(70%/85%), 카테고리 막대 transform none(최종 상태 표시), 선택 filter `#0f1420`/흰 텍스트, PARTIAL 세그먼트 45° 텍스처, 320/375/768/1180/1440px 전부 가로 스크롤 0을 확인했다.
  - `vitest`는 실행하지 못했다. 이 저장소는 Node 24를 요구하는데 실행 환경이 Node 20이라 `corepack pnpm`이 `node:sqlite` 부재로 기동하지 않고, 로컬 `vitest` 직접 실행도 jsdom 의존 `html-encoding-sniffer`의 `ERR_REQUIRE_ESM`으로 실패한다. 변경하지 않은 `src/features/jobs/filters.test.ts`에서도 동일하게 실패해 환경 문제임을 확인했다.
- Next steps:
  - Node 24 환경에서 `corepack pnpm check`와 `frontend/e2e/job-analysis.spec.ts`를 실행해 회귀를 확인한다.

## [2026-08-04] Session Summary (공고 분석 판단·근거 정보 밀도 보정)

- What was done:
  - 최신 결과를 동적 판단 heading, 실제 summary, 적합도·지원 가능성·커버리지 행과 자기소개서 CTA로 압축하고 OUTDATED reason을 기본 접힘 disclosure로 변경했다.
  - 하위 요건·공고 핵심·강점/보완·근거·criterion·history를 반복 카드 대신 section divider와 compact row로 표시했다.
- Key decisions:
  - 점수와 분석 데이터는 변환하지 않고 기존 5점 단위 표시만 유지한다. 결과가 있을 때 재분석은 상단 보조 action으로 제공하며 하단 중복 command는 제거한다.
- Issues encountered:
  - 초기 mobile geometry에서 마지막 metric이 첫 viewport 밖이었고 mobile 전용 spacing 보정으로 해결했다.
- Validation:
  - `JobAnalysisPage.test.ts` 포함 집중 Vitest와 전체 unit 282건, desktop/mobile Chromium·visual capture, type/lint/format/build 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Landing·공고 분석 결과 presentation 완성)

- What was done:
  - Landing hero에 떠 있는 product signal과 journey flow를 추가하고 demo scene 전환에 진행 상태·depth 효과를 연결했다.
  - 공고 분석 결과에 시각적 판단 요약, 구분된 section, 5개 단위 criterion pagination, 회전 indicator disclosure와 visible keyboard focus를 적용했다.
- Key decisions:
  - chart는 API 원값을 바꾸지 않는 SVG/CSS presentation으로 만들고 service blue와 의미 색상 token을 사용한다. filter 변경 시 첫 페이지로 돌아가며 데이터 축소 시 유효한 마지막 페이지로 보정한다.
- Issues encountered:
  - 인앱 browser 부재는 Playwright CLI 실제 Chromium fallback으로 보완했고 외부 reference의 scroll 장면과 반복 animation timing을 확인했다.
- Validation:
  - page 집중 Vitest 20건, type check, 실제 Chromium 1440·390px와 reduced-motion·overflow·pagination·2열 geometry 검증 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 결과 판단·다음 행동 중심 재구성)

- What was done:
  - `JobAnalysisPage`의 중복 제목과 상단 재분석 card를 제거하고 결과 hero에 적합도·지원 가능 여부·커버리지와 자기소개서 primary CTA를 통합했다.
  - 요건 분포·category 점수·공고 핵심·강점/보완·활용 경험·조건 근거를 하나의 report 안의 compact row와 구분선 목록으로 바꿨다.
- Key decisions:
  - 성공·주의색은 강점/보완의 작은 상단선과 실제 상태 badge에만 사용한다. Mobile은 결과 요약 2열, 상태 filter horizontal scroll과 접힌 상세로 desktop과 다른 밀도를 사용한다.
- Issues encountered:
  - 변경 후 Browser visual 검증은 browser unavailable로 미실행이다.
- Validation:
  - `JobAnalysisPage.test.ts`와 `jobPages.test.ts` 18건, type check, Frontend 전체 check·281 tests·build가 통과했다.
- Next steps:
  - 390px에서 긴 criterion·secondary link wrapping과 full-page 높이를 시각 확인한다.

## [2026-08-04] Session Summary (공고 분석 결과 요약·필터·인사이트 UI)

- What was done:
  - `JobAnalysisPage`의 매칭 보드에 padding을 보강하고 공고 추출 목록을 AI 핵심 요약과 세 개의 접힌 상세로 바꿨다.
  - 강점·보완점은 번호형 insight panel로, 기준 결과는 상태 filter와 친화적 상세 문구로, 이력은 날짜 중심의 접힌 card로 재구성했다.
- Key decisions:
  - 저장된 `analysisSummary`의 제거 요청 문장은 presentation에서 숨기고 나머지 AI 요약은 보존한다.
  - 숫자 version은 사용자 주 제목에서 제외하고 현재 결과·분석 시각으로 식별한다.
- Issues encountered:
  - None.
- Validation:
  - 집중 Vitest 9/9, Frontend 전체 check와 Chromium desktop/mobile 회귀 통과.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 시각 요약·접이식 근거)

- What was done:
  - `JobAnalysisPage`에 coverage metric, match count cards, category progress bars, source item count와 접이식 판정 근거를 추가했다.
- Key decisions:
  - 요건 원문은 세 section card로 유지하고 세부 설명은 필요할 때 펼쳐 읽게 한다.
- Issues encountered:
  - 긴 criterion 설명이 항상 펼쳐져 핵심 분포보다 텍스트가 먼저 보였다.
- Validation:
  - Frontend 전체 67 files/281 tests·production build와 최종 Job Analysis 9 tests/type check 통과.
- Next steps:
  - 실제 장문 공고의 모바일 레이아웃을 확인한다.

## [2026-08-04] Session Summary (공고 분석 최신 Run 표시 우선순위 수정)

- What was done:
  - `JobAnalysisPage`가 최초 자동 분석 Run보다 `queuedAt,desc`로 조회한 최신 `JOB_ANALYSIS` Run을 우선해 상태와 상세 link를 표시하도록 수정했다.
  - 최신 성공 Run과 과거 실패 자동 Run이 함께 있는 회귀 fixture를 추가했다.
- Key decisions:
  - 방금 접수한 local Run, 서버 최신 Run, 최초 자동 Run 순서만 사용하며 별도 상태 복제나 추가 API를 만들지 않는다.
- Issues encountered:
  - 기존 ID 우선순위가 최초 자동 Run을 서버 최신 Run보다 먼저 선택해 성공 결과와 과거 실패 카드가 동시에 노출됐다.
- Validation:
  - Job Analysis 집중 Vitest 9건과 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 실패 카드 재실행 버튼 보완)

- What was done:
  - `JobAnalysisPage`의 terminal 실패 카드에 `공고 분석 재실행` 버튼을 항상 표시하도록 보완했다.
  - 범용 retry 불가 Run은 현재 공고 version과 `forceReanalyze=true`로 새 `BALANCED` 분석을 요청하고, 실패 카드가 보일 때 하단 중복 분석 command는 숨긴다.
- Key decisions:
  - 서버가 허용한 generic retry는 기존 lineage를 유지하고, 그 외 실패의 재실행은 최신 resource snapshot을 사용하는 명시적 분석 요청으로 분리한다.
- Issues encountered:
  - 기존 구현은 `retryable=false`인 실패에서 버튼을 렌더링하지 않아 실제 첨부 화면에 행동 경로가 없었다.
- Validation:
  - Job Analysis 집중 Vitest 8건과 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 사용자 문구·동의 상세 레이아웃)

- What was done:
  - `SignupPage`에서 이메일 형식 보조 문구를 제거하고 비밀번호 안내를 요청된 세 문장과 두 개의 충족 표시로 간소화했다.
  - 서비스·AI 동의 상세를 한눈에 보기, 번호 상세 카드, 강조 안내, 고정 footer 구조로 재설계했다.
- Key decisions:
  - desktop은 중앙 dialog, mobile은 bottom sheet를 유지하되 Modal 전체 대신 본문만 scroll하도록 했다. 상세 확인은 checkbox를 자동 선택하지 않는다.
- Issues encountered:
  - 인앱 Browser가 없어 저장소 Chromium 회귀로 대체했고, 중복 문구 locator 두 건을 보정했으나 재검증 상한 때문에 보정 후 완주는 확인하지 못했다.
- Validation:
  - `authFlow.test.ts` 포함 집중 Vitest 20건과 Frontend 전체 check·build가 통과했다. Chromium 최종 완주는 `NOT_VERIFIED`.
- Next steps:
  - 수정된 공개 인증 shell Chromium 회귀를 다음 회차에 확인한다.

## [2026-08-04] Session Summary (가입·온보딩·공고 등록 입력 UX 보강)

- What was done:
  - Signup field 이탈 검증과 동적 password checklist, Onboarding 지원 자격 fieldset, JobNew 날짜·오전/오후·30분 시각 control을 추가했다.
  - 온보딩 첫 저장에서 기본 프로필과 지원 자격을 각각 현재 version으로 저장하고 eligibility conflict 시 최신 값을 다시 불러온다.
- Key decisions:
  - 지원 자격의 상세 사유는 수집하지 않고 미선택 값을 허용하며, 마감 기본값은 오후 11:30으로 두되 날짜가 없으면 `null`을 전송한다.
- Issues encountered:
  - 신규 eligibility query 때문에 전체 test의 기존 router mock 1건을 보완했다.
- Validation:
  - Page 집중 테스트, Chromium desktop/mobile 회귀와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 비밀번호 안내·동의 상세 Modal)

- What was done:
  - `SignupPage`에 실제 비밀번호 byte 수 안내와 이용약관·개인정보 및 AI 처리 상세 Modal을 추가했다.
  - Modal에 수집 항목·목적·보유 기간·거부 영향, AI 처리 대상·masking·외부 API 보관 가능성·사용자 검토 책임을 사용자 문장으로 구성했다.
- Key decisions:
  - Modal은 checkbox를 자동 선택하지 않고 ESC·배경·닫기·focus trap·trigger focus 복귀·body scroll lock과 mobile sheet를 지원한다.
- Issues encountered:
  - None.
- Validation:
  - `authFlow.test.ts` 포함 집중 Vitest와 전체 Frontend check 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (AI page 활성 실행 복구·단일 재분석 CTA)

- What was done:
  - Document detail, Job overview/analysis/interview와 Cover Letter edit가 persisted active Run을 복구하고 동일 resource의 새 AI command를 비활성화한다.
  - 문서 재분석 확인 문구에 기존 경험 즉시 제거와 downstream 미사용을 명시하고 Job Analysis OUTDATED CTA를 한 개로 줄였다.
- Key decisions:
  - Run 상태 조회를 확인할 수 없는 동안에도 중복 실행보다 안전한 버튼 비활성화를 우선한다.
- Issues encountered:
  - None.
- Validation:
  - 관련 6개 test file 42개 회귀와 최종 Frontend 전체 67 files/275 tests·build가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 중앙 본문·CTA 정렬)

- What was done:
  - Dashboard를 동일한 좌우 레일과 중앙 본문으로 나눠 우측 바로가기를 제외한 헤더·CTA·본문의 중심을 viewport 중심에 맞췄다.
  - `자료 등록`·`공고 등록` CTA의 우측 끝을 중앙 본문 우측 경계에 맞추고 87rem 이하에서는 기존 가로형 바로가기로 전환했다.
- Key decisions:
  - Dashboard의 88rem 외곽 폭과 container sticky 바로가기를 유지하고 page 범위의 CSS grid만 조정했다.
- Issues encountered:
  - 최초 전체 check에서 변경한 Vue·E2E 파일의 Prettier 형식 검사만 실패해 두 파일만 formatter로 정리했다.
- Validation:
  - Dashboard Vitest 5/5, Frontend 전체 check 67 files/269 tests·production build와 Chromium Dashboard 회귀 1/1이 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (프로필 지원 자격 입력 영역)

- What was done:
  - `ProfileBasicPage`에 기존 화면 구조를 유지한 지원 자격 확인 정보 section을 추가했다.
- Key decisions:
  - 자기신고이며 실제 지원 단계에서 재확인이 필요하다는 안내를 form 내부에 표시한다.
- Issues encountered:
  - None.
- Validation:
  - Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (공고 분석 결과 hero 문구 보정)

- What was done:
  - 최신 공고 분석 결과 hero의 `최신 분석`·`분석 버전 N` 노출을 단일 사용자 문장으로 교체했다.
  - requirement 내부 출처 경로가 사용자에게 노출되지 않는 page 회귀를 추가했다.
- Key decisions:
  - 분석 버전은 과거 이력 선택 영역에서 계속 제공한다.
- Issues encountered:
  - None.
- Validation:
  - Job Analysis page 집중 테스트 7건과 Frontend 전체 67 files/267 tests 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard sticky 탐색·workspace 문구 보정)

- What was done:
  - Dashboard 우측 섹션 바로가기를 Desktop container sticky로 전환하고 좁은 화면에서는 기존 일반 흐름을 유지했다.
  - 준비 workspace 제목을 중간 단어가 끊기지 않는 두 의미 묶음으로 렌더링하고 Job Analysis 재시도 CTA를 간결하게 변경했다.
- Key decisions:
  - `fixed` positioning이나 전역 focus 변경 없이 Dashboard page 범위의 layout·문구만 조정했다.
- Issues encountered:
  - None.
- Validation:
  - Dashboard·JobAnalysis Vitest 12/12와 Chromium UI shell 3/3 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard section 탐색·Job Analysis 실패 UX)

- What was done:
  - Dashboard의 시각적 중복 제목을 숨기고 screen reader heading은 유지했으며 self-hosted variable Noto Sans KR 제목과 섹션 anchor를 연결하는 비고정 바로가기를 추가했다.
  - Job Analysis의 재분석 품질 control을 제거하고 `BALANCED` 요청으로 고정하며 내부 structured output 문구를 사용자 안내로 변환했다.
- Key decisions:
  - Dashboard 바로가기는 Desktop 우측 일반 flow, 좁은 화면 가로 navigation으로 제공한다.
- Issues encountered:
  - Journey는 완료된 분석 page에서는 숨겨져 Overview에서 노출되므로 Browser nowrap 검증을 실제 노출 route로 이동했다.
- Validation:
  - Dashboard·JobAnalysis·JobDetail unit 13 tests, Frontend 전체 265 tests와 Browser 회귀 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (마감 캘린더 밀도·상태 hierarchy 개선)

- What was done:
  - 캘린더 상단 summary와 월 toolbar, 고정 간격 날짜 grid, today marker, selected surface와 deadline event chip을 B2C dashboard tone으로 재설계했다.
- Key decisions:
  - 기존 click·month navigation·today·desktop/mobile detail 연동과 서울 시간 계약은 변경하지 않았다.
- Issues encountered:
  - 오늘 다음 셀 hover가 선택 외곽선과 시각적으로 겹치는 문제를 grid gap과 inset selection으로 보정했다.
- Validation:
  - Dashboard unit test, Chromium responsive 3/3과 hover cell bounding-box regression 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 사람 icon·주말 캘린더·Guide modal 개선)

- What was done:
  - 커리어 avatar를 사람 SVG로 교체하고 제목 이름만 theme color로 강조했으며 주말과 날짜별 마감 건수, workspace CTA 위치, 장문 modal을 보완했다.
- Key decisions:
  - Calendar cell에 weekday를 명시해 색상 규칙을 testable하게 만들고 modal 본문은 서버의 빈 줄 기준 문단으로 렌더링한다.
- Issues encountered:
  - Dashboard 내부의 미정의 color alias를 기존 brand·muted token에 연결했다.
- Validation:
  - Dashboard·shared UI unit test, Frontend 67 files/265 tests·build와 Chromium 1440·1024·390px 회귀 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Dashboard 지원 워크스페이스 재구성)

- What was done:
  - 자연스러운 개인화 제목, 커리어 카드·첫 준비·다음 할 일·행동형 요약, 월별 마감 캘린더와 5개 서버 가이드 modal을 구현했다.
- Key decisions:
  - loading·partial error·empty·unknown을 분리하고 mobile deadline은 native `details`, guide는 focus trap dialog를 사용한다.
- Issues encountered:
  - None.
- Validation:
  - Dashboard component/API/router tests와 Frontend 전체 264 tests, Chromium 반응형 회귀 통과.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Landing Hero 크기·카피·데모 control 정리)

- What was done:
  - Hero headline 크기를 기존 대비 약 80%로 낮추고 서비스 소개·이용 흐름·핵심 가치·AI 활용 원칙 heading을 요청 문구로 교체했다.
  - 자동 DOM 데모의 일시 정지·재생 button과 수동 정지 상태를 제거하고 viewport·Page Visibility·reduced motion 기반 lifecycle은 유지했다.
- Key decisions:
  - 명시적인 최신 요청에 따라 수동 control을 제거하되 visual demo는 `aria-hidden`, 전체 흐름은 고정 screen reader 설명으로 제공하고 reduced motion에서는 대표 scene을 정적으로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - Landing component Vitest 10/10과 Chromium Landing 7/7, 1440·390·320px screenshot 검수가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (Landing Hero·자동 DOM 제품 데모)

- What was done:
  - Hero를 전체 폭 2줄 heading과 하단 설명·CTA/제품 데모 2열로 재구성하고, 정적 preview를 경험 준비→공고 등록→자동 분석→결과→다음 준비의 5개 DOM scene으로 교체했다.
  - `LandingProductDemo.vue`에 단일 timeout loop, viewport·Page Visibility·수동 pause/resume, reduced motion 정지와 unmount cleanup을 구현했다.
  - Hero stagger와 후속 section 최초 진입 reveal을 기본 visible·mount 후 opt-in 방식으로 추가했다.
- Key decisions:
  - 다른 서비스 MP4는 motion 참고에만 사용하고 production asset·문구·UI·오디오는 포함하지 않았으며 새 animation dependency도 추가하지 않았다.
  - 자동 scene은 `aria-live`로 알리지 않고 전체 흐름을 한 번의 screen reader 설명으로 제공한다.
- Issues encountered:
  - 시스템 PATH에 ffmpeg가 없어 임시 `imageio-ffmpeg` 바이너리로 reference metadata와 1초 간격 frame을 분석했다.
- Validation:
  - 관련 ESLint·`vue-tsc`, Landing component/Vitest 11/11, Chromium Landing 7/7과 1440·390·320px 시각 검수가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (서비스 소개 Landing·Dashboard 체크리스트)

- What was done:
  - 서비스 가치, 문제, 5단계, 핵심 가치, AI 활용 원칙과 CTA를 semantic section으로 구성한 `LandingPage`를 추가했다.
  - Dashboard의 신규 사용자 전용 분기를 제거하고 profile·Document·Job별 완료·미완료·unknown 상태를 표시하는 체크리스트를 일반 현황 위에 배치했다.
- Key decisions:
  - 세 항목 모두 완료할 때만 체크리스트를 숨기며 AI 작업 유무와 영구 dismiss 상태는 사용하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Landing·Dashboard·Guide component tests와 1440·390·320px Playwright, 시각 캡처가 통과했다.
- Next steps:
  - None.

## [2026-08-02] Session Summary (핵심 페이지 정보 구조·가이드)

- What was done:
  - 목록·상세·편집 page header variant와 자연스러운 한국어 문구를 적용하고 공고 등록·overview·analysis 화면을 자동 분석 흐름으로 재구성했다.
  - 가입 직후 흐름과 다시 볼 수 있는 5단계 `/guide`를 실제 제품 component preview로 구현했다.
- Key decisions:
  - guide dismiss 영속 상태를 새로 만들지 않고 언제든 진입 가능한 도움말 route로 제공한다.
- Issues encountered:
  - 분석 화면의 nav와 child CTA 중복 locator는 landmark scope로 구분했다.
- Validation:
  - Dashboard·JobAnalysis·Guide component tests, 전체 Frontend check와 30장 visual capture 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (공고 자동 판독·수동 fallback 안내)

- What was done:
  - Job overview의 자동 처리 중·자동 부족 안내와 CTA 회귀를 보정했다.
- Key decisions:
  - URL/사용자 입력 필드를 보존하고 깨진 자동 text를 표시하지 않는다.
- Issues encountered:
  - 기존 문구 assertion 2건을 새 사용자 메시지로 갱신했다.
- Validation:
  - job page component test와 Frontend 전체 check 통과.
- Next steps:
  - None.

## [2026-08-01] Session Summary (자료·대외활동 화면 B2C 흐름)

- What was done:
  - 문서 목록/상세 정보 구조와 사용자 문구를 개선하고 직접 대외활동 등록·수정·삭제 화면을 추가했다.
  - 자료·공고·프로필·AI 내역 위험 작업을 공통 확인 모달로 옮기고 성공 toast를 연결했다.
- Key decisions:
  - `/profile/activities`를 canonical route로 두고 과거 evidence route는 redirect한다.
- Issues encountered:
  - 실제 브라우저 실패 화면에서 소재 요약이 `정리 중`으로 남아 상태 label을 직접 표시하도록 보정했다.
- Validation:
  - Frontend 전체 check와 Playwright desktop/mobile 흐름 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (P8 세 화면·답변 409 UX)

- What was done:
  - Job Interview tab, `/interviews`, question set 상세에서 준비·coverage/source·질문·답변 version·feedback 흐름을 구현했다.
- Key decisions:
  - `LIMITED|NONE`은 경고가 있는 성공으로, provider 장애는 안전한 오류·retry로 구분한다.
  - 답변 충돌 취소는 server state로 동기화하고 재적용은 최신 parent와 최초 사용자 snapshot을 명시적으로 결합한다.
- Issues encountered:
  - None.
- Validation:
  - page tests와 actual E2E의 SUFFICIENT·409·feedback·responsive 흐름이 통과했다.
- Next steps:
  - P9 mock interview 화면은 구현하지 않는다.

## [2026-07-31] Session Summary (최종 학력·UI 문구와 hover 보정)

- What was done:
  - 기본 정보 닉네임 field와 수동 대표 학력 action을 제거하고 학력 단계·최종 학력 badge를 추가했다.
  - 승인·거절 안내 card, AI 작업 `선택`/`삭제(n)` 문구와 관심 공고 active hover를 보정했다.
- Key decisions:
  - 서버가 계산한 `isPrimary`만 최종 학력 badge로 표시한다.
- Issues encountered:
  - None.
- Validation:
  - page/layout targeted 24 tests와 Frontend 전체 check가 통과했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (프로필 하단 저장·대외활동·작업 삭제)

- What was done:
  - 기본 정보 savebar를 모든 입력 뒤로 이동하고 대외활동 filter gap, 학력 상태 한국어 표시와 승인·confidence 안내를 적용했다.
  - legacy 응답에 EDUCATION source가 섞여도 대외활동 card 목록에서 렌더링하지 않는 client guard를 추가했다.
  - terminal Agent Run 개별 삭제, 현재 페이지 선택·전체 선택 및 선택 삭제 UI를 추가했다.
- Key decisions:
  - 직접 입력 근거는 이미 VERIFIED라 승인·거절을 숨기고 문서 AI 추출 근거에만 검토 action을 표시한다.
  - 작업 삭제 확인문은 실행 결과와 비용 audit이 보존됨을 알린다.
- Issues encountered:
  - 전체 check 중 수정 파일 4개와 마지막 학력 상태 mapping 1개의 Prettier 경고는 대상 파일 format 뒤 해소했다.
- Validation:
  - page targeted 13 tests와 Frontend 전체 53 files/215 tests·production build 통과.
- Next steps:
  - None.

## [2026-07-31] Session Summary (프로필 기본 정보·필터 간격 보정)

- What was done:
  - Profile 기본 정보 저장 bar에 좌우 border·radius·16px padding을 적용하고 닉네임 입력을 기존 단일 저장 action에 통합했다.
  - Cover Letter 목록의 검색·상태·정렬·적용 control grid에 12px gap을 추가했다.
- Key decisions:
  - Profile 본문 저장 성공 뒤 nickname만 실패하면 부분 성공 alert를 보여 주고 nickname dirty 상태를 유지한다.
- Issues encountered:
  - 실행 중인 기존 Backend가 새 account endpoint 전 source여서 browser API 결합 대신 Frontend unit과 Backend integration을 각각 검증했다.
- Validation:
  - Page tests와 Frontend 53 files/214 tests, 1440×1000 save bar inset·filter gap 실측이 통과했다.
- Next steps:
  - None.

## [2026-07-30] Session Summary (P7 editor 409 비교·재적용 보강)

- What was done:
  - 문항 field, 전체 정렬, current answer content와 lifecycle 상태를 operation별 최신 server snapshot과 최초 사용자 snapshot으로 비교한다.
  - 질문·정렬·답변 충돌의 재적용과 취소를 각각 검증하고 actual E2E에 실제 문항 409를 추가했다.
- Key decisions:
  - 재적용은 사용자의 명시적 버튼 동작이며 refetch나 Vue Query mutation이 자동으로 overwrite하지 않는다.
- Issues encountered:
  - answer 취소 시 Vue Proxy를 직접 복제하지 않고 canonical plain document로 동기화하도록 보정했다.
- Validation:
  - page 대상 409 tests와 전체 Frontend 53 files/211 tests, P7 actual Chromium 1/1이 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 기다린다.

## [2026-07-30] Session Summary (P7 자기소개서 세 화면)

- What was done:
  - 전체 목록, 공고별 상태/생성 진입과 문항 navigator·TipTap·근거·검증·version drawer를 갖춘 canonical editor를 구현했다.
  - question CRUD/order, generation partial result/retry, 명시적 save/restore/verify, warning acknowledgement/finalize와 archive read-only를 연결했다.
- Key decisions:
  - 공고 tab에 editor를 복제하지 않고 archived 상세은 mutation을 비활성화하며 조건부 unarchive만 제공한다.
- Issues encountered:
  - 실제 question maxLength number input parser 오류와 mutation UI race를 보정했다.
- Validation:
  - page/component tests, P7 actual 전체 시나리오와 1440/390px overflow가 통과했다.
- Next steps:
  - 독립 validator 판정을 반영한다.

## [2026-07-29] Session Summary (P6 공고별 분석 결과 페이지)

- What was done:
  - 분석 없음·선행 조건·진행·WAITING_USER·실패·성공·OUTDATED·history 상태를 단일 Job Analysis page에 구현했다.
  - Eligibility, fit score 안내, requirement, strength/gap, verified evidence와 criterion breakdown을 분리해 표시했다.
  - historical evidence의 현재 상태가 바뀌어도 분석 당시 결과를 유지하고 재분석 제외 상태를 텍스트로 표시한다.
- Key decisions:
  - 프로필 미완료는 경고만 표시하고 usable 공고 분석을 차단하지 않으며 OUTDATED 기존 결과도 숨기지 않는다.
- Issues encountered:
  - 실제 E2E의 같은 버전 제목 locator와 비공개 evidence GET assertion을 공개 계약에 맞게 보정했다.
  - 1차 validator의 historical detail 거부 finding을 canonical current evidence 상태 수용과 OUTDATED 안내로 보정했다.
- Validation:
  - Job Analysis component, 1440px/390px Chromium·keyboard·overflow와 Frontend 전체 169 tests가 통과했다.
- Next steps:
  - 수정된 actual P6 E2E assertion은 재검증 상한으로 아직 실행하지 않았다.

## [2026-07-28] Session Summary (지원 현황 Dashboard·기본 프로필 단일 편집 구조)

- What was done:
  - Dashboard를 사용자 이름 기반 제목, 핵심 빠른 작업, 실제 집계, 상태 기반 다음 할 일, 마감 임박 공고, 최근 활동과 신규 사용자 전용 시작 안내로 재구현했다.
  - Profile 기본 정보 화면을 기본 정보·자기소개·희망 조건의 세 section과 단일 저장 action으로 재구성하고 dirty·saving·success·error·409 conflict 상태를 분리했다.
  - Dashboard의 기존 사용자, 신규 사용자 이름 fallback, 부분 query 오류를 검증하는 unit test를 추가하고 Profile 저장·field error 회귀 test를 갱신했다.
- Key decisions:
  - Dashboard 최근 목록은 현재 query가 반환한 항목만 표시하고, 전체 수치는 pagination의 `totalElements`만 사용한다.
  - 서버 field error는 해당 control 가까이에 유지하고 저장 완료 뒤 version baseline을 갱신하되 auto-save는 도입하지 않았다.
- Issues encountered:
  - Cover Letter와 Mock Interview는 현재 연결 가능한 Dashboard API가 없어 가짜 최근 활동이나 수치를 만들지 않았다.
- Validation:
  - 관련 Vitest 6 files/25 tests와 전체 40 files/154 tests, TypeScript, production build 통과.
  - 1440px·390px Dashboard와 Profile 스크린샷에서 overflow, CTA 우선순위와 상태 구분을 확인했다.
- Next steps:
  - 전용 Dashboard 계약 구현 뒤 자기소개서·모의 면접·검증 경고 집계를 연결한다.

## [2026-07-28] Session Summary (Dashboard·필터·기본 정보 화면 완성도 보정)

- What was done:
  - Dashboard hero 제목을 desktop 한 줄, 390px 의미 단위 두 줄로 조정하고 장식과 설명의 충돌을 제거했다.
  - 자료·관심 공고 filter control 사이의 여백을 늘리고 프로필 기본 정보의 공통 정보·희망 조건을 번호, eyebrow, surface와 section divider로 구분했다.
  - 노란 프로필 작성 안내를 브랜드 blue soft surface로 교체하고 희망 조건 입력군의 구조적 rail을 추가했다.
- Key decisions:
  - 기존 form ID, label, mutation, query, route와 오류·conflict 동작은 유지했다.
- Issues encountered:
  - 첫 mobile 캡처에서 제목이 세 줄이 되어 한 차례 typography를 보정했다.
- Validation:
  - 1574px·390px Dashboard, 1440px·390px 기본 정보, 1600px 자료·공고 필터를 직접 검수했고 390px 가로 넘침이 없었다.
  - Frontend 전체 check와 fixture UI shell 3/3이 통과했다.
- Next steps:
  - 실제 데이터가 필요한 cross-stack 시나리오는 이번 visual-only 보정 범위에서 재실행하지 않았다.

## [2026-07-28] Session Summary (현재 전체 Page 정보 구조·Form 재설계)

- What was done:
  - 인증·onboarding·dashboard·7개 profile·documents·jobs·분석 기록·404에 Hiresemble Blue control과 B2C action copy를 적용했다.
  - 자료 등록은 dropzone→분류→분석, 공고 등록은 URL 우선→직접 입력 disclosure, 목록 filter는 mobile 접기 흐름으로 재구성했다.
- Key decisions:
  - Document·Job 상태 축, 201/202, idempotency, 409, SSE, ID·test selector와 입력값 보존을 유지했다.
- Issues encountered:
  - 실제 Document E2E는 upload API 일반 오류로 첫 시나리오가 timeout되어 완료하지 못했다.
  - 실제 Profile E2E는 현재 온보딩 문구까지 동기화했지만 완료율 text·progressbar strict locator 중복에서 중단됐다.
- Validation:
  - page component test, 전체 149 tests, fixture Playwright 5/5와 18개 화면 네 viewport 시각 검수가 통과했다.
- Next steps:
  - Profile 완료율 locator를 명시적으로 한정하고 실행 Backend 설정을 갱신한 뒤 actual pipeline을 재검증한다.

## [2026-07-28] Session Summary (프로필·자료 등록 화면 전문 서비스화)

- What was done:
  - 프로필을 지원 방향 brief와 단계형 form으로, 자료 등록을 파일 선택·분류·분석 안내 흐름으로 재구성했다.
  - 닉네임, 분석 기록과 졸업(예정)일을 전체 현재 route의 사용자 언어로 통일했다.
- Key decisions:
  - 기존 DOM ID·API mutation·route와 자유 입력 기능을 유지하고 정보 계층과 반응형 표현만 강화했다.
- Issues encountered:
  - 모바일에서는 sticky guide를 일반 흐름으로 바꾸고 file card action을 wrap해 overflow를 제거했다.
- Validation:
  - Page tests, Frontend 전체 145 tests와 390px Playwright 검증이 통과했다.
- Next steps:
  - 실제 장문 경력·파일명 데이터로 시각 밀도를 추가 확인한다.

## [2026-07-28] Session Summary (현재 Route B2C UX Writing 전면 적용)

- What was done:
  - 18개 사용자 route와 404의 제목, 설명, CTA, helper, loading·empty·error·success·conflict 문구를 사용자 결과와 다음 행동 중심으로 다시 작성했다.
  - Dashboard를 가상 KPI 없이 네 가지 실제 작업을 잇는 numbered path로 재구성했다.
  - 경험 정보의 원시 JSON 입력을 타입 보존 항목형 편집기로 바꾸고 자료 목록·상세에서 MIME type을 숨겼다.
- Key decisions:
  - `근거`는 문맥에 따라 경험 정보·자료에서 찾은 정보로 바꾸고 대표 학력은 `먼저 보여 줄 학력`으로 설명한다.
- Issues encountered:
  - 일부 성공·충돌 문장에 남은 `-습니다`형을 루트 시각·문구 감사에서 찾아 `-해요/-해 주세요`형으로 통일했다.
- Validation:
  - page component test와 18개 보호 route+404의 1440·390px 직접 진입·overflow smoke가 통과했다.
- Next steps:
  - Backend가 필요한 실제 데이터 밀도와 긴 파일명·공고명 검수는 actual E2E 환경에서 수행한다.

## [2026-07-27] Session Summary (현재 Route Page 정보 구조 개선)

- What was done:
  - 인증, onboarding, dashboard, 7개 profile route, Documents, Jobs, Agent Run과 404의 typography·form·action·state hierarchy를 개선했다.
  - Dashboard 개발 문구를 제거하고 실제 route 빠른 작업만 제공했으며 onboarding 마지막 단계는 구현된 문서 업로드 또는 추후 입력만 제공한다.
- Key decisions:
  - 가상 집계·최근 활동·미구현 analysis/cover-letter/interview/settings 화면과 API는 추가하지 않았다.
  - 기존 form ID, `data-testid`, accessible name, 상태별 CTA와 mutation/query 흐름을 보존했다.
- Issues encountered:
  - 구조화 profile의 반복 form은 하나의 generic page 안에 있어 동작을 분할하지 않고 공통 scoped style로 시각 일관성만 맞췄다.
- Validation:
  - 기존 literal DOM ID와 `data-testid` 누락 0건, page component와 전체 128 tests가 통과했다.
- Next steps:
  - cross-stack 환경에서 긴 실제 문서명·URL·공고 본문 조합의 수동 시각 검수를 보강한다.

## [2026-07-27] Session Summary (P5 Job 목록·등록·overview Page 구현)

- What was done:
  - 상태 tab·filter·pagination 목록, 201/202 생성과 편집·상태·retry·manual·delete 상세를 구현했다.
- Key decisions:
  - 업무/추출 badge를 분리하고 submittedAt 이력이 있는 CLOSED 공고를 표시한다.
- Issues encountered:
  - NEEDS_MANUAL_INPUT retry를 제거하고 수동 입력만 강조하도록 validator 보정했다.
- Validation:
  - page component test와 실제 Chromium Job E2E 5/5가 통과했다.
- Next steps:
  - P6 전까지 분석 버튼·가짜 page를 추가하지 않는다.

## [2026-07-19] Session Summary (P4 Document 목록·상세 Page 구현)

- What was done:
  - upload·filter·pagination·sort 목록과 metadata·text·manual·reparse·download·delete·evidence 상세를 구현했다.
- Key decisions:
  - `PARSED + evidence FAILED`는 업로드 실패가 아니라 text preview를 유지하는 partial success로 표시한다.
- Issues encountered:
  - None.
- Validation:
  - page component tests와 실제 성공·manual·failure·isolation Browser 시나리오가 통과했다.
- Next steps:
  - Dashboard와 P5 이후 pages는 미착수다.

## [2026-07-19] Session Summary (P3 Agent Run 목록·상세 Page 구현)

- What was done:
  - workflow/status/retryable filter, pagination·sort 목록과 URL canonicalization을 구현했다.
  - REST detail 뒤 SSE controller를 연결하고 retry successor 이동과 cancel CAS를 조정했다.

- Key decisions:
  - WAITING_USER action·FAILED retry·active cancel은 server boolean과 상태를 함께 사용한다.

- Issues encountered:
  - None.

- Validation:
  - list page·detail panel component와 browser fixture가 통과했다.

- Next steps:
  - Dashboard 집계나 AI 설정 page는 P10까지 추가하지 않는다.

## [2026-07-19] Session Summary (P2 프로필·온보딩·evidence Page 구현)

- What was done:
  - 기본 프로필, 다섯 구조화 resource, evidence 목록·편집·검토와 4단계 onboarding을 구현했다.
  - 완료·부족 항목, 대표 학력, timeline/list, pagination·sort, 삭제 확인과 409 재적용 UI를 연결했다.

- Key decisions:
  - `SOURCE_DELETED`는 read-only로 렌더링하되 P2 data에서는 생성하지 않는다.
  - document 연결·filter는 후속 단계 안내만 표시하고 입력 control을 활성화하지 않는다.

- Issues encountered:
  - onboarding fetch 오류가 성공 단계로 진행되지 않도록 실패 상태를 테스트로 보정했다.

- Validation:
  - page component·onboarding flow와 frontend 전체 check, 실제 Chromium E2E가 통과했다.

- Next steps:
  - Dashboard는 P10 전까지 shell로 유지하고 document 업로드는 P4에서 구현한다.

## [2026-07-19] Session Summary (P1 인증 Page와 shell 구현)

- What was done:
  - signup/login Form, onboarding/dashboard shell, root 대기와 404 page를 구현했다.

- Key decisions:
  - signup은 항상 onboarding, login은 검증된 returnTo 또는 dashboard로 이동한다.

- Issues encountered:
  - server field 오류 시 disabled input에 focus할 수 없는 접근성 결함을 test로 발견해 제출 상태 해제 후 focus하도록 수정했다.

- Validation:
  - authFlow component test와 route shell·404 test, Frontend check가 통과했다.

- Next steps:
  - P2에서 onboarding 실제 Form과 API를 별도 범위로 구현한다.
