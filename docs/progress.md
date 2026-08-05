# Progress

## Overview

P0 승인 제품 명세 5종, 전체 시스템 설계·구현 계획·승인 결정 기록, Codex 작업 규칙 6종과 최신순 Session 기반 계층형 추적 문서가 구성되어 있다. P0–P8은 완료됐고 P8.5는 `IMPLEMENTED_NOT_LIVE_VERIFIED`, P8.5-V는 사용자 검증 대기다. P8.6–P8.9-A는 P9 이전 운영 기반으로 계획됐고 P9는 이 선행 단계가 완료될 때까지 차단된다.

## [2026-08-05] Session Summary (README 서비스 소개 재작성용 화면 자산 영역 추가)

- What was done:
  - `docs/assets/`를 신설해 루트 README 서비스 소개에 사용할 실제 화면 캡처 15종과 추적 문서를 관리하도록 했고, `docs/index.md`의 하위 디렉터리 표에 반영했다.
- Key decisions:
  - 문서 전용 정적 자산만 두고 자동 생성 test artifact는 옮기지 않는다. 캡처는 실제 사용자·운영 데이터 없이 결정론적 fixture로만 생성한다.
- Issues encountered:
  - None.
- Validation:
  - 생성한 캡처와 README 링크 경로 일치를 직접 확인했다.
- Next steps:
  - 화면 계약이 바뀌면 캡처와 README 설명을 함께 갱신한다.

## [2026-08-05] Session Summary (공고 반기·기간 필터 명세 동기화)

- What was done:
  - 공고 등록 반기 분류와 목록 기간 filter의 기능·API·DB·페이지 계약을 서버·클라이언트 구현에 맞춰 동기화했다.
- Key decisions:
  - 등록 시각의 서울 날짜를 시작 기준으로 사용하고 owner 보유 범위 밖 preset은 화면에 표시하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Backend·Frontend 전체 표준 검증 결과와 명세를 대조했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (Landing motion·공고 분석 화면 계약 동기화)

- What was done:
  - page 명세에 Landing motion·reduced-motion, 조건별 5개 pagination, disclosure target과 모바일 2열 판단 요약 계약을 반영하고 UI/UX 재설계 메모에 reference 방향과 검증 한계를 기록했다.
- Key decisions:
  - 외부 reference의 구조와 실제 반복 motion을 확인하되 그대로 복제하지 않고 Hiresemble의 지원 준비 흐름을 설명하는 효과로 번역한다.
- Issues encountered:
  - 인앱 Browser가 사용 가능한 browser를 반환하지 않아 Playwright CLI 실제 Chromium fallback으로 reference 세 곳을 검증했다.
- Validation:
  - 명세를 Vue 구현, 집중 20 tests, 실제 Chromium pagination·responsive geometry와 Frontend 전체 282 tests·build 결과에 대조했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (공고 분석 결과 표시 계약 개선)

- What was done:
  - 페이지 명세에 점수 반올림, 요약 우선 상세 공개, 강점·보완 insight, 조건 filter와 날짜 중심 분석 기록을 반영했다.
- Key decisions:
  - 공개 API·점수 계산 계약은 변경하지 않고 사용자 화면의 정보 구조만 구체화한다.
- Issues encountered:
  - None.
- Validation:
  - 구현·component test·Chromium desktop/mobile와 Frontend 전체 check 결과에 문서를 대조했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 표시 문구·동의 상세 계약)

- What was done:
  - 페이지 명세의 회원가입 표시 계약을 간결한 비밀번호 세 문장과 전문 용어가 없는 동의 상세로 갱신했다.
- Key decisions:
  - 검증·보안 내부 계약은 유지하고 사용자 화면에는 행동과 결과를 중심으로 안내하도록 구분했다.
- Issues encountered:
  - 화면 자동화 완주는 locator 중복과 재검증 상한으로 `NOT_VERIFIED`이다.
- Validation:
  - 문서를 구현·Vitest 20건·Frontend 전체 67 files/279 tests·build에 대조했다.
- Next steps:
  - 다음 Chromium 검증 회차에 페이지 계약의 desktop/mobile 완주를 확인한다.

## [2026-08-04] Session Summary (가입·온보딩·공고 마감 입력 계약)

- What was done:
  - 기능·API·페이지 명세에 새 비밀번호 조합, blur 오류 표시, 온보딩 지원 자격과 30분 단위 공고 마감 입력을 반영했다.
