# Progress

## Overview

다섯 기준 명세를 연결한 전체 시스템 설계와 단계별 구현 계획이 작성되어 있다. 이 문서는 목표 구조와 미결 계약을 추적하며 실제 비즈니스 기능 구현 완료를 의미하지 않는다.

## [2026-07-18] Session Summary (전체 시스템 설계와 구현 계획 기준선 작성)

- What was done:
  - `docs/spec/`의 모든 명세와 현재 저장소 골격을 분석해 프로젝트 목적, MVP, 모듈·도메인 관계와 기능·DB·API·페이지 추적 설계를 작성했다.
  - 문서·공고·자기소개서·면접·Agent Run workflow, 인증·격리·개인정보, 비동기·복구·SSE 구조를 통합했다.
  - 계약 결정부터 AC-01~13 통합 검증까지의 단계별 구현 계획, 완료 조건과 backend·AI workflow·frontend·validator 파일 소유권을 작성했다.
  - 충돌·누락은 권장안과 구현 보류 범위를 분리해 기록했으며 기준 명세와 비즈니스 코드는 변경하지 않았다.

- Key decisions:
  - `docs/spec/`을 기준 계약으로 유지하고 파생 설계는 별도 `docs/design/`에서 관리한다.
  - 미결 상태·DTO·수명주기·AI 정책은 P0 결정 게이트가 닫히기 전 migration이나 공개 계약으로 구현하지 않는다.
  - 백엔드는 도메인·HTTP·persistence, AI workflow는 context·model·prompt·workflow, frontend는 UI·API consumer를 소유하고 루트 관리자가 계약·추적 문서를 통합한다.

- Issues encountered:
  - 공고 상태 축, 품질·version·질문 enum, tenant DB 제약, 삭제·provenance, 멱등성, Agent Run 복구·SSE, 자기소개서 최종화, 면접·모의 면접 lifecycle 등 구현 전 결정 항목이 확인됐다.
  - 상세 이슈와 권장안은 `system-architecture.md`의 명세 불일치 절에 기록했다.
  - 독립 validator가 자기소개서 목록·보관, 조사 재시도, 면접 준비 목록의 직접 추적과 상위 진행 문서·Markdown format 보완을 요구했다.

- Validation:
  - backend·AI workflow·frontend 읽기 전용 분석이 모두 `DONE`으로 종료됐고 파일을 수정하지 않았다.
  - 독립 validator가 사용자 요구 1~~15, AC-01~~13, 격리·비동기·책임 경계와 상대 링크를 통과시키고 보조 MVP 추적 3건을 `NEEDS_CHANGES`로 판정했다.
  - 지적된 자기소개서 `ARCHIVED`·목록, `research-runs` retry, `/interviews` 목록을 한 차례 보완했다.
  - 정적 재검사에서 AC 13개, 필수 5필드를 가진 이슈 18개, 변경 문서 상대 링크와 `git diff --check`가 통과했다.
  - `corepack pnpm --dir frontend exec prettier --check "../docs/design/*.md" "../docs/index.md" "../index.md"`가 통과했다.
  - 비즈니스 코드·dependency·migration·API·UI를 변경하지 않아 backend/frontend build test는 이번 문서 작성 범위에서 실행하지 않는다.

- Next steps:
  - 실제 구현 전에 P0 결정 게이트의 공개 계약·데이터 수명주기·AI 운영 정책을 사용자 승인으로 확정한다.
