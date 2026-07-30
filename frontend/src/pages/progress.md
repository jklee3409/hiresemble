# Progress

## Overview

P1 인증부터 P7 Cover Letter 목록·공고 context·canonical editor, 현재 route 기반 dashboard와 전용 404를 일관된 제품 UI로 관리한다.

## [2026-07-30] Session Summary (P7 editor 409 비교·재적용 보강)

- What was done:
  - 문항 field, 전체 정렬, current answer content와 lifecycle 상태를 operation별 최신 server snapshot과 최초 사용자 snapshot으로 비교한다.
  - 질문·정렬·답변 충돌의 재적용과 취소를 각각 검증하고 actual E2E에 실제 문항 409를 추가했다.
- Key decisions:
  - 재적용은 사용자의 명시적 버튼 동작이며 refetch나 Vue Query mutation이 자동으로 overwrite하지 않는다.
- Issues encountered:
  - answer 취소 시 Vue Proxy를 직접 복제하지 않고 canonical plain document로 동기화하도록 보정했다.
- Validation:
  - page 대상 409 tests와 전체 Frontend 53 files/211 tests, P7 actual Chromium 1/1이 통과했다.
- Next steps:
  - 최종 read-only validator 재판정을 기다린다.

## [2026-07-30] Session Summary (P7 자기소개서 세 화면)

- What was done:
  - 전체 목록, 공고별 상태/생성 진입과 문항 navigator·TipTap·근거·검증·version drawer를 갖춘 canonical editor를 구현했다.
  - question CRUD/order, generation partial result/retry, 명시적 save/restore/verify, warning acknowledgement/finalize와 archive read-only를 연결했다.
- Key decisions:
  - 공고 tab에 editor를 복제하지 않고 archived 상세은 mutation을 비활성화하며 조건부 unarchive만 제공한다.
- Issues encountered:
  - 실제 question maxLength number input parser 오류와 mutation UI race를 보정했다.
- Validation:
  - page/component tests, P7 actual 전체 시나리오와 1440/390px overflow가 통과했다.
- Next steps:
  - 독립 validator 판정을 반영한다.

## [2026-07-29] Session Summary (P6 공고별 분석 결과 페이지)

- What was done:
  - 분석 없음·선행 조건·진행·WAITING_USER·실패·성공·OUTDATED·history 상태를 단일 Job Analysis page에 구현했다.
  - Eligibility, fit score 안내, requirement, strength/gap, verified evidence와 criterion breakdown을 분리해 표시했다.
  - historical evidence의 현재 상태가 바뀌어도 분석 당시 결과를 유지하고 재분석 제외 상태를 텍스트로 표시한다.
- Key decisions:
  - 프로필 미완료는 경고만 표시하고 usable 공고 분석을 차단하지 않으며 OUTDATED 기존 결과도 숨기지 않는다.
- Issues encountered:
  - 실제 E2E의 같은 버전 제목 locator와 비공개 evidence GET assertion을 공개 계약에 맞게 보정했다.
  - 1차 validator의 historical detail 거부 finding을 canonical current evidence 상태 수용과 OUTDATED 안내로 보정했다.
- Validation:
  - Job Analysis component, 1440px/390px Chromium·keyboard·overflow와 Frontend 전체 169 tests가 통과했다.
- Next steps:
  - 수정된 actual P6 E2E assertion은 재검증 상한으로 아직 실행하지 않았다.

## [2026-07-28] Session Summary (지원 현황 Dashboard·기본 프로필 단일 편집 구조)

- What was done:
  - Dashboard를 사용자 이름 기반 제목, 핵심 빠른 작업, 실제 집계, 상태 기반 다음 할 일, 마감 임박 공고, 최근 활동과 신규 사용자 전용 시작 안내로 재구현했다.
  - Profile 기본 정보 화면을 기본 정보·자기소개·희망 조건의 세 section과 단일 저장 action으로 재구성하고 dirty·saving·success·error·409 conflict 상태를 분리했다.
  - Dashboard의 기존 사용자, 신규 사용자 이름 fallback, 부분 query 오류를 검증하는 unit test를 추가하고 Profile 저장·field error 회귀 test를 갱신했다.