- Key decisions:
  - eligibility와 Job의 기존 공개 API·DB 계약은 유지하고 첫 입력 화면과 변환 UX만 구체화했다.
- Issues encountered:
  - None.
- Validation:
  - Backend·Frontend 구현, OpenAPI 계약 테스트와 전체 module check 결과에 문서를 대조했다.
- Next steps:
  - None.

## [2026-08-04] Session Summary (회원가입 검증·동의 상세 계약)

- What was done:
  - 기능·페이지 명세를 실제 UTF-8 비밀번호 규칙과 이용약관·개인정보·AI 처리 상세 Modal 계약에 맞췄다.
- Key decisions:
  - 기존 API·DB 동의 timestamp와 10..72 byte 계약은 유지하고 화면 설명만 실제 구현 범위로 구체화했다.
- Issues encountered:
  - 운영 주체·문의처·국외 이전 계약처럼 저장소에서 확정할 수 없는 법적 고지 정보는 제품 문구로 추측하지 않았다.
- Validation:
  - 공식 개인정보 동의 고지 항목과 OpenAI API 데이터 제어 문서를 확인하고 Frontend 구현·회귀에 대조했다.
- Next steps:
  - 운영 배포 전 확정된 사업자와 Provider 계약 정보로 개인정보 처리방침을 법률 검토한다.

## [2026-08-02] Session Summary (공고 분석·문서 소재 한국어 표시 계약)

- What was done:
  - 기능 명세에 공고 분석과 문서 추출 소재의 한국어 사용자 문장 계약을 추가했다.
  - 페이지 명세에 결과 hero 고정 문구와 내부 공고 경로의 사용자용 치환 정책을 반영했다.
- Key decisions:
  - 저장 구조·API 계약은 유지하고 prompt 및 presentation 동작만 명확히 했다.
- Issues encountered:
  - None.
- Validation:
  - 기능·페이지 명세를 Backend prompt/validator와 Frontend 표시 회귀에 대조했다.
- Next steps:
  - None.

## [2026-08-01] Session Summary (이미지 공고 extraction v3 문서 동기화)

- What was done:
  - 기능·페이지·기술·아키텍처·구현 계획에 trusted ref, Provider parity, retry 승격, WebP와 aggregate 정책을 반영했다.
- Key decisions:
  - migration/OpenAPI는 유지하고 실제 Provider/live 상태를 재판정하지 않는다.
- Issues encountered:
  - None.
- Validation:
  - Backend 70 suites/491 tests, Frontend 61/243, P5 Chromium 5/5와 Markdown/diff 검증 통과.
- Next steps:
  - live Provider 검증은 별도 사용자 경계다.

## [2026-08-01] Session Summary (문서 filtering·terminal partial 계약 동기화)

- What was done:
  - 기능·DB·기술·아키텍처·구현 계획·Provider runbook에 candidate rejection 성공 계약과 workflow-owned terminal policy를 반영했다.
- Key decisions:
  - 공개 API·DB와 migration은 유지하고 safe count는 기존 step output에만 기록한다.
- Issues encountered:
  - terminal 보정 뒤 실제 Provider 재호출은 수행하지 않았다.
- Validation:
  - Backend 68 suites/466 tests와 문서 상태를 대조했고 Provider 호출은 0회다.
- Next steps:
  - 문서 terminal classification을 live 1회 확인한 뒤 남은 P8.5-V 수직 흐름을 진행한다.

## [2026-08-01] Session Summary (structured semantic 계약·live 상태 동기화)

- What was done:
  - 활성 명세·아키텍처·구현 계획·Provider runbook에 local ref, metadata 제거, phase/reason, retry/cost와 최신 live 실패를 반영했다.
- Key decisions:
  - API/DB 계약은 유지하고 P8.5/P8.5-V 상태를 올리지 않는다.
- Issues encountered:
  - 과거 live invalid field와 truncation은 `NOT_VERIFIED`다.
- Validation:
  - 코드의 68 suites/459 tests와 문서의 상태·명령을 대조했고 Provider 호출은 0회다.
- Next steps:
  - persistent Chat cap을 우회하지 않고 별도 승인 뒤 synthetic Chat 1회→document vertical 1회 순서로 수행한다.

## [2026-08-01] Session Summary (OpenAI strict schema 수정 문서 동기화)

