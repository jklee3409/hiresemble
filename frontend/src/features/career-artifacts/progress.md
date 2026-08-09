# Progress

## Overview

Career Artifact Gate 4 Frontend 구현과 검증 결과를 최신순으로 기록한다.

## [2026-08-09] Session Summary (Private GitHub 승인 경험 회귀 확인)

- What was done:
  - 별도 artifact pipeline을 만들지 않고 기존 wizard가 private GitHub에서 승인된 canonical 경험도 동일한 exact model·DOCX/PPTX workflow로 소비하는 fixture를 Phase 5 journey에 연결했다.
- Key decisions:
  - raw private evidence나 connection identity는 Career Artifact request/context에 추가하지 않는다.
- Issues encountered:
  - Phase 5 browser journey가 connection 화면 locator에서 먼저 멈춰 private 경험→artifact 종단 구간은 해당 run에서 도달하지 못했다.
- Validation:
  - Frontend 전체 102 files/465 tests와 기존 Career Artifact Chromium 3/3은 통과했다. Phase 5 종단 journey는 재검증 대기다.
- Next steps:
  - selector 보정 상태의 Phase 5 journey에서 Resume DOCX와 Portfolio preview/PPTX를 다시 확인한다.

## [2026-08-09] Session Summary (진행 표시와 자료 영역 전환 정리)

- What was done:
  - `CareerArtifactRunMonitor`의 진행 막대가 class 없는 native `<progress>`라 OS 기본 초록색으로 그려지던 문제를 고쳤다. 공용 `.progress-track`을 쓰고 현재 단계와 비율을 막대 위 한 줄로 옮겼다.
  - monitor 표면을 회색 채움에서 흰 카드로 바꿔 같은 화면의 다른 섹션과 위계를 맞췄다.
  - `CareerArtifactAreaSwitch`의 회색 배경이 부모 layout에 따라 어떤 화면에서만 짧아지던 문제를 `display: flex; width: 100%`로 고정해 해결했다.
  - 제안 문구와 미리보기 kicker의 `구조화`, `근거` 같은 표현을 사용자 말로 바꿨다.
- Key decisions:
  - 진행 막대는 화면마다 다시 만들지 않고 `main.css`의 공용 token을 쓴다.
- Issues encountered: 없음.
- Commands run:
  - `vitest run src/features/career-artifacts`: 통과.
- Follow-ups: 없음.

## [2026-08-08] Session Summary (자료 영역 3-way 전환)

- What was done:
  - `CareerArtifactAreaSwitch`를 `자료 업로드 | 외부 연동 | AI로 만든 초안` 세 갈래로 넓히고 각 항목을 자기 flag로 개별 gate했다. 남는 항목이 하나뿐이면 전환 자체를 그리지 않는다.
  - 첫 항목 문구를 `업로드한 자료`에서 `자료 업로드`로 바꿨다.
  - active 판정을 고정 두 갈래 비교에서 path prefix 비교로 바꿔 하위 경로에서도 같은 맥락을 유지한다.
- Key decisions:
  - 두 번째 항목 이름을 `GitHub`가 아니라 `외부 연동`으로 뒀다. provider를 IA에 박지 않아야 비개발 직군에게도 이 영역이 자기 것으로 읽히고 출처가 늘어도 구조가 유지된다.
- Issues encountered: 없음.
- Validation:
  - `node node_modules/vitest/vitest.mjs run src/features/career-artifacts`: 통과.
- Next steps: 없음.

## [2026-08-08] Session Summary (Career Artifact Gate 4 Frontend)

- What was done:
  - query key·filter·presentation, TTL draft/idempotency, 자료 switch·suggestion·4단계 form·Run monitor와 Resume/Portfolio structured preview를 구현했다.
- Key decisions:
  - current projection만 preview하고 historical version은 download만 제공한다. 연락처는 includeContact false에서 null/빈 배열로 정규화하고 AI 문맥 밖 renderer 용도로만 전송한다.
- Issues encountered:
  - lifecycle refetch와 SSE fixture replay race를 query cancel과 단조로운 fixture 상태 전이로 해결했다.
- Validation:
  - feature unit/component, 전체 Frontend 94 files/422 tests와 Career Artifact/GitHub Chromium 4/4가 통과했다.
- Next steps:
  - 과거 preview endpoint, 새 template, Private GitHub는 현재 범위 밖이다.