- Key decisions:
  - Dashboard 최근 목록은 현재 query가 반환한 항목만 표시하고, 전체 수치는 pagination의 `totalElements`만 사용한다.
  - 서버 field error는 해당 control 가까이에 유지하고 저장 완료 뒤 version baseline을 갱신하되 auto-save는 도입하지 않았다.
- Issues encountered:
  - Cover Letter와 Mock Interview는 현재 연결 가능한 Dashboard API가 없어 가짜 최근 활동이나 수치를 만들지 않았다.
- Validation:
  - 관련 Vitest 6 files/25 tests와 전체 40 files/154 tests, TypeScript, production build 통과.
  - 1440px·390px Dashboard와 Profile 스크린샷에서 overflow, CTA 우선순위와 상태 구분을 확인했다.
- Next steps:
  - 전용 Dashboard 계약 구현 뒤 자기소개서·모의 면접·검증 경고 집계를 연결한다.

## [2026-07-28] Session Summary (Dashboard·필터·기본 정보 화면 완성도 보정)

- What was done:
  - Dashboard hero 제목을 desktop 한 줄, 390px 의미 단위 두 줄로 조정하고 장식과 설명의 충돌을 제거했다.
  - 자료·관심 공고 filter control 사이의 여백을 늘리고 프로필 기본 정보의 공통 정보·희망 조건을 번호, eyebrow, surface와 section divider로 구분했다.
  - 노란 프로필 작성 안내를 브랜드 blue soft surface로 교체하고 희망 조건 입력군의 구조적 rail을 추가했다.
- Key decisions:
  - 기존 form ID, label, mutation, query, route와 오류·conflict 동작은 유지했다.
- Issues encountered:
  - 첫 mobile 캡처에서 제목이 세 줄이 되어 한 차례 typography를 보정했다.
- Validation:
  - 1574px·390px Dashboard, 1440px·390px 기본 정보, 1600px 자료·공고 필터를 직접 검수했고 390px 가로 넘침이 없었다.
  - Frontend 전체 check와 fixture UI shell 3/3이 통과했다.
- Next steps:
  - 실제 데이터가 필요한 cross-stack 시나리오는 이번 visual-only 보정 범위에서 재실행하지 않았다.

## [2026-07-28] Session Summary (현재 전체 Page 정보 구조·Form 재설계)

- What was done:
  - 인증·onboarding·dashboard·7개 profile·documents·jobs·분석 기록·404에 Hiresemble Blue control과 B2C action copy를 적용했다.
  - 자료 등록은 dropzone→분류→분석, 공고 등록은 URL 우선→직접 입력 disclosure, 목록 filter는 mobile 접기 흐름으로 재구성했다.
- Key decisions:
  - Document·Job 상태 축, 201/202, idempotency, 409, SSE, ID·test selector와 입력값 보존을 유지했다.
- Issues encountered:
  - 실제 Document E2E는 upload API 일반 오류로 첫 시나리오가 timeout되어 완료하지 못했다.
  - 실제 Profile E2E는 현재 온보딩 문구까지 동기화했지만 완료율 text·progressbar strict locator 중복에서 중단됐다.
- Validation:
  - page component test, 전체 149 tests, fixture Playwright 5/5와 18개 화면 네 viewport 시각 검수가 통과했다.
- Next steps:
  - Profile 완료율 locator를 명시적으로 한정하고 실행 Backend 설정을 갱신한 뒤 actual pipeline을 재검증한다.

## [2026-07-28] Session Summary (프로필·자료 등록 화면 전문 서비스화)