- What was done:
  - 기술 명세·아키텍처·구현 계획·Provider runbook을 중앙 strict schema 검증, Provider 전용 metadata/TipTap 경계와 실제 문서 실패 증거에 맞췄다.
- Key decisions:
  - 공개 API/DB 계약은 유지하고 offline 구현 완료와 live Chat/vertical 성공을 분리한다.
- Issues encountered:
  - 당시 raw Provider 오류 metadata가 없어 원인은 `HIGH_CONFIDENCE`로 기록했다.
- Validation:
  - Backend 68 suites/452 tests 통과와 수정 뒤 실제 Provider 호출 0회를 반영했다.
- Next steps:
  - 사용자 bounded Chat 1회와 문서 vertical 1회 뒤 P8.5-V 상태를 갱신한다.

## [2026-08-01] Session Summary (Provider 연결 보정 문서 동기화)

- What was done:
  - V14 embedding 정책, OpenAI `/v1` base URL과 bounded smoke 결과를 설계·DB·운영 문서에 반영했다.
- Key decisions:
  - Tavily 성공만으로 P8.5를 완료하지 않고 OpenAI capability는 quota 복구 후 재검증한다.
- Issues encountered:
  - Chat·Embedding은 `insufficient_quota`로 성공하지 못했다.
- Validation:
  - 문서의 live call 수와 미래 migration 번호를 V14 기준으로 대조했다.
- Next steps:
  - OpenAI quota 복구 뒤 P8.5-V 결과를 다시 갱신한다.

## [2026-08-01] Session Summary (P8.5 이후 문서 체계 재설계)

- What was done:
  - 설계·활성 명세·운영·루트 안내 문서를 P8.5 실제 상태와 P8.5-V–P10-C 단계 그래프에 맞춰 동기화했다.
  - 운영 계약 결정 문서와 usage metering·Backoffice 계획 문서를 새로 연결했다.
- Key decisions:
  - Provider 비용 예산, 제품 기능 한도, 사용자 사용량·과금 가능 unit, 실제 결제·구독을 서로 다른 책임으로 유지한다.
  - 미래 API·route·migration은 `PLANNED`로만 기록하고 현재 OpenAPI 63 paths/84 operations 기준선은 변경하지 않는다.
- Issues encountered:
  - 실제 Provider 호출 0회가 확인되어 P8.5는 완료가 아닌 실호출 미검증 상태로 유지했다.
- Validation:
  - 변경 Markdown 형식·상대 링크·추적 문서 형식·Compose·diff·경로·비밀 패턴 검사를 통과했다.
- Next steps:
  - 사용자가 P8.5-V를 수행하고, 첫 코드 구현은 P8.6에서 시작한다.

## [2026-08-01] Session Summary (P8.5 Provider 운영 문서)

- What was done:
  - 구현 계획·기술/DB 명세와 Provider 활성화 운영 문서를 local real/local-offline/test 격리 계약에 맞췄다.
- Key decisions:
  - configuration readiness와 bounded live verification 상태를 분리해 기록한다.
- Issues encountered:
  - None.
- Validation:
  - 코드·profile·V13·실행 결과와 문서 내용을 대조했다.
- Next steps:
  - live verification 통과 후 P8.5 상태를 DONE으로 전환한다.

## [2026-07-31] Session Summary (최종 학력·헤더 닉네임 계약 동기화)

- What was done:
  - 네 기준 명세에 상단 닉네임 Modal과 서버 계산 최종 학력의 API·DB·화면 경계를 반영했다.
- Key decisions:
  - 학력 hierarchy와 tie-break를 서버·migration·명세에서 같은 순서로 유지한다.
- Issues encountered:
  - None.
- Validation:
  - V11 upgrade test, 개발 DB 보정 결과와 Frontend/Backend 전체 검증을 대조했다.
- Next steps:
  - None.

## [2026-07-31] Session Summary (대외활동·Agent Run 삭제 계약 동기화)

- What was done:
  - 기능·API·DB·페이지 명세에 학력 evidence 제외, 문서 AI 근거 전용 승인·거절·confidence 의미와 terminal Agent Run 개별/선택 삭제를 반영했다.
- Key decisions:
  - 학력 구조화 정보는 유지하되 대외활동 evidence와 문서 추출 대상에서 제외하고, 작업 내역 삭제는 audit 보존 soft delete로 계약화했다.
- Issues encountered:
  - None.
- Validation:
  - 구현 코드·OpenAPI 73 operations/53 paths, V9~V10 schema와 네 기준 명세 표현을 대조했다.
