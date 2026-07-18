# 기능 명세서

- 문서 버전: 1.1 (P0 승인 기준선)
- 기준일: 2026-07-18
- 대상: 핵심 MVP
- 사용자 역할: `USER`
- 공고 상태: `IN_PROGRESS`, `SUBMITTED`, `CLOSED`

---

## 1. MVP 사용자 여정

```text
회원 가입
→ 기본 프로필 입력
→ 이력서·포트폴리오 업로드
→ 추출된 근거 검토
→ 공고 URL 등록
→ 공고 분석 및 적합도 확인
→ 자기소개서 문항 등록
→ AI 초안 생성·검증
→ 사용자 직접 수정·버전 저장
→ 면접 정보 조사 및 예상 질문 생성
→ 답변 작성·피드백 또는 대화형 모의 면접
```

---

## 2. 공통 비즈니스 규칙

1. 모든 사용자 데이터는 로그인 사용자에게만 노출한다.
2. 사용자가 업로드하거나 입력한 사실과 외부 검색 결과를 구분한다.
3. AI가 추출한 사용자 근거는 사용자가 승인·수정·거절할 수 있다.
4. 자기소개서 생성은 승인된 사용자 근거를 우선 사용한다.
5. 출처가 없는 수치·역할·성과는 자기소개서 검증에서 경고 또는 실패 처리한다.
6. 외부 면접 정보는 출처 URL과 조회 시점을 함께 제공한다.
7. 제품 AI 장기 작업은 `agent_run`으로 비동기 수행하고 진행률을 표시한다. 동기 mock turn과 회원 탈퇴 deletion task는 각각 별도 durable 경계를 사용한다.
8. 원본 문서 분석 실패가 전체 사용자 등록을 막아서는 안 된다.
9. 공고 URL 분석 실패 시 URL은 유지하고 사용자가 본문·마감일을 직접 입력한다.
10. AI 결과는 사용자가 저장하기 전 확정 제출물로 간주하지 않는다.
11. 공고의 지원 업무 상태와 URL 추출 상태, 문서의 parse 상태와 evidence 추출 상태는 서로 다른 축이다.
12. 프로필 완료 여부는 Dashboard와 경고에만 사용하며 기능 전체의 접근 권한으로 사용하지 않는다.
13. 사용자 소유 리소스와 과거 산출물은 출처·상태를 추적할 수 있어야 하며, 원천 삭제 뒤에는 원문 대신 `SOURCE_DELETED` marker만 제공한다.

---

# 3. 회원과 인증

## AUTH-001 회원 가입

### 입력

- 이메일
- 비밀번호
- 이름 또는 닉네임
- 개인정보 처리 및 AI 사용 동의

### 규칙

- 이메일은 소문자 정규화 후 unique
- 비밀번호 최소 10자
- 비밀번호는 BCrypt로 해시 저장
- 가입 완료 후 세션 생성 가능
- 활성 계정과 동일한 이메일 재가입 금지
- 탈퇴 계정의 물리 purge가 완료된 뒤에는 같은 이메일 재가입 허용

### 완료 조건

- `users` 생성
- `user_profiles` 기본 행 생성
- 로그인 세션 생성
- 온보딩 페이지로 이동

## AUTH-002 로그인·로그아웃

- 이메일과 비밀번호로 로그인
- 로그인 실패 시 이메일 존재 여부를 노출하지 않는 공통 오류 메시지
- 로그아웃 시 현재 세션 무효화
- 세션 만료 후 보호 페이지 접근 시 로그인 페이지로 이동

## AUTH-003 현재 사용자 조회

헤더, 대시보드, 권한 검증에 필요한 최소 사용자 정보를 반환한다.

## AUTH-004 회원 탈퇴

- 현재 비밀번호를 다시 확인한다.
- 접수 transaction에서 사용자를 즉시 비가역 `WITHDRAWN`으로 바꾸고 모든 Session을 폐기한다.
- 탈퇴는 Agent Run과 분리된 durable deletion task로 처리하며 `Idempotency-Key`를 지원하지 않는다.
- 모든 사용자 API와 Object download는 접수 즉시 차단한다.
- Object와 사용자 콘텐츠의 물리 삭제 목표는 요청 후 24시간 이내다.
- 개인정보가 없는 성공 deletion task metadata만 30일 보존한 뒤 삭제한다.
- purge 완료 후 같은 이메일의 새 계정 가입을 허용하며 탈퇴 계정 복구는 제공하지 않는다.

