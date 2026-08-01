# Progress

## Overview

P4 Documents의 user-scoped query·mutation·SSE invalidation과 두 상태 축 presentation을 구현했다.

## [2026-08-01] Session Summary (문서 소재 검토 작업대)

- What was done:
  - 검토 전·활용 승인·활용 제외·재검토 상태, 개별/선택/전체 동작과 원본 비삭제 안내를 단일 검토 패널에 구현했다.
- Key decisions:
  - 중복 결과 섹션 대신 카드 안 접힌 보조 정보로 원본·AI 정리를 비교한다.
- Issues encountered:
  - None.
- Validation:
  - document page/component/API tests와 실제 TXT 업로드·실패 상태를 검증했다.
- Next steps:
  - 성공 분석 복수 소재는 실제 Provider 환경에서 추가 검증한다.

## [2026-07-28] Session Summary (자료 등록 단계형 UX와 용어 개선)

- What was done:
  - drag 상태, 선택 파일 card·해제, 유형별 설명, 자료 이름 예시와 분석 안내를 추가했다.
  - 연결된 Agent Run 사용자 용어를 진행 중인 분석·분석 기록으로 바꿨다.
- Key decisions:
  - multipart field, idempotency key와 두 상태 축 계약은 변경하지 않았다.
- Issues encountered:
  - None.
- Validation:
  - Document validation/page tests와 390px overflow 검증이 통과했다.
- Next steps:
  - 실제 storage pipeline에서 긴 파일명과 20MB 경계를 재확인한다.

## [2026-07-28] Session Summary (문서 읽기·경력 정보 정리 표현 적용)

- What was done:
  - parse를 `문서 읽기`, evidence extraction을 `경력 정보 정리`로 표현하고 partial success·SOURCE_DELETED 안내와 action 문구를 재작성했다.
- Key decisions:
  - 두 상태 축과 REST 원천·SSE invalidation, upload·manual·reparse·download·delete 동작은 변경하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Document presentation/page test와 AI 작업 fixture 흐름이 통과했다.
- Next steps:
  - 실제 storage pipeline E2E는 격리 서비스 환경에서 재실행한다.

## [2026-07-27] Session Summary (Document 상태·근거 표현 개선)

- What was done:
  - evidence 검토와 active Run monitor를 공용 상태 언어에 맞추고 action priority와 긴 내용 가독성을 개선했다.
- Key decisions:
  - parse와 evidence extraction을 별도 label로 유지하며 `PARSED + evidence FAILED`를 업로드 전체 실패로 표현하지 않는다.
- Issues encountered:
  - 없음.
- Validation:
  - Document page/component 회귀와 전체 Frontend check가 통과했다.
- Next steps:
  - actual storage pipeline E2E는 격리 서비스 환경에서 재실행한다.

## [2026-07-19] Session Summary (Documents feature 구현)

- What was done:
  - multipart upload, Idempotency-Key, URL filter·pagination·sort, manual resume, reparse, download, delete cache purge를 연결했다.
- Key decisions:
  - SSE 단절은 문서 실패가 아니며 REST 상태가 최종 원천이다.
- Issues encountered:
  - 짧은 문서가 빠르게 WAITING_USER가 될 때 SSE event race가 있어 해당 event에서도 list/detail을 invalidate하도록 보정했다.
- Validation:
  - 관련 targeted 9/9와 Frontend 전체 95 tests, 실제 E2E의 same-run resume가 통과했다.
- Next steps:
  - P6 retrieval UI를 이 feature에 선행 추가하지 않는다.