- Next steps:
  - None.

## [2026-07-28] Session Summary (프로필 사용자 용어 계약 동기화)

- What was done:
  - 기능·페이지 명세의 졸업 예정일을 졸업(예정)일로, 사용자 노출 표시 이름을 닉네임으로 바꿨다.
- Key decisions:
  - API·DB field 이름과 호환성은 유지하고 사용자 의미만 명확히 했다.
- Issues encountered:
  - None.
- Validation:
  - Frontend check의 Markdown Prettier와 명세 잔여 용어 검색이 통과했다.
- Next steps:
  - 향후 설정 화면 구현 시 닉네임 용어를 그대로 사용한다.

## [2026-07-23] Session Summary (backend package 문서 체계 동기화)

- What was done:
  - 백엔드 규칙·설계와 새 책임 package 44개의 `index.md`·`progress.md`를 실제 Java 구조에 맞춰 연결했다.

- Key decisions:
  - 새 source directory마다 책임·의존성·주의사항과 검증 이력을 기록하고 상위 문서에는 관계만 요약했다.
  - 활성 `docs/spec/**` 계약은 변경되지 않아 수정하지 않았다.

- Issues encountered:
  - 새 디렉터리와 기존 상위 문서의 상대 링크를 함께 정리해야 했다.

- Validation:
  - Java 237개의 package↔path, 내부 import, 구 FQCN, wildcard·중복 import, package-private 교차 참조 검사가 모두 0건으로 통과했다.
  - 엄격한 UTF-8 decode·replacement 문자·BOM과 HEAD 대비 exact/semantic 본문 불일치가 모두 0건이며 `git diff --check HEAD`가 통과했다.
  - Docker가 없어 지침에 따라 Gradle·Testcontainers·애플리케이션 실행은 하지 않았고 runtime은 `NOT_VERIFIED`다.

- Next steps:
  - Docker 사용 가능한 개발 또는 CI 환경에서 `Set-Location backend; .\gradlew.bat check`를 실행한다.

## [2026-07-19] Session Summary (P4 구현 계획·추적 문서 동기화)

- What was done:
  - P4 실제 구현 범위와 Document·AI·Frontend·migration·E2E 추적 문서를 상호 링크했다.
- Key decisions:
  - 활성 `docs/spec/**`는 계약 충돌이 없어 수정하지 않고 구현 계획과 상태 문서만 갱신했다.
- Issues encountered:
  - 최초 Validator의 Agent Run Document filter MAJOR를 owner resolver와 직접 회귀 테스트로 한 차례 보정했다.
- Validation:
  - 상대 링크·progress 표준 필드·P5 이후 미착수 표현을 최종 문서 감사 대상으로 포함했다.
  - 최종 read-only Validator가 문서와 실제 상태를 포함해 `PASS`했다.
- Next steps:
  - P5/P6 착수 시 P4의 typed resource·masked-only·forward migration 경계를 보존한다.

## [2026-07-18] Session Summary (P0 제품 계약 문서 기준선 확정)

- What was done:
  - 제품 소유자 승인을 다섯 기준 명세에 동기화하고 spec 결정 추적, 설계 승인 상태와 구현 계획을 한 문서 계층으로 정렬했다.
  - proposal은 승인 근거 기록으로 전환하고 활성 계약이 `spec/`에만 존재하도록 책임 경계를 갱신했다.

- Key decisions:
  - 기능·API·DB·페이지·기술 계약의 P0 기준선은 문서 버전 1.1이며 D-01–D-18과 Gate A–C가 모두 닫혔다.
  - 문서 기준선 확정과 코드 구현 완료를 분리하고 P1은 공통 HTTP·인증 기반부터 시작한다.

- Issues encountered:
  - 기존 설계와 proposal에는 승인 전 충돌·권장 문구가 남아 있어 활성 계약과 역사 기록의 precedence를 명시해야 했다.
  - 명세 간 조건부 응답, 상태 복구, 공개/내부 필드 경계를 정규화했지만 비즈니스 파일이나 기존 V1 migration은 수정하지 않았다.

- Validation:
  - 전문 분석 3개는 읽기 전용 `DONE`, 독립 validator는 다섯 명세의 정책·Gate·상태·API·DB·페이지 정합성을 `PASS`로 반환했다.
  - Markdown 표·링크·enum·endpoint·field bound·quality·idempotency와 Prettier·`git diff --check`를 검사했다. 문서 전용이라 backend/frontend build는 실행하지 않았다.