---

# 4. 사용자 프로필

## PROF-001 기본 프로필

### 필드

- 이름
- 희망 직무
- 희망 산업
- 희망 지역
- 졸업 예정일
- 간단 소개

### 규칙

- 희망 직무는 복수 선택 가능
- 프로필 완료 표시 항목은 `legalName`, 희망 직무 1개 이상, 희망 산업 1개 이상, 희망 지역 1개 이상, 대표 학력 1개다.
- `profileCompleted`는 다섯 항목을 모두 충족할 때 true이고 완료율은 충족 항목 수를 5로 나눈 정수 백분율(항목당 20%)이다.
- Dashboard 완료율과 프로필 경고에 완료 여부·부족 항목·프로필 이동 링크를 표시한다.
- 가입 직후에는 `/onboarding`으로 이동하지만 이후 route 진입을 완료 여부로 강제하지 않는다.
- `profileCompleted=false`만으로 공고 등록·분석·자기소개서·면접 기능을 차단하지 않는다.
- 데이터가 부족한 개별 workflow는 자신의 prerequisite 검증과 안전한 경고로 처리한다.
- 프로필 수정 후 향후 분석부터 최신값 사용

## PROF-002 학력 관리

- 여러 학력 등록·수정·삭제
- 학교명, 전공, 학위, 재학 상태, 입학일, 졸업일, 학점
- 하나의 학력을 대표 학력으로 지정 가능

## PROF-003 자격증 관리

- 자격증명, 발급 기관, 취득일, 만료일, 자격 번호 선택 입력
- 만료 자격증도 이력으로 보존

## PROF-004 어학 성적 관리

- 시험명, 점수, 등급, 응시일, 만료일
- 시험 유형별 점수 형식을 문자열로 저장해 다양한 시험을 지원

## PROF-005 수상 내역 관리

- 수상명, 주최 기관, 수상일, 설명
- 관련 증빙 문서를 연결 가능

## PROF-006 경력 관리

- 회사·기관, 직무, 고용 형태, 시작일, 종료일, 재직 여부, 역할·성과
- 프로젝트 경험은 경력 설명 또는 포트폴리오 근거로 관리
- 날짜 역전 금지

## PROF-007 구조화 프로필 근거 동기화

학력·자격증·어학·수상·경력을 저장하거나 수정하면 서버가 해당 레코드와 연결된 `profile_evidence`를 동기화한다.

- 직접 입력 데이터의 근거 상태는 기본 `VERIFIED`
- `source_type`과 `source_entity_id`로 원본 레코드 추적
- 원본 레코드 삭제 시 과거 산출물이 참조한 연결 근거는 최소 tombstone으로 `SOURCE_DELETED`, 미참조 근거는 삭제
- 문서에서 AI가 추출한 근거와 사용자가 직접 입력한 근거를 구분

---

# 5. 문서와 사용자 근거

## DOC-001 파일 업로드

### 지원 형식

- PDF
- DOCX
- TXT

### 문서 유형

- `RESUME`
- `PORTFOLIO`
- `CAREER_DESCRIPTION`
- `CERTIFICATE`
- `TRANSCRIPT`
- `OTHER`

### 규칙

- 파일당 최대 20MB
- MIME와 확장자 교차 검증
- 문서 parse 상태는 `UPLOADED`, `PARSING`, `PARSED`, `NEEDS_MANUAL_TEXT`, `FAILED`다.
- 업로드 완료 즉시 `UPLOADED`, parse 시작 시 `PARSING`이다.
- `PARSED`는 text 추출·masking·chunk 생성까지 완료했다는 뜻이며 evidence 추출 성공을 뜻하지 않는다.
- 정규화한 비공백 Unicode code point가 100자 미만이면 `NEEDS_MANUAL_TEXT`, 기술 실패는 `FAILED`다.
- evidence 추출 상태는 별도 `NOT_STARTED`, `QUEUED`, `EXTRACTING`, `SUCCEEDED`, `FAILED`다.

## DOC-002 문서 텍스트 추출

