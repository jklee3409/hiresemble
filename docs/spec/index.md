# `docs/spec` 디렉터리 안내

## 디렉터리 목적

이 디렉터리는 Hiresemble 핵심 MVP가 제공해야 할 기능, 외부 API, 데이터 모델, 화면 흐름과 기술 제약을 정의하는 기준 계약을 관리한다. 명세는 구현 방향과 인수 조건을 정의하지만, 문서가 존재한다는 사실만으로 기능 구현이 완료된 것은 아니다.

## 주요 파일 및 하위 디렉터리

| 경로                             | 역할                                                                                                             |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| [`functional.md`](functional.md) | 회원·프로필·문서·공고·자기소개서·면접·Agent Run의 비즈니스 규칙, 사용자 여정과 AC-01–AC-13 인수 조건을 정의한다. |
| [`api.md`](api.md)               | `/api/v1`, Session Cookie/CSRF, 요청·응답·상태 코드, 오류 형식, 멱등성과 도메인별 endpoint 계약을 정의한다.      |
| [`db.md`](db.md)                 | PostgreSQL 18/pgvector의 논리 상태, 테이블·관계·제약, 트랜잭션과 데이터 보존 정책을 정의한다.                    |
| [`page.md`](page.md)             | Vue SPA의 route, layout, 화면별 기능과 API 연결, 상태 관리, route guard와 핵심 E2E 시나리오를 정의한다.          |
| [`tech_stack.md`](tech_stack.md) | 모듈러 모놀리스 아키텍처, 기술 선택, 보안, AI workflow, 비용 제어, 테스트·배포 원칙과 MVP 제외 범위를 정의한다.  |
| [`progress.md`](progress.md)     | 명세 영역의 현재 작성 상태, 구현과의 차이, 검증 및 후속 작업을 추적한다.                                         |

현재 관리 대상 하위 디렉터리는 없다.

## 각 구성 요소의 역할

- 기능 명세는 제품이 해야 할 일을 정의하고 API·DB·페이지 명세의 상위 비즈니스 근거가 된다.
- API 명세는 백엔드와 프론트엔드 사이의 공개 HTTP 계약을 정의한다.
- DB 명세는 기능 상태와 소유권·보존 규칙을 영속화하는 목표 데이터 계약을 정의한다.
- 페이지 명세는 기능과 API를 사용자가 수행하는 route·화면·E2E 흐름으로 연결한다.
- 기술 스택 명세는 네 문서를 구현할 때 지켜야 할 아키텍처와 품질·보안 제약을 정의한다.

## P0 승인 기준선과 결정 추적

2026-07-18 승인된 P0 제품 계약은 이 디렉터리의 다섯 기준 명세가 공동으로 정의한다. [`../design/p0-contract-decision-proposal.md`](../design/p0-contract-decision-proposal.md)는 결정 과정과 승인 근거를 보존하는 기록이며 활성 계약이 아니다. 아래 표는 결정 기록의 식별자를 중복 정책 서술 없이 활성 명세에 연결한다.