- Next steps:
  - P1 구현 작업에서 OpenAPI·migration·code를 활성 명세와 대조하고 Fake 기반 테스트로 공통 기반을 먼저 고정한다.

## [2026-07-18] Session Summary (P0 계약 제안서 최종 감사)

- What was done:
  - `design/p0-contract-decision-proposal.md`의 공개 계약·DB 수명주기·AI runtime·route projection을 기준 명세와 의미 기반으로 재감사하고 차단 계약만 보정했다.
  - 수정 전 validator `NEEDS_CHANGES`, 보정 후 새 validator `PASS`를 확인해 제안서를 `READY_FOR_OWNER_REVIEW`로 전환했다.

- Key decisions:
  - URL과 memo 상한을 각각 2000자로 통일하고, 면접 답변 source를 `USER_EDITED` 전용 타입으로 분리했다.
  - 탈퇴 idempotency를 제거하고 embedding model·dimension 및 profile 완료 정책을 제품 승인 항목으로 분리했다.

- Issues encountered:
  - 최초 감사에서 품질 request 부재, session 폐기 뒤 replay 불가, 근거 없는 vector 차원, 취소 후 PENDING과 내부 DTO 노출이 승인 차단 문제로 확인됐다.
  - 기준 명세·코드·migration·설정은 수정하지 않고 제안서 안에서만 해소했다.

- Validation:
  - 최종 validator가 D-01~~D-18, Gate A~~C, 상태·enum·DTO·API·DB·제품 결정과 변경 경로를 `PASS`했다.
  - Markdown 표·링크·중복·상한·allowlist·상태 전이, Prettier와 `git diff --check`를 검사했다. 문서 작업이라 build는 생략했다.

- Next steps:
  - 제품 소유자 승인 후 다섯 기준 명세를 동기화하고 proposal을 결정 기록으로 전환한다.

## [2026-07-18] Session Summary (P0 계약 결정 제안서 통합)

- What was done:
  - 설계의 D-01–D-18과 Gate A–C를 다섯 기준 명세에 연결한 `design/p0-contract-decision-proposal.md`를 만들고, 설계 index·진행 기록·링크와 기존 Markdown 표·범위 표기를 정리했다.
  - API·DB·AI runtime·route projection의 구현 전 기준선과 제품 소유자 질문 6개를 작성했다.

- Key decisions:
  - 11개 기술 항목은 단일 권장안, 제품 경험·보존·비용 선택이 필요한 7개 항목은 승인 필요로 분류했다.
  - 이 제안서는 기준 명세가 아니며 사용자 승인과 명세 동기화 전에는 P0 완료나 구현 근거로 확정하지 않는다.

- Issues encountered:
  - read-only validator의 두 차례 판정은 모두 `NEEDS_CHANGES`였고, 첫 4개 차단점은 재검증에서 해소 확인됐다.
  - 두 번째에 새로 발견된 DTO 상한·출처 enum·path 불일치는 루트가 정합화했으나 규칙상 세 번째 validator를 실행하지 않아 최종 보정분은 독립 미검증이다.

- Validation:
  - 최종 루트 검사에서 D 18행, Gate A~C, 기준 endpoint 95개, 필수 타입 18개, 제품 질문 6개, 표 열 수·링크·Prettier·`git diff --check`를 확인했다.
  - 코드·테스트·dependency·migration·설정과 `spec/**`는 변경하지 않았다.

- Next steps:
  - 제품 승인 후 `spec/**`를 동기화하고 독립 계약 검증을 통과시킨 다음 구현 단계로 이동한다.

## [2026-07-18] Session Summary (명세 기반 전체 시스템 설계와 구현 계획 작성)

- What was done:
  - `docs/design/`을 만들고 전체 architecture·도메인·DB/API/page 연결, 주요 업무·AI workflow와 보안·비동기 설계를 작성했다.
  - 계약 결정부터 AC-01~~13까지의 P0~~P10 구현 순서, 단계별 완료 조건과 개발·검증 에이전트 파일 소유권을 작성했다.
  - `docs/index.md`와 상위 저장소 안내를 갱신해 기준 명세, 파생 설계, 작업 규칙의 책임을 분리했다.