```text
원본 저장
→ 페이지/문단별 텍스트 추출
→ 개인정보 마스킹
→ 청크 생성
→ 문서 parse `PARSED`
→ 임베딩 생성
→ 근거 추출 Agent 실행
→ evidence 추출 `SUCCEEDED|FAILED`
```

파싱 실패·텍스트 부족 시 사용자는 추출 텍스트를 직접 입력하거나 문서를 다시 처리할 수 있다. `WAITING_USER`인 동일 실행은 수동 입력으로 재개하고, terminal 실패 또는 명시적 reparse는 lineage를 가진 새 Agent Run을 만든다. parse 성공 뒤 evidence 추출만 실패한 경우 text·chunk를 보존하고 성공한 deterministic step을 재사용한다.

## DOC-003 사용자 근거 추출·검토

추출 대상 예시:

- 기술
- 프로젝트 역할
- 문제 상황
- 수행 행동
- 정량 성과
- 수상
- 자격증
- 경력
- 교육

근거 상태:

- `PENDING`
- `VERIFIED`
- `REJECTED`
- `SOURCE_DELETED`(원천 삭제 시 server 전용)

사용자는 활성 근거의 내용 수정 후 `VERIFIED`로 승인할 수 있다. `SOURCE_DELETED` tombstone은 읽기 전용이며 수정·승인·거절할 수 없다. 승인된 근거는 자기소개서와 면접 준비의 우선 Context가 된다.

## DOC-004 파일 삭제

- 문서 metadata를 즉시 soft delete하고 API와 download URL에서는 곧바로 404로 처리한다.
- 원본 Object, 추출 text, chunk와 embedding은 물리 삭제하며 Object 삭제 실패는 Outbox로 재시도한다.
- 과거 자기소개서·면접 결과가 참조한 document evidence는 원문 없는 최소 tombstone으로 보존하고 `SOURCE_DELETED`로 표시한다.
- 참조되지 않은 document evidence는 삭제한다.
- 보존된 과거 버전·검증·근거 링크는 삭제하지 않으며 raw excerpt를 반환하지 않는다.

---

# 6. 채용 공고

## JOB-001 공고 URL 등록

### 필수 입력

- 공고 URL

### 선택 입력

- 회사명
- 직무명
- 공고 본문 직접 입력
- 마감 일시

### 등록 처리

1. URL 정규화와 중복 확인
2. `job_postings` 생성
3. 기본 상태 `IN_PROGRESS`
4. 사용 가능한 공고 본문을 직접 입력했다면 `MANUAL_INPUT_PROVIDED`로 저장하고 URL 추출 Agent Run을 만들지 않음
5. 직접 입력 본문이 없다면 `QUEUED`로 URL 본문 비동기 추출
6. 회사명·직무명·본문·마감일 후보 추출
7. 회사명·직무명·마감일 사용자 입력값이 있으면 자동 추출값보다 우선
8. 추출 결과 저장 및 사용자 확인

공고 업무 상태는 항상 `IN_PROGRESS|SUBMITTED|CLOSED` 중 하나이며 URL 추출 상태와 분리한다.

추출 상태:

| 상태                    | 의미                        |
| ----------------------- | --------------------------- |
| `QUEUED`                | 원격 추출 대기              |
| `EXTRACTING`            | 원격 추출 중                |
| `EXTRACTED`             | 원격 추출 성공              |
| `MANUAL_INPUT_PROVIDED` | 사용자가 usable 본문을 제공 |
| `NEEDS_MANUAL_INPUT`    | 사용자 본문 입력 필요       |
| `FAILED`                | 재시도 가능한 기술 실패     |

### 실패 처리

- JavaScript 렌더링, 로그인, 봇 차단 등으로 본문 추출 실패 가능
- 공고 레코드는 유지
- 업무 상태는 유지하고 추출 상태를 `NEEDS_MANUAL_INPUT`으로 표시
- 사용자가 공고 본문과 마감일을 직접 수정 가능

## JOB-002 공고 상태 관리

### 상태

| 상태          | 의미                                |
| ------------- | ----------------------------------- |
| `IN_PROGRESS` | 지원 준비 중 또는 지원 여부 검토 중 |
| `SUBMITTED`   | 사용자가 서류 제출 완료로 변경      |
| `CLOSED`      | 공고 마감 또는 사용자가 종료 처리   |

### 전이