- What was done:
  - 프로필을 지원 방향 brief와 단계형 form으로, 자료 등록을 파일 선택·분류·분석 안내 흐름으로 재구성했다.
  - 닉네임, 분석 기록과 졸업(예정)일을 전체 현재 route의 사용자 언어로 통일했다.
- Key decisions:
  - 기존 DOM ID·API mutation·route와 자유 입력 기능을 유지하고 정보 계층과 반응형 표현만 강화했다.
- Issues encountered:
  - 모바일에서는 sticky guide를 일반 흐름으로 바꾸고 file card action을 wrap해 overflow를 제거했다.
- Validation:
  - Page tests, Frontend 전체 145 tests와 390px Playwright 검증이 통과했다.
- Next steps:
  - 실제 장문 경력·파일명 데이터로 시각 밀도를 추가 확인한다.

## [2026-07-28] Session Summary (현재 Route B2C UX Writing 전면 적용)

- What was done:
  - 18개 사용자 route와 404의 제목, 설명, CTA, helper, loading·empty·error·success·conflict 문구를 사용자 결과와 다음 행동 중심으로 다시 작성했다.
  - Dashboard를 가상 KPI 없이 네 가지 실제 작업을 잇는 numbered path로 재구성했다.
  - 경험 정보의 원시 JSON 입력을 타입 보존 항목형 편집기로 바꾸고 자료 목록·상세에서 MIME type을 숨겼다.
- Key decisions:
  - `근거`는 문맥에 따라 경험 정보·자료에서 찾은 정보로 바꾸고 대표 학력은 `먼저 보여 줄 학력`으로 설명한다.
- Issues encountered:
  - 일부 성공·충돌 문장에 남은 `-습니다`형을 루트 시각·문구 감사에서 찾아 `-해요/-해 주세요`형으로 통일했다.
- Validation:
  - page component test와 18개 보호 route+404의 1440·390px 직접 진입·overflow smoke가 통과했다.
- Next steps:
  - Backend가 필요한 실제 데이터 밀도와 긴 파일명·공고명 검수는 actual E2E 환경에서 수행한다.

## [2026-07-27] Session Summary (현재 Route Page 정보 구조 개선)

- What was done:
  - 인증, onboarding, dashboard, 7개 profile route, Documents, Jobs, Agent Run과 404의 typography·form·action·state hierarchy를 개선했다.
  - Dashboard 개발 문구를 제거하고 실제 route 빠른 작업만 제공했으며 onboarding 마지막 단계는 구현된 문서 업로드 또는 추후 입력만 제공한다.
- Key decisions:
  - 가상 집계·최근 활동·미구현 analysis/cover-letter/interview/settings 화면과 API는 추가하지 않았다.
  - 기존 form ID, `data-testid`, accessible name, 상태별 CTA와 mutation/query 흐름을 보존했다.
- Issues encountered:
  - 구조화 profile의 반복 form은 하나의 generic page 안에 있어 동작을 분할하지 않고 공통 scoped style로 시각 일관성만 맞췄다.
- Validation:
  - 기존 literal DOM ID와 `data-testid` 누락 0건, page component와 전체 128 tests가 통과했다.
- Next steps:
  - cross-stack 환경에서 긴 실제 문서명·URL·공고 본문 조합의 수동 시각 검수를 보강한다.

## [2026-07-27] Session Summary (P5 Job 목록·등록·overview Page 구현)

- What was done:
  - 상태 tab·filter·pagination 목록, 201/202 생성과 편집·상태·retry·manual·delete 상세를 구현했다.
- Key decisions:
  - 업무/추출 badge를 분리하고 submittedAt 이력이 있는 CLOSED 공고를 표시한다.
- Issues encountered:
  - NEEDS_MANUAL_INPUT retry를 제거하고 수동 입력만 강조하도록 validator 보정했다.
- Validation:
  - page component test와 실제 Chromium Job E2E 5/5가 통과했다.
- Next steps:
  - P6 전까지 분석 버튼·가짜 page를 추가하지 않는다.

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