- Key decisions:
  - 기준 계약은 `spec/`, 파생 구현 구조는 `design/`, Codex 작업 절차는 `agent-rules/`에서 관리한다.
  - 명세 충돌·누락은 권장안과 구현 보류 범위를 함께 기록하고 P0 승인 전 확정 사실로 취급하지 않는다.

- Issues encountered:
  - 독립 validator가 최초 설계에서 자기소개서 목록·`ARCHIVED`, 조사 재시도, 면접 준비 목록의 직접 추적 누락과 Markdown format 차이를 발견했다.
  - 세 보조 MVP 흐름을 한 차례 보완하고 format을 적용했다.

- Validation:
  - 세 전문 분석 에이전트와 독립 validator가 모든 `docs/spec/*.md`와 설계 문서를 읽기 전용으로 교차 검증했다.
  - 정적 재검사에서 AC 13개, 필수 5필드를 가진 이슈 18개, 변경 문서 상대 링크와 `git diff --check`가 통과했다.
  - 변경 Markdown의 Prettier 검사가 통과했고 비즈니스 코드·dependency·migration·API·UI는 변경하지 않았다.

- Next steps:
  - 구현 전에 설계의 P0 결정 게이트를 사용자 승인으로 닫고 영향받는 명세를 일관되게 갱신한다.

## [2026-07-17] Session Summary (Session 기반 작업 이력 문서 체계 표준화)

- What was done:
  - 전체 관리 대상 `progress.md`를 단일 Overview와 최신순 Session Summary 구조로 전환하고 기존 이력을 보존했다.
  - 문서 작업 절차와 추적 규칙에 역할별 최신 5개 기본 조회, 제한 과거 검색과 루트 관리자 갱신 책임을 반영했다.

- Key decisions:
  - 제품 계약인 `docs/spec/` 원문은 변경하지 않고 작업 이력과 Codex 운영 규칙만 갱신했다.
  - 상위 문서에는 통합 영향, 하위 규칙 문서에는 구체적인 조회·작성 책임을 기록해 중복을 줄였다.

- Issues encountered:
  - 기준 파일 자체에는 중간 Overview/Notes, 표준 필드 누락과 날짜 역전이 있어 제목 패턴 외에는 사용자 명시 표준을 우선했다.

- Validation:
  - 관리 대상 22개 문서의 구조·필드·정렬과 기존 21개 문서의 168개 섹션 보존을 정적으로 검증했다.
  - 변경 Markdown 전체의 Prettier 검사가 통과했다.

- Next steps:
  - 이후 작업은 관련 기록의 최신 5개만 기본 조회하고 새 Session을 Overview 바로 아래에 추가한다.

## [2026-07-17] Session Summary (제품 명세 및 Codex 규칙 문서 체계 구축)

- What was done:
  - 당시 구현 상태:
    제품 명세 5종, Codex 작업 규칙 6종과 계층형 추적 문서가 구성되어 있다.
  - 완료된 작업:
    - 기능, API, DB, 페이지, 기술 스택 명세 작성
    - 문서 영역의 책임과 명세/작업 규칙 경계 정의
    - 레퍼런스 응답·예외 구조와 현재 API 계약 차이 분석
    - `agent-rules/` 세부 규칙 6종과 21개 관리 대상의 문서 추적 계층 생성
  - 당시 진행 중인 작업:
    없음. 이번 문서 구조 초기화는 완료됐다.

- Key decisions:
  - 제품 계약은 `spec/`, Codex의 수행 절차는 `agent-rules/`에서 관리한다.
  - 분석 결과는 적용 규칙과 함께 기록하되 레퍼런스 코드를 복제하지 않는다.

- Issues encountered:
  - 레퍼런스의 공통 성공 envelope는 `spec/api.md`의 직접 반환 계약과 충돌한다. API 명세를 우선하는 적용 규칙이 필요하다.
  - 명세는 구현 계획이며 현재 구현 완료를 의미하지 않는다.

- Validation:
  - 5개 명세 파일의 존재와 주요 공통 계약을 확인했다.
  - 55개 Markdown 파일의 저장소 내부 상대 링크와 42개 추적 문서의 필수 구조를 검사했다. 결과: 성공.
  - 신규 Markdown 49개를 Prettier로 검사했으며 최초 형식 차이를 수정한 뒤 모두 통과했다.

- Next steps:
  - 기능 구현 시 명세와 실제 API/OpenAPI 간 계약 검증 자동화
  - 주요 기술 결정에 대한 ADR 도입 여부 검토