```text
IN_PROGRESS → SUBMITTED
IN_PROGRESS → CLOSED
SUBMITTED → CLOSED
CLOSED → IN_PROGRESS 또는 SUBMITTED  // 마감 연장·오등록 시 사용자 명시적 재오픈
```

### 자동 마감

- `deadline_at <= 현재 시각`
- 현재 상태가 `IN_PROGRESS` 또는 `SUBMITTED`
- Scheduler가 `CLOSED`로 변경
- `closed_reason = DEADLINE_PASSED`
- 기존 `submitted_at` 유지
- 상태 이력 기록

## JOB-003 공고 목록·필터

필터:

- 지원 중: `IN_PROGRESS`
- 서류 제출: `SUBMITTED`
- 마감: `CLOSED`
- 회사명·직무명 검색
- 마감 임박
- 정렬: 최근 등록, 마감 임박, 최근 수정

## JOB-004 공고 분석

분석 항목:

- 회사·직무
- 주요 업무
- 필수 지원 자격
- 우대 사항
- 기술·도메인 키워드
- 경력·학력 조건
- 마감일과 출처
- 사용자 지원 가능 여부
- 사용자 강점·부족한 점
- 관련 사용자 근거
- 직무 적합도 점수와 근거

`Eligibility`와 `fitScore`는 별도로 계산하고 표시한다. `INELIGIBLE`이어도 점수를 0으로 만들거나 상한을 두지 않으며 합격 기준점이나 합격 확률을 제공하지 않는다.

적합도 점수는 0.00~100.00이고 다음 가중치를 사용한다.

| 기준             | 가중치 |
| ---------------- | -----: |
| 필수 자격        |     40 |
| 핵심 업무·기술   |     30 |
| 우대 사항        |     15 |
| 관련 경험·도메인 |     10 |
| 학력·자격·어학   |      5 |

각 criterion은 `MATCHED=1.0`, `PARTIAL=0.5`, `MISSING|UNKNOWN=0`으로 계산하고, 공고에 없는 category 가중치는 존재 category에 비례 재배분한다. 점수 근거는 구조화 프로필과 `VERIFIED` evidence만 사용한다. 추출 가능한 criterion이 하나도 없으면 분석 결과 row를 만들지 않고 Agent Run을 `INSUFFICIENT_JOB_DATA`로 실패시킨다. rubric version과 criterion별 공고 source·승인 evidence를 저장한다. 화면에는 다음 문구를 표시한다.

> 적합도 점수는 합격 가능성이 아니라 등록된 정보와 공고 요구사항의 일치도를 나타냅니다.

## JOB-005 공고 정보 수정

사용자가 회사, 직무, 본문, 마감일을 수정할 수 있다. 수정 후 분석이 오래된 경우 `OUTDATED` 배지를 표시하고 재분석을 제안한다.

---

# 7. 자기소개서

## CL-001 자기소개서 생성 단위

하나의 공고는 여러 자기소개서 문항을 포함하는 active `cover_letter`를 최대 하나 가질 수 있다. active는 `DRAFT|FINALIZED`이며 `ARCHIVED` history는 여러 개 허용한다.

자기소개서 상태:

- `DRAFT`
- `FINALIZED`
- `ARCHIVED`

## CL-002 문항 관리

문항별 필드:

- 문항 순서
- 문항 내용
- 글자 수 제한
- 메모

사용자는 문항 추가·수정·삭제·순서 변경 가능하다.

## CL-003 AI 자기소개서 생성

### 입력 Context

- 선택 공고의 최신 분석
- 사용자 기본 프로필
- 승인된 근거
- 관련 문서 청크
- 사용자 지정 경험
- 문항과 글자 수 제한
- 회사 조사 결과가 있는 경우 공식 정보

### 워크플로

```text
문항 전체 계획
→ 문항별 요구 분석·근거 검색
→ 지원서 전체 경험 배분
→ 문항별 작성
→ 문항별 Fact Check
→ 문항별 immutable version·검증 저장
```

### 생성 규칙

