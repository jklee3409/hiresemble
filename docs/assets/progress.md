# Progress

## Overview

- README 서비스 소개용 실제 화면 캡처 15종을 관리한다.
- 캡처는 Backend 없이 결정론적 fixture 응답과 Chromium desktop viewport에서 얻는다.

## [2026-08-05] Session Summary (README 서비스 소개용 화면 캡처 신규 생성)

- What was done:
  - Landing, 이용 가이드, Dashboard, 내 지원 정보, 이력서·자료 목록·상세, 공고 등록·목록·정보, 공고 분석 요약·요건 구간, 자기소개서 편집, 면접 조사 결과·예상 질문, AI 작업 내역 화면을 1440px desktop, device scale factor 2로 캡처했다.
  - 디렉터리 추적 문서 `index.md`와 `progress.md`를 함께 생성했다.

- Key decisions:
  - 실제 사용자·운영 데이터를 쓰지 않고 `/api/v1/**`를 가로챈 예시 fixture로만 화면을 채운다.
  - 화면 높이가 긴 공고 분석과 면접 준비는 viewport 높이와 scroll 위치만 조정해 같은 1440px 폭을 유지한다.

- Issues encountered:
  - 초기 fixture가 `deadlineSource`, `outdatedReasons`, `automaticAnalysis.state`, evidence `sourceType`, 면접 `questionType`, Dashboard `primaryEducation` 필드에서 실제 Zod 계약과 어긋나 오류 화면이 캡처됐다. 계약 값으로 맞춰 재캡처했다.

- Validation:
  - 캡처된 15개 PNG를 직접 열어 오류 배너 없이 의도한 화면이 렌더링됐는지 확인했다.

- Next steps:
  - 화면 개편 시 관련 캡처와 README 설명을 함께 갱신한다.