| 결정 | 활성 계약 위치                                                                                                                                                |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D-01 | [`functional.md`](functional.md) JOB-001–002, [`api.md`](api.md) 2·7장, [`db.md`](db.md) 2·5장, [`page.md`](page.md) 7장                                      |
| D-02 | [`api.md`](api.md) 1.6·2장, [`db.md`](db.md) 2·10장, [`tech_stack.md`](tech_stack.md) 8–9장                                                                   |
| D-03 | [`api.md`](api.md) 1·3–12장, [`page.md`](page.md) 1·4–13장                                                                                                    |
| D-04 | [`api.md`](api.md) 1.1장, [`db.md`](db.md) 1장, [`tech_stack.md`](tech_stack.md) 7장                                                                          |
| D-05 | [`functional.md`](functional.md) AUTH-004·DOC-004·CL-004, [`api.md`](api.md) 4·6·8장, [`db.md`](db.md) 4·6·11–12장                                            |
| D-06 | [`api.md`](api.md) Document DTO·6장, [`db.md`](db.md) 4.1장, [`tech_stack.md`](tech_stack.md) 6.3장                                                           |
| D-07 | [`api.md`](api.md) 1.5장, [`db.md`](db.md) 9.1장, [`tech_stack.md`](tech_stack.md) 9–10장                                                                     |
| D-08 | [`functional.md`](functional.md) SYS-001–002, [`api.md`](api.md) 11장, [`db.md`](db.md) 9.2–9.3장, [`tech_stack.md`](tech_stack.md) 10장                      |
| D-09 | [`functional.md`](functional.md) SYS-003, [`api.md`](api.md) Agent Run DTO·12장, [`db.md`](db.md) 10장, [`tech_stack.md`](tech_stack.md) 9장                  |
| D-10 | [`functional.md`](functional.md) CL-001–006, [`api.md`](api.md) 3·8장, [`db.md`](db.md) 6장, [`page.md`](page.md) 8·11장                                      |
| D-11 | [`functional.md`](functional.md) INT-001–004, [`api.md`](api.md) 9장, [`db.md`](db.md) 7장, [`page.md`](page.md) 9장                                          |
| D-12 | [`functional.md`](functional.md) INT-005–006, [`api.md`](api.md) 10장, [`db.md`](db.md) 8장, [`page.md`](page.md) 10장, [`tech_stack.md`](tech_stack.md) 10장 |
| D-13 | [`functional.md`](functional.md) AUTH-001–004·PROF-001, [`api.md`](api.md) 1·4장, [`db.md`](db.md) 3·11장, [`page.md`](page.md) 1·3·13–14장                   |
| D-14 | [`functional.md`](functional.md) JOB-001–005, [`api.md`](api.md) 7장, [`db.md`](db.md) 5장, [`page.md`](page.md) 7장                                          |
| D-15 | [`functional.md`](functional.md) DOC-002–003·SYS-002, [`api.md`](api.md) 6장, [`db.md`](db.md) 4·9장, [`tech_stack.md`](tech_stack.md) 8·10장                 |
| D-16 | [`functional.md`](functional.md) CL-003·INT-001–004, [`db.md`](db.md) 4.3·10장, [`tech_stack.md`](tech_stack.md) 4.4·6.2·8–9장                                |
| D-17 | [`api.md`](api.md) 4–12장, [`page.md`](page.md) 1·4·7·10–11·15장                                                                                              |
| D-18 | [`functional.md`](functional.md) CL-007, [`api.md`](api.md) 8장, [`db.md`](db.md) 6.1장, [`page.md`](page.md) 8·11장                                          |

Gate 항목은 다음 활성 계약 위치에서 닫힌다. 표의 A-1–A-6, B-1–B-5, C-1–C-5는 결정 기록의 Gate A–C 행 순서를 보존한 추적 식별자다.