- 문항별 독립 생성
- 글자 수 제한 준수
- 동일 지원서 안에서 경험 중복을 최소화
- 출처 없는 수치 생성 금지
- 과장된 직무 경험 표현 금지
- current answer가 없을 때 생성 버전은 `AI_GENERATED`, 있을 때 새 생성 버전은 `AI_REVISED`다.
- 문항별 결과를 atomic하게 저장하고 일부 실패 시 성공 version을 보존한다. retry는 동일 hash의 성공 문항을 재사용하고 실패 문항만 다시 실행한다.
- 미승인 masked chunk는 evidence 후보 탐색·semantic 탐색·FactCheck 모순 확인에만 사용하고 writer·적합도 score·면접 질문의 긍정 사실 근거로 사용하지 않는다. 그 chunk만으로 검증을 통과시키지 않고 `WARNING + UNVERIFIED_CLAIM`으로 표시한다.

## CL-004 사용자 편집과 버전 관리

- 사용자는 TipTap 편집기에서 직접 수정
- 사용자가 `버전 저장`을 선택할 때 서버에 새 버전 생성
- 입력 중 임시 자동 저장은 사용자 ID·resource·question·base version으로 격리한 `sessionStorage`에만 최대 24시간 제공하며 서버 버전을 만들지 않음
- 각 문항에 현재 버전 하나만 존재
- 버전 출처:
  - `AI_GENERATED`
  - `USER_EDITED`
  - `AI_REVISED`
  - `RESTORED`
- 과거 버전 조회·복원 가능
- 복원도 새로운 버전을 생성하며 기존 기록은 삭제하지 않음
- `FINALIZED` 상태에서 문항 또는 답변을 수정하면 자기소개서 상태를 자동으로 `DRAFT`로 되돌림
- `ARCHIVED`는 읽기 전용이며 edit·generate·verify·finalize를 허용하지 않음

## CL-005 검증

검증 항목:

- 등록된 사용자 근거와 일치하는가
- 정량 수치가 원문 근거와 같은가
- 지원자의 역할이 과장되지 않았는가
- 공고·회사명·직무명이 혼동되지 않았는가
- 문항 요구사항을 충족하는가
- 글자 수 제한을 지키는가
- 동일 지원서 내 반복이 과도하지 않은가

검증 상태:

- `PENDING`
- `PASSED`
- `WARNING`
- `FAILED`

검증 결과에는 문장별 문제, 근거 링크, 수정 제안을 제공한다. 검증은 문서를 자동 수정하지 않으며 사용자가 적용 여부를 결정한다. 검증은 immutable answer version에 연결되므로 current answer가 바뀌면 과거 검증으로 최종화할 수 없다.

## CL-006 최종화

모든 active 문항에 current answer가 있고 해당 current version의 최신 검증이 존재해야 한다. 최신 검증이 `PENDING|FAILED`이면 최종화할 수 없다. `WARNING`이면 사용자가 해당 verification ID를 명시적으로 확인한 경우에만 최종화할 수 있고 `PASSED`는 바로 허용한다. 공고 상태를 `SUBMITTED`로 변경하는 것은 별도 사용자 행동이다.

## CL-007 보관·복구

- `DRAFT|FINALIZED → ARCHIVED`를 허용하며 `archivedAt`을 기록한다.
- `ARCHIVED`는 읽기 전용이다.
- 같은 공고에 active 자기소개서가 없을 때만 `ARCHIVED → DRAFT` unarchive를 허용한다.
- unarchive하면 `archivedAt`을 제거하고 기존 `finalizedAt`은 마지막 최종화 이력으로 보존한다.

---

# 8. 면접 준비

## INT-001 면접 준비 생성

입력:

- 공고
- 사용할 자기소개서
- 목표 면접 유형 선택
- 조사 품질 모드

필수 조건:

- 공고 분석 완료
- 자기소개서 최소 1개 문항 존재

## INT-002 회사·유사 직무 면접 정보 조사

검색 대상:

- 회사 공식 채용 안내
- 회사 기술 블로그·보도자료
- 동일 회사 유사 직무 면접 후기
- 유사 직무 기술 질문 사례
- 최근 채용 프로세스 정보

출처 분류:

- `OFFICIAL`
- `TECH_BLOG`
- `NEWS`
- `INTERVIEW_REVIEW`
- `COMMUNITY`
- `OTHER`

각 출처에 URL, 제목, 발행일, 조회일, 스니펫, 신뢰도 안내를 제공한다. 익명 후기는 사실로 단정하지 않는다. 준비 요청 하나는 combined research run 하나와 질문 세트 하나를 만들며 질문과 출처를 N:M provenance link로 연결한다.

