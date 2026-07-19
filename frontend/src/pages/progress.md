# Progress

## Overview

P1 인증·보호 shell, P2 profile, P3 Agent Run과 P4 Document list/detail page 및 전용 404를 관리한다.

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
