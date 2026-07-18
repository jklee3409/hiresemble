# Progress

## Overview

다섯 기준 명세를 연결한 전체 시스템 설계와 단계별 구현 계획, 승인 전 P0 계약 결정 제안서가 작성되어 있다. 이 문서는 목표 구조와 미결 계약을 추적하며 실제 비즈니스 기능 구현 완료나 P0 승인을 의미하지 않는다.

## [2026-07-18] Session Summary (P0 계약 결정 제안서 최종 정합성 감사 및 보정)

- What was done:
  - 기존 `PROPOSAL`을 기준 명세·설계와 다시 대조하고 수정 전·후 별도 read-only validator로 의미 기반 계약 감사를 수행했다.
  - URL·memo 상한, 면접 답변 source, mock feedback 품질, 탈퇴 idempotency, embedding 선택, feedback 취소, 공개 DTO 경계와 프로필 완료 정책을 최소 보정했다.
  - 최종 validator `PASS` 뒤 문서 상태를 `READY_FOR_OWNER_REVIEW`로 변경했다.

- Key decisions:
  - D-01~D-18은 `RECOMMENDED` 10개와 `OWNER_DECISION_REQUIRED` 8개이며, embedding과 프로필 완료를 포함한 제품 질문 8개를 검토 대상으로 둔다.
  - 회원 탈퇴에는 replay 불가능한 idempotency를 적용하지 않고, mock feedback은 `BALANCED` 고정, 면접 답변 feedback은 성공 row만 생성한다.
  - 승인 후 기준 명세를 동기화하고 이 제안서는 결정 기록 또는 archived proposal로 전환한다.

- Issues encountered:
  - 최초 validator는 embedding 차원 고정, mock HIGH_QUALITY, 탈퇴 replay, profile hard gate 4개 BLOCKER와 URL·memo·source·취소·DTO 경계 등의 MAJOR를 확인해 `NEEDS_CHANGES`로 판정했다.
  - 보정 중 기준 명세나 코드로 범위를 확장하지 않았고, 공개 DTO에서 내부 checksum·hash·prompt/model·step reuse 정보를 제거했다.

- Validation:
  - 최종 validator는 D 18개, Gate A 6개·B 5개·C 5개, enum·DTO·quality·idempotency·cancel/retry·제품 분류·변경 경로를 의미 기반으로 검사해 `PASS`했다.
  - Markdown 표 열 수, 상대 링크, 중복 endpoint·enum/field, 상한·nullability, quality allowlist, 상태 전이를 정적으로 검사하고 Prettier·`git diff --check`를 실행했다.
  - 문서 전용 작업이므로 backend/frontend build는 실행하지 않았다.

- Next steps:
  - 제품 소유자가 8개 질문과 나머지 권장안을 승인·수정한 뒤 `docs/spec/**`을 한 작업에서 동기화하고 P0 완료 여부를 다시 판정한다.

## [2026-07-18] Session Summary (P0 계약 결정 제안과 구현 차단 기준선 작성)

- What was done:
  - `system-architecture.md`의 D-01–D-18과 Gate A–C를 기능·API·DB·페이지·기술 명세 및 현재 bootstrap 코드와 대조해 `p0-contract-decision-proposal.md`를 작성했다.
  - canonical enum·상태 전이, 95개 기존 endpoint와 2개 제안 endpoint의 DTO·validation·오류·동기/비동기 계약, 데이터 수명주기·migration 책임, 고정 AI workflow, route·projection과 제품 승인 질문 6개를 구체화했다.
  - `index.md`와 설계·계획 문서에 제안서 링크를 추가하고 `implementation-plan.md`의 파일 소유권 표, `system-architecture.md`의 Gate·AC 범위 표기를 수정했다.

- Key decisions:
  - D 항목은 `RECOMMENDED` 11개와 `OWNER_DECISION_REQUIRED` 7개로 분류했으며, 사용자 승인 전에는 P0를 완료하거나 기준 명세·코드·migration에 적용하지 않는다.
  - PostgreSQL과 REST snapshot을 상태 원천으로 유지하고 SSE는 전달 수단으로만 사용하며, tenant 복합 FK·provenance·idempotency·outbox·lease/cancel·비용 reserve/settle을 구현 기준선으로 제안했다.
  - 회원 탈퇴는 Agent Run이 아닌 user FK 없는 durable deletion task와 receipt로 추적하고, AI는 8개 `WorkflowType`의 유한 step registry로만 실행한다.

- Issues encountered:
  - 1차 validator가 중첩 DTO, 면접 준비 품질 allowlist, 회원 삭제 run 소유권, 취소 후 resource 상태 4건을 `NEEDS_CHANGES`로 판정해 허용된 1회 보정과 동일 validator 재검증을 수행했다.
  - 2차 validator는 앞선 4건 해소를 확인했지만 request/response 문자열 상한, `JobSummaryDto` 필드 타입, `ResearchSourceType`, reparse placeholder를 새 `NEEDS_CHANGES`로 지적했다.
  - 루트 관리자가 마지막 지적을 명세와 같은 상한·enum·path로 정합화했으며, 오케스트레이션 상한에 따라 세 번째 validator는 실행하지 않았다. 따라서 최종 루트 보정분은 독립 validator 미검증 상태다.

- Validation:
  - backend·ai_workflow·frontend 분석 에이전트는 모두 `DONE`, 파일 변경 없음으로 종료했고 루트가 결과를 원문·diff와 대조했다.
  - validator는 두 번 모두 read-only·파일 변경 없음으로 전체 matrix를 검사했으며 두 번째 판정은 `DONE/NEEDS_CHANGES`였다.
  - 최종 루트 검사에서 D 18행(11/7), 기준 endpoint 95개 누락 0, 필수 타입 18개, 제품 질문 6개, Markdown 표 열 수, 로컬 링크, Prettier와 `git diff --check`가 통과했다.
  - 비즈니스 코드·테스트·dependency·migration·설정과 `docs/spec/**`는 변경하지 않았다.

- Next steps:
  - 제품 소유자가 6개 질문과 나머지 권장안을 승인·수정한 뒤 다섯 기준 명세를 한 번에 동기화하고, P1 착수 전에 최종 제안서의 독립 계약 검증을 다시 수행한다.

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
