# Progress

## Overview

Gate 2 공개 GitHub와 Gate 5 GitHub App private source의 owner-scoped query, connection mutation, callback cleanup과 focused Run 표시 기반을 관리한다.

## [2026-08-09] Session Summary (GitHub 화면 진행률 표시와 문구·디자인 정리)

- What was done:
  - `GitHubRunMonitor.vue`의 `<progress>`가 class 없이 브라우저 기본 막대(초록/회색)로 그려지던 것을 공용 `progress-track`으로 바꿨다. 단계 이름과 %를 막대 위 한 줄에 두고 막대는 열 폭을 다 쓴다.
  - 끝난 작업에는 진행률 막대와 연결 상태 문구를 남기지 않는다. 상태 badge와 `AI 작업 상세 보기` link만 남긴다.
  - `presentation.ts`에 `GITHUB_STATUS_DESCRIPTIONS`와 `GITHUB_CONNECTION_STATUS_LABELS`를 추가해 상태 설명과 연결 상태 라벨을 한곳에서 정의했다. `formatGitHubInstant`는 Intl medium/short로 바꾸고 `formatGitHubDate`를 추가했다.
  - 영문 jargon과 합쇼체를 서비스 문구로 바꿨다. `ACTIVE`→`연결됨`, `installation`·`uninstall`·`PAT/token`·`provenance 원본`·`snapshot`·`capability`를 한국어 설명으로 교체했다.
  - `.alert` 면 채움 알림을 공용 `InlineNotice`로 교체했다.
  - 후속 확인에서 `등록된 연결 / GitHub 연결 N개` 제목 줄을 없애고 `sr-only` 제목만 남겼다. 다른 목록 화면과 같은 규칙이다. FAILED 안내는 "AI 작업 상세에서 재시도 가능 여부 확인" 링크 문장 대신 제목·설명과 `원인 보기` 버튼을 가진 notice로 바꿨다.
- Key decisions:
  - 사용자에게 의미 없는 optimistic-lock `version` 숫자를 카드에서 빼고 `등록한 날`을 넣었다. mutation은 계속 `source.version`을 사용한다.
  - 아직 아무것도 고르지 않은 상태를 붉은 `inline-error`로 알리던 문구를 안내 문구로 바꿨다. 선택 전은 오류가 아니고 primary 버튼이 이미 비활성이다.
  - `Metadata read`·`Contents read`는 GitHub 화면에 그대로 나오는 권한 이름이라 한국어 설명과 함께 남겼다.
- Issues encountered:
  - 두 GitHub spec이 진행 중에만 보이는 단계 이름(`경험 후보 찾기`)을 확인하고 있었는데, fixture가 terminal SSE를 즉시 보내 막대를 감춘 뒤에는 잡히지 않았다. monitor 존재와 완료 상태 확인으로 바꿨다.
  - `phase5-private-github.spec.ts`에 이전부터 있던 strict locator 중복 2건(`ACTIVE`, 경험 제목)을 exact/first로 고쳤다.
- Validation:
  - `corepack pnpm check`: 102 files, 465 tests와 lint·format·typecheck·production build 통과.
  - `github-source.spec.ts` Chromium 1건 통과. `phase5-private-github.spec.ts`는 이번 문구 변경 구간(권한 다시 확인·연결 해제 dialog·private 기록 정리·회원 탈퇴)을 모두 통과했지만 마지막 줄에서 실패해 전체는 `NOT_VERIFIED`다.
- Next steps:
  - `phase5-private-github.spec.ts:126`은 탈퇴 후 `/login?returnTo=%2Fsettings%2Faccount`를 기대하는데 실제로는 `/login`으로만 이동한다. 이번 변경과 무관한 Gate 5 동작 차이이므로 별도로 판단이 필요하다.

## [2026-08-09] Session Summary (GitHub App connection과 private source card)

- What was done:
  - capability/unavailable, 연결 시작·결과, personal/organization 목록·상태·refresh·manage link·disconnect dialog와 private source 입력을 구현했다.
- Key decisions:
  - install/manage URL은 HTTPS github.com의 고정 path/query shape만 허용하고 callback 민감 query는 즉시 제거한다. PAT 입력 UI는 없다.
- Issues encountered:
  - setup mock 302의 route interception 우회가 있어 E2E fixture를 명시적 OAuth link와 catch-all HTTPS 차단으로 보정했다.
- Validation:
  - component/navigation/query test와 Frontend 전체 check가 통과했다. 통합 Chromium은 strict locator 보정 뒤 재검증 대기다.
- Next steps:
  - 같은 5개 Chromium journey 재검증 후 실제 App UAT를 사용자에게 인계한다.

## [2026-08-08] Session Summary (Career Artifact suggestion readiness 연동)

- What was done:
  - READY/PARTIAL 성공 summary에 선택적 Career Artifact 제안을 연결하고 source 완료·삭제 뒤 readiness를 갱신했다.
- Key decisions:
  - Career Artifact flag off 또는 readiness 실패 시 제안과 요청만 숨기고 GitHub 주 흐름은 유지한다.
- Issues encountered:
  - 없음.
- Validation:
  - 기존 `github-source.spec.ts`를 Career Artifact spec과 함께 Chromium에서 통과했다.
- Next steps:
  - Private GitHub는 Gate 5까지 추가하지 않는다.

## [2026-08-08] Session Summary (GitHub Source query·mutation·Run monitor)

- What was done:
  - source list/detail/repository key, server query, create/selection/refresh/delete mutation, 상태·URL presentation과 focused Agent Run monitor를 구현했다.
  - 같은 pending action의 idempotency key 재사용, 성공·입력 identity 변경 시 교체, delete stream 종료와 source/experience cache 정리를 검증했다.
- Key decisions:
  - mutation retry는 모두 false이고 409는 자동 재시도하지 않는다. refresh unchanged는 source snapshot만 갱신하며 changed run만 monitor한다.
- Issues encountered:
  - None.
- Validation:
  - `src/features/github` 단위·component test와 전체 Frontend 80 files/369 tests 통과.
- Next steps:
  - None.
