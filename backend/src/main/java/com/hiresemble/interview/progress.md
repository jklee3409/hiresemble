# Progress

## Overview

P8 면접 준비·예상 질문·답변 version·feedback 수직 기능이 구현되어 있다.

## [2026-08-06] Session Summary (면접 AI 비용 전역 정책 통합)

- What was done: 준비·feedback별 고정 예상 비용과 price version 설정을 제거하고 retry를 공통 활성 가격 version 계약에 맞췄다.
- Key decisions: 면접 준비·feedback과 향후 mock turn/session은 별도 USD 상한 없이 같은 전역 일일 budget을 사용한다.
- Issues encountered: None.
- Validation: 메인·테스트 소스 컴파일 통과.
- Next steps: mock interview 구현 시 turn/session 비용 상수를 다시 만들지 않는다.

## [2026-07-31] Session Summary (P8 면접 준비·답변 피드백 구현)

- What was done:
  - prerequisite 접수, question set·question, immutable answer version CAS와 feedback success-only persistence를 구현했다.
- Key decisions:
  - 질문 source/evidence link를 authoritative provenance로 저장하고 feedback은 요청 시점 answer version에 고정한다.
- Issues encountered:
  - 새 source 저장 전 DB 조회 검증과 answer history 동적 SQL 공백 결함을 actual E2E에서 발견해 transaction 입력 allowlist와 명시적 SQL 공백으로 보정했다.
- Validation:
  - P8 API·DB·workflow 테스트, Backend 61 suites/407 tests와 P8 actual Chromium 1/1·DB assertions가 통과했다.
- Next steps:
  - P9 모의 면접 session·turn·화면은 구현하지 않았다.