| Gate                                       | 닫힌 계약 위치                                                                                                                     |
| ------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| A-1 품질·질문·version source enum          | [`api.md`](api.md) 1.6·2장, [`db.md`](db.md) 2장, [`tech_stack.md`](tech_stack.md) 8–9장                                           |
| A-2 endpoint DTO·validation·version·filter | [`api.md`](api.md) 1·3–12장, [`page.md`](page.md) 14장                                                                             |
| A-3 CSRF/signup/login·탈퇴                 | [`functional.md`](functional.md) AUTH-001–004, [`api.md`](api.md) 1.1·4장, [`db.md`](db.md) 3·11장                                 |
| A-4 cover letter editor·finalization       | [`functional.md`](functional.md) CL-003–006, [`api.md`](api.md) 3·8장, [`db.md`](db.md) 6장                                        |
| A-5 ARCHIVED 진입·복귀                     | [`functional.md`](functional.md) CL-007, [`api.md`](api.md) 8장, [`db.md`](db.md) 6.1장, [`page.md`](page.md) 8·11장               |
| A-6 mock idempotency·Agent detail·SSE      | [`api.md`](api.md) 3·10–11장, [`db.md`](db.md) 8–9장, [`page.md`](page.md) 10·12장                                                 |
| B-1 tenant composite integrity             | [`api.md`](api.md) 1.1장, [`db.md`](db.md) 1장, [`tech_stack.md`](tech_stack.md) 7장                                               |
| B-2 idempotency·outbox·lease·cancel        | [`api.md`](api.md) 1.5·11장, [`db.md`](db.md) 4.5·9장, [`tech_stack.md`](tech_stack.md) 10장                                       |
| B-3 삭제·SOURCE_DELETED·version 보존       | [`functional.md`](functional.md) DOC-004·CL-004, [`api.md`](api.md) 6·8장, [`db.md`](db.md) 4·6·11–12장                            |
| B-4 research/source provenance cardinality | [`functional.md`](functional.md) INT-001–003, [`api.md`](api.md) 9장, [`db.md`](db.md) 7장                                         |
| B-5 embedding model·dimension              | [`db.md`](db.md) 4.3·12–13장, [`tech_stack.md`](tech_stack.md) 6.2장                                                               |
| C-1 retry·WAITING_USER·run identity        | [`functional.md`](functional.md) SYS-001–002, [`api.md`](api.md) 11장, [`db.md`](db.md) 9장                                        |
| C-2 model mapping·가격·reserve             | [`functional.md`](functional.md) SYS-003, [`api.md`](api.md) 1.6·12장, [`db.md`](db.md) 10장, [`tech_stack.md`](tech_stack.md) 9장 |
| C-3 미승인 chunk                           | [`functional.md`](functional.md) CL-003·INT-001, [`db.md`](db.md) 4.3장, [`tech_stack.md`](tech_stack.md) 8.2장                    |
| C-4 score rubric·source coverage           | [`functional.md`](functional.md) JOB-004·INT-002, [`api.md`](api.md) 7·9장, [`db.md`](db.md) 5.4·7장                               |
| C-5 동기 mock turn                         | [`functional.md`](functional.md) INT-005–006, [`api.md`](api.md) 10장, [`db.md`](db.md) 8장, [`tech_stack.md`](tech_stack.md) 10장 |

## 다른 디렉터리와의 의존 관계

- [`../../AGENTS.md`](../../AGENTS.md)는 제품 계약 충돌 시 이 디렉터리를 코드 작업 규칙보다 우선하도록 정한다.
- `backend/`는 API·DB·기능·기술 명세를 구현하고, `frontend/`는 페이지·API·기능·기술 명세를 소비한다.
- [`../../compose.yaml`](../../compose.yaml)과 환경 설정은 DB 및 기술 스택 명세의 로컬 인프라 선택을 반영한다.
- Codex의 작업 방식과 상태 추적은 [`../agent-rules/`](../agent-rules/)에서 별도로 관리한다.

## 변경 시 주의사항

- 명세 변경을 구현 완료로 기록하지 않는다. 실제 구현 여부와 검증 결과는 관련 코드 디렉터리와 이 디렉터리의 `progress.md`에서 추적한다.
- 기능의 상태, 필드, 식별자 또는 흐름을 바꾸면 API, DB, 페이지, 기술 명세의 교차 영향을 함께 확인한다.
- `api.md`는 성공 DTO 직접 반환과 실제 HTTP 상태 코드, 공통 오류 필드를 요구한다. 공통 응답·예외 구조를 적용할 때 이 공개 계약을 임의로 바꾸지 않는다.
- 사용자 소유 데이터 격리, 개인정보 최소 전송, 비용 상한, 출처 추적 같은 공통 원칙을 하위 명세에서 서로 다르게 정의하지 않는다.
- 확정되지 않은 구현 아이디어나 작업 이력은 기준 계약에 섞지 않고 `progress.md` 또는 관련 작업 규칙에 기록한다.

## 관련 규칙 및 문서

- [Codex 최상위 지침](../../AGENTS.md)
- [상위 문서 영역 안내](../index.md)
- [문서 추적 규칙](../agent-rules/documentation-tracking.md)
- [백엔드 공통 응답·예외 규칙](../agent-rules/backend-response-exception.md)
- [명세 진행 상황](progress.md)
- [프로젝트 전체 진행 상황](../../progress.md)