출처 범위는 `SUFFICIENT|LIMITED|NONE`으로 계산한다. usable source 3개 이상, `OFFICIAL|TECH_BLOG` 1개 이상, 서로 다른 category/domain 2개 이상과 주요 claim link가 있으면 `SUFFICIENT`; usable하지만 부족하면 `LIMITED`; 정상 검색 결과에서 usable source가 없으면 `NONE`이다. `LIMITED|NONE`도 성공 결과로 저장하되 회사·process 사실을 단정하지 않는다. 모든 provider 호출이 장애인 경우에만 조사 실패다.

## INT-003 예상 면접 질문

질문 유형:

- 자기소개서 검증
- 이력서·포트폴리오 검증
- 직무 기술
- 프로젝트 심층
- 행동·협업
- 회사·지원 동기
- 꼬리 질문

질문별 제공 정보:

- 질문
- 질문 의도
- 평가 포인트
- 답변 구성 가이드
- 관련 근거
- 예상 꼬리 질문
- 출처 기반 여부

## INT-004 예상 질문 답변 작성·피드백

사용자는 질문별 답변을 작성하고 immutable version으로 저장할 수 있다. MVP source는 server가 지정하는 `USER_EDITED`만 있고 client는 source를 입력하지 않는다.

피드백 기준:

- 질문 적합성
- 구조와 논리
- 구체성
- 기술 정확성
- 본인 역할 명확성
- 근거와 수치
- 회사·직무 연결
- 장황함과 모호 표현

피드백은 점수뿐 아니라 잘한 점, 보완점, 개선 예시를 제공한다.

## INT-005 대화형 모의 면접

### 시작 설정

- 면접 유형
- 난이도
- 질문 수 목표
- 압박 질문 여부
- 주요 프로젝트
- 피드백 방식: 매 질문 후 또는 세션 종료 후

### 진행

1. 면접관 질문
2. 사용자 텍스트 답변
3. 답변 분석
4. 필요 시 꼬리 질문
5. 다음 질문
6. 세션 종료
7. 종합 피드백

### 규칙

- 질문은 해당 세션의 공고·자기소개서·프로필 근거를 사용
- 사용자가 말하지 않은 사실을 답변으로 간주하지 않음
- 기술적으로 잘못된 답변은 근거와 함께 지적
- 최대 질문 수 또는 사용자의 종료 요청으로 완료
- start/message 요청은 HTTP deadline 20초, 요청당 chat 호출 1회, search 0회, embedding 0회다.
- turn당 최대 비용은 USD 0.03, session의 동기 turn 누적 상한은 USD 0.30이다.
- 각 요청은 `clientRequestId`와 session version을 사용한다. 동일 ID/hash가 처리 중이면 처리 상태를, terminal이면 성공 또는 실패의 저장된 안전 응답을 복구한다. 실패 replay도 유료 호출을 다시 하지 않으며 새 유료 호출은 명시적으로 새 ID를 받은 경우에만 수행한다.
- timeout 또는 structured output 실패 시 서버가 자동으로 모델을 다시 호출하지 않는다.
- 동기 turn은 Agent Run을 만들지 않고 session/turn usage로 기록한다.

## INT-006 모의 면접 피드백

세션 피드백:

- 전체 요약
- 강점
- 반복 약점
- 추가 학습 주제
- 개선 우선순위
- 다시 연습할 질문
- 질문별 점수와 코멘트

세션과 메시지는 나중에 다시 열람 가능하다. 세션을 `COMPLETED`로 전환한 뒤 종합 feedback만 `BALANCED` 품질의 비동기 Agent Run으로 생성하며, feedback이 pending·failed여도 session과 transcript는 `COMPLETED`로 유지한다.

---

# 9. 시스템 기능

## SYS-001 Agent Run 진행 상태

표시 항목:

- 전체 상태
- 현재 단계
- 진행률
- 단계별 성공·실패
- 재시도 가능 여부
- 사용 모델 등급
- 소요 시간
- 예상 또는 실제 비용

상태:

- `QUEUED`
- `RUNNING`
- `WAITING_USER`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`
- `INTERRUPTED`

`WAITING_USER`는 모델 호출 전 필수 수동 입력이 없는 상태다. 미사용 비용 예약을 해제하고 사용자가 입력을 제공하면 같은 run을 `QUEUED`로 재개한다. terminal retry는 기존 run을 다시 열지 않고 lineage를 가진 새 run을 만든다.

## SYS-002 실패와 재시도

- 동일 사용자·workflow·step·scope의 입력·context·policy hash가 같은 성공 단계만 재사용
- 자동 재시도 2회는 최초 시도 포함 최대 3 attempt이며 모델 승격 호출도 attempt를 소비
- 429/5xx·일시 network·비동기 timeout·structured output validation 실패만 자동 재시도
- 소유권·입력·domain validation, safety, 설정, 예산 오류는 자동 재시도하지 않음
- 비복구 오류는 사용자 재시도 버튼 제공
- resource별 retry와 범용 Agent Run retry는 같은 predecessor unique claim을 사용해 실패 attempt당 successor를 최대 하나만 만든다. 새 domain output이 필요한 workflow도 그 successor에 대응하는 resource set을 정확히 하나만 만든다.
- URL 추출 실패는 수동 입력으로 전환
- 모델 비용 한도 초과는 실행 중단 후 원인 표시
- 검색 결과 부족은 출처 부족 상태로 완료 가능
- cancel은 active run에만 cooperative하게 적용한다. 이미 발생한 비용은 정산하되 취소 뒤 결과는 적용하지 않고 run terminal 상태와 resource의 마지막 안정 상태 복원을 한 transaction으로 처리한다.

## SYS-003 비용 제어

- 작업별 모델 정책과 immutable provider price catalog version
- 사용자 기본 일일 예산 USD 1.00, 사용자별 시스템 최대 일일 예산 USD 2.00
- 최대 비동기 Agent Run 비용 USD 0.30
- 예산 reset zone `Asia/Seoul`
- chat·embedding·search usage를 모두 가격 catalog와 예산에 포함
- 실행·turn 접수 전에 비용을 원자 reserve하고 실제 usage로 settle한 뒤 미사용액을 release
- 외부 provider 가격은 명세 금액으로 고정하지 않고 immutable price catalog version에 저장
- 값은 운영 설정과 versioned policy로 관리하고 비즈니스 코드 상수로 하드코딩하지 않음
- 중복 분석 캐시
- 문서 전체 대신 관련 근거만 Context에 포함

공개 품질 모드는 `ECONOMY|BALANCED|HIGH_QUALITY`다. `HIGH_QUALITY`는 사용자 설정 활성화, 요청별 명시 선택과 비용 예약 성공을 모두 요구하며 자기소개서 생성·자기소개서 검증·면접 답변 피드백에만 허용한다. 모의 면접 종합 feedback은 `BALANCED`로 고정한다. 공고 분석, 문서·공고 추출과 면접 준비는 `ECONOMY|BALANCED`만 허용한다.

---

# 10. 핵심 인수 조건

| ID    | 인수 조건                                                       |
| ----- | --------------------------------------------------------------- |
| AC-01 | 신규 사용자가 가입·로그인 후 자기 데이터만 조회한다.            |
| AC-02 | 학력·자격증·어학·수상·경력을 각각 CRUD 할 수 있다.              |
| AC-03 | PDF/DOCX/TXT를 업로드하고 파싱 상태와 추출 근거를 확인한다.     |
| AC-04 | 공고 URL만으로 공고가 등록되며 추출 실패 시 직접 보완 가능하다. |
| AC-05 | 공고를 `IN_PROGRESS`, `SUBMITTED`, `CLOSED`로 필터링한다.       |
| AC-06 | 마감일이 지나면 Scheduler가 공고를 `CLOSED`로 변경한다.         |
| AC-07 | 공고 분석이 사용자 근거와 매칭된 강점·부족점을 제공한다.        |
| AC-08 | 문항별 자기소개서 초안을 생성하고 근거 검증 결과를 제공한다.    |
| AC-09 | 사용자가 자기소개서를 수정하고 과거 버전을 조회·복원한다.       |
| AC-10 | 회사·유사 직무 면접 정보에 출처가 표시된다.                     |
| AC-11 | 예상 질문 답변을 저장하고 질문별 피드백을 받는다.               |
| AC-12 | 대화형 모의 면접을 진행하고 세션 종합 피드백을 확인한다.        |
| AC-13 | 장기 작업의 진행 상태와 실패 원인을 UI에서 확인한다.            |
