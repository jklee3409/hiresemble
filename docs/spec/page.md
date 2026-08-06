# 페이지 구조 명세서

- 문서 버전: 1.3 (공개 Landing·첫 사용 흐름 계약)
- Frontend: Vue 3 SPA
- 기본 화면: Desktop First, 모바일 반응형
- API Prefix: `/api/v1`
- 공고 상태: `IN_PROGRESS`, `SUBMITTED`, `CLOSED`

---

## 1. 전체 라우트

```text
/
├─ 공개 서비스 소개 Landing
├─ /signup
├─ /login
├─ /onboarding
├─ /guide
├─ /dashboard
├─ /profile
│  ├─ /profile/basic
│  ├─ /profile/education
│  ├─ /profile/certifications
│  ├─ /profile/languages
│  ├─ /profile/awards
│  ├─ /profile/careers
│  └─ /profile/activities
├─ /documents
│  └─ /documents/:documentId
├─ /jobs
│  ├─ /jobs/new
│  └─ /jobs/:jobId
│     ├─ /jobs/:jobId/overview
│     ├─ /jobs/:jobId/analysis
│     ├─ /jobs/:jobId/cover-letter
│     ├─ /jobs/:jobId/interview
│     └─ /jobs/:jobId/interview/mock/new
├─ /cover-letters
│  └─ /cover-letters/:coverLetterId/edit
├─ /interviews
│  ├─ /interview-question-sets/:questionSetId
│  └─ /mock-interviews/:sessionId
├─ /agent-runs
│  └─ /agent-runs/:agentRunId
└─ /settings
   ├─ /settings/account
   ├─ /settings/ai
   ├─ /settings/usage
   └─ /settings/privacy

/backoffice
├─ /backoffice/overview
├─ /backoffice/users
├─ /backoffice/users/:userId
├─ /backoffice/usage
├─ /backoffice/ai-costs
├─ /backoffice/agent-runs
├─ /backoffice/failures
└─ /backoffice/configuration

unmatched /:pathMatch(.*)* → 전용 404
```

Canonical redirect:

| 입력                     | 결과                                                                |
| ------------------------ | ------------------------------------------------------------------- |
| `/`, anonymous           | 공개 서비스 소개 Landing                                            |
| `/`, authenticated       | `/dashboard`                                                        |
| `/signup`, authenticated | 방금 가입한 session이면 `/onboarding`, 그 외 `/dashboard`로 replace |
| `/login`, authenticated  | 안전한 `returnTo` 또는 `/dashboard`로 replace                       |
| `/profile`               | `/profile/basic`                                                    |
| `/jobs/:jobId`           | `/jobs/:jobId/overview`                                             |
| `/settings`              | `/settings/account`                                                 |

job 상세 tab child는 `overview|analysis|cover-letter|interview`, 별도 생성 child는 `interview/mock/new`만 허용한다. 타 사용자 UUID도 같은 404 화면을 사용한다.

현재 실제 router는 `/settings/*`, `/backoffice/*`, `/mock-interviews/*`, `/jobs/:jobId/interview/mock/new`를 구현하지 않았다. 다음 표의 미래 route는 구현 전까지 목표 계약이며 현재 route처럼 취급하지 않는다.

| Route group                                        | Implementation status | Phase     | prerequisite API                                 |
| -------------------------------------------------- | --------------------- | --------- | ------------------------------------------------ |
| `/` 공개 Landing                                   | `IMPLEMENTED`         | 공개 진입 | 인증 API bootstrap                               |
| 현재 `/signup`~`/agent-runs/:agentRunId`, `/guide` | `IMPLEMENTED`         | P1~P8     | 현재 OpenAPI 69 paths/94 operations              |
| `/settings/usage`                                  | `PLANNED`             | P8.7      | `GET /settings/usage`, `/settings/usage/history` |
| account, AI, privacy 설정 세 route                 | `PLANNED`             | P10-A     | account, settings AI/privacy API                 |
| `/jobs/:jobId/interview/mock/new`                  | `PLANNED`             | P9        | mock session create                              |
| `/mock-interviews/:sessionId`                      | `PLANNED`             | P9        | mock session/start/message/complete/feedback     |
| `/backoffice`와 모든 child                         | `PLANNED`             | P8.9-A    | `/api/v1/backoffice/**` ADMIN GET                |

`/backoffice`는 `/backoffice/overview`로 redirect한다. 일반 사용자 navigation에는 Backoffice를 표시하지 않는다.

---

## 2. 공통 Layout

## 2.1 공개 LandingPage

대상: anonymous `/`.

- 서비스 가치, 해결하려는 문제, 내 정보→자료→공고 자동 분석→자기소개서→면접의 5단계, 사용자 중심 가치와 AI 활용 원칙을 설명한다.
- 주요 action은 `/signup`과 `/login`만 제공하며 보호 route로 직접 연결하지 않는다.
- semantic `header`, `main`, `section`, `footer`, skip link와 section anchor를 제공한다.
- Hero 제품 데모와 지원 흐름 band는 viewport·문서 visibility에서만 자동 재생하고 `prefers-reduced-motion`에서는 전환·marquee·장식 motion을 중단한다.
- 로그인 사용자의 `/`는 인증 bootstrap 완료 뒤 component를 mount하기 전에 `/dashboard`로 replace한다.

## 2.2 PublicLayout

대상:

- `/signup`
- `/login`

구성:

- 서비스 로고
- 인증 Form
- 개인정보·AI 처리 안내
- 오류 메시지 영역

PublicLayout의 desktop·mobile 브랜드는 `/`의 공개 Landing으로 돌아간다. Landing은 인증 form 전용 PublicLayout 안에 넣지 않는다.

## 2.3 AppLayout

대상: 로그인 보호 페이지.

### Navigation과 상단 Header

- Desktop은 상단에 `홈`, `내 정보`, `이력서·자료`, `관심 공고`, `자기소개서`, `면접 준비`의 사용자 여정 중심 navigation을 둔다.
- 모바일은 `홈`, `공고`, `자기소개서`, `면접 준비`, `더보기` bottom navigation을 사용한다. 더보기 dialog에서 내 정보, 자료, AI 작업과 가이드에 접근한다.
- 상단 우측에는 진행 중 Agent Run 알림과 사람 아이콘+닉네임 account menu를 둔다. 사진 기능이 없으므로 이름 첫 글자 avatar와 별도 sidebar profile card를 사용하지 않는다.
- account menu는 이용 가이드, AI 작업, 닉네임 변경, 로그아웃을 제공하고 header와 navigation에 사용자 정보를 중복 표시하지 않는다.
- 목록·상세·편집·분석·설정 성격에 맞는 PageHeader variant를 사용하며 모든 화면에 eyebrow와 대형 제목을 반복하지 않는다.

### 공통 상태 UI

- Skeleton
- Empty State
- Inline Validation
- Toast
- Confirm Dialog
- Agent Progress Drawer
- Version Conflict 비교·재적용 Dialog
- 인증 shell별 전용 404

브라우저 기본 `alert`, `confirm`, `prompt`는 사용하지 않는다. 저장·승인·요청 성공은 Toast, 조회·네트워크 오류는 Toast 또는 해당 영역 메시지, 입력 오류는 field 인접 Inline Validation으로 구분한다. 자료·대외활동·AI 작업 삭제, 다시 분석, 승인 취소처럼 되돌리기 어렵거나 새 사용량이 생길 수 있는 동작은 Confirm Dialog를 사용한다. Dialog는 cancel에 초기 focus를 두고 Tab focus trap, ESC 닫기, 배경 클릭 취소와 trigger focus 복귀를 지원한다.

Frontend의 TypeScript type과 runtime validation은 [`api.md`](api.md) 2장의 canonical enum 값을 이름과 의미까지 그대로 사용한다. 화면 전용 alias나 추가 상태를 만들지 않고 알 수 없는 값은 안전한 일반 상태와 갱신 안내로 처리한다. 특히 공고 업무·추출, 문서 parse·evidence 추출, 자기소개서, 조사 coverage, 모의 면접·feedback, Agent Run 상태 축을 서로 합치지 않는다.

## 2.4 BackofficeLayout (`PLANNED` P8.9-A)

대상: `/backoffice/**` ADMIN route.

- AppLayout과 분리된 운영 navigation과 좁은 ADMIN identity banner를 사용한다.
- Backend ADMIN 확인 전 화면·사용자 검색 query를 실행하지 않는다.
- overview, 사용자, 사용량, AI 원가, Agent Run, 실패, 구성 메뉴만 제공한다.
- 일반 사용자 AppLayout/navigation에는 Backoffice link를 넣지 않는다.
- 사용자 검색·상세·drill-down 접근은 audit 대상임을 운영자에게 표시한다.
- 원문·transcript·prompt/response·API key를 렌더링할 component를 만들지 않는다.

---

# 3. 인증과 온보딩

## 3.1 `/signup`

### 구성

- 이메일
- 비밀번호
- 비밀번호 확인
- 닉네임
- 이용약관 동의
- AI 처리 동의
- 가입 버튼

이메일은 `@`와 domain을 포함한 형식을 client에서 검사하되 placeholder 외의 별도 형식 안내는 표시하지 않는다. 비밀번호는 실제 API 계약과 같이 전체 10자 이상, 문자·숫자·특수문자 각 1개 이상과 서버 저장 경계를 검사한다. 사용자가 이메일 또는 비밀번호 입력을 떠나는 시점에 해당 항목만 검증하며, 실패하면 입력 테두리와 오류 문구를 red 상태로 표시한다. 오류가 표시된 뒤 값을 고치면 입력 중에도 다시 검사해 유효해지는 즉시 red 상태를 해제한다. 비밀번호 안내는 `비밀번호는 10자 이상 입력해주세요.`, `숫자/문자/특수 문자를 최소 한 글자 이상 포함해주세요.`, `다른 서비스에서 쓰지 않는 비밀번호를 권장해요.`로 표시하고, 저장 경계를 넘은 입력은 기술 단위 없이 짧게 입력해 달라는 행동 안내로 표시한다.

두 필수 동의는 각각 별도 `상세 보기`를 제공한다.

- 이용약관·개인정보 상세 Modal은 `안전하게 저장해요`를 포함한 세 개의 핵심 요약을 먼저 보여주고, 이후 서비스 이용 약속과 수집 항목·목적·보관 기간·동의 거부 영향을 항목별로 설명한다. 비밀번호는 다시 알아볼 수 없는 형태로 바꾸어 저장한다고 안내하고 실제 암호 알고리즘 명칭은 노출하지 않는다. 탈퇴 즉시 접근 차단, 24시간 안에 사용자 자료 삭제, 개인을 알아볼 수 없는 삭제 확인 내용 30일 보관을 쉽게 설명한다.
- AI 처리 상세 Modal은 `OpenAI 기반으로 처리해요`를 포함한 세 개의 핵심 요약을 먼저 보여주고, 처리 대상, OpenAI 기반 기능 사용, OpenAI 서비스 개선에 미사용, 안전한 운영을 위한 최대 30일 보관 가능성, 공개 웹 검색 원칙, 민감한 정보를 가리는 방식, 일반 서비스 기록 비저장, 결과의 사용자 검토와 동의 거부 영향을 쉽게 설명한다. AI 세부 방식이나 데이터 표현에 관한 전문 용어는 화면에 노출하지 않는다.
- Modal은 핵심 요약, 번호가 있는 상세 카드, 주의 안내, 고정된 확인 영역 순서로 구성한다. desktop에서는 중앙 dialog, mobile에서는 bottom sheet로 표시하고 본문만 scroll되어 제목과 확인 버튼이 독립된 영역에 유지된다. ESC·배경·닫기 버튼, Tab focus trap, trigger focus 복귀와 body scroll lock을 지원하며 상세 확인은 동의 checkbox를 자동 선택하지 않는다.

### API

- `GET /auth/csrf`
- `POST /auth/signup`

### 완료

- `/onboarding` 이동

## 3.2 `/login`

### API

- `POST /auth/login`
- `GET /auth/me`

성공 시 사용자의 마지막 보호 페이지 또는 `/dashboard`.

## 3.3 `/onboarding`

단계:

1. 기본 프로필과 지원 자격 자기신고(근무 가능일, 병역 상태, 해외여행 가능 여부, 채용 결격 사유 여부)
2. 최종 학력
3. 희망 직무·산업·지역
4. 이력서 또는 포트폴리오 업로드
5. 추후 입력 선택

API:

- `PUT /profile`
- `GET /profile/eligibility`
- `PUT /profile/eligibility`
- `POST /profile/educations`
- `POST /documents`

문서 분석은 완료를 기다리지 않고 대시보드로 이동 가능.

가입 직후만 `/onboarding`으로 이동한다. 이후에는 프로필 완료 여부로 route를 강제 redirect하지 않는다. `legalName`, 희망 직무·산업·지역 각 1개, 서버가 계산한 최종 학력 1개 중 부족 항목과 충족 항목당 20%인 완료율을 경고·프로필 이동 링크와 함께 표시하되 공고·분석·자기소개서·면접 진입을 일괄 차단하지 않는다.

온보딩과 대시보드는 언제든 다시 볼 수 있는 `/guide` 진입점을 제공한다. 가이드는 내 정보→자료→공고 자동 분석→자기소개서→면접의 5단계를 실제 공통 UI component와 안전한 demo data로 만든 mini preview, 텍스트 설명과 route CTA로 보여 준다. 강제 tour나 영구 localStorage dismiss 상태는 사용하지 않는다.

공개 `/`는 가입 전 서비스 가치와 이 5단계의 의미를 설명하지만 보호 route CTA를 제공하지 않는다. `/onboarding`은 가입 직후 실제 정보를 저장하는 제품 기능이고, `/guide`는 로그인 뒤 실제 기능 route로 이동하는 재방문 가이드다.

## 3.4 `/guide`

- 5단계 전체 이용 순서와 단계별 실제 route CTA
- 공통 `AppIcon`, `StatusBadge`, design token을 사용하는 제품 UI mini preview
- screenshot만으로 의미를 전달하지 않고 각 preview에 accessible label과 본문 설명 제공
- 서버 완료 상태 없이 재방문 가능

---

# 4. 대시보드 `/dashboard`

대시보드는 로그인 직후 사용자가 현재 준비 상태, 먼저 할 일, 이번 달 마감, 최근 활동과 짧은 가이드를 한 흐름에서 확인하는 지원 워크스페이스다. 요약과 마감은 `GET /dashboard?month=YYYY-MM`, 가이드는 `GET /career-guides`를 사용하며 최근 활동은 기존 owner-scoped Document·Job·Agent Run 목록을 조합한다.

첫 사용 체크리스트:

1. `profile.profileCompleted === true`: 기본 정보 준비 완료
2. document `totalElements > 0`: 이력서 또는 포트폴리오 등록 완료
3. job `totalElements > 0`: 첫 관심 공고 등록 완료

- 완료 항목 수를 `n / 3`으로 표시하고 하나를 완료해도 남은 항목을 유지한다.
- 세 항목을 모두 완료했을 때만 체크리스트를 숨기며 AI 작업 수는 표시 여부에 영향을 주지 않는다.
- Dashboard query 실패는 미완료나 0개로 계산하지 않고 `확인 필요`, `—`와 재조회 action을 제공한다.
- 체크리스트와 일반 지원 현황, 다음 할 일, 최근 활동은 함께 표시한다. 영구 dismiss 상태와 profile 완료 route gate는 사용하지 않는다.

## 정보 구조와 문구

- 제목은 `{displayName}님의 지원 준비 현황`, 설명은 `마감 일정과 다음 할 일을 한눈에 확인하세요.`를 사용한다. 제목에서는 이름만 Hiresemble Blue로 강조한다.
- 커리어 카드는 서버가 안전하게 제공하는 이름, 희망 직무·지역, 최종 학력, `지원 정보 준비도`와 프로필 CTA를 표시한다. 사진이나 이름 첫 글자 avatar 대신 별도 사람 SVG icon과 추상 CSS 장식을 사용하며 미입력 값은 명시한다.
- 요약은 `준비 중인 공고`, `지원 완료`, `AI가 확인 중`, `등록한 이력서·자료`의 행동 중심 문구와 정확한 서버 count를 사용한다. 각 카드에는 서버 값에서 직접 유도한 보조 문구(`등록한 공고 N건 중`, `분석 중 N건` 등)만 덧붙이고 추정치를 만들지 않는다. 조회 실패 카드는 `—`와 확인 실패 문구를 함께 표시한다.
- 프로필 보완, 확인이 필요한 자료, 입력 대기 Agent Run, 가까운 마감을 우선순위 순으로 `다음 할 일`에 표시한다. `다음 할 일` 카드 하단에는 공고 등록·자료 등록·자기소개서·면접 준비로 이동하는 `빠른 실행` 링크를 고정해 할 일 수가 적을 때도 카드 하단 행동 위치를 유지한다.
- 최근 활동 항목에는 자료·공고·AI 작업을 구분하는 icon을 붙이고 색상에만 의존하지 않도록 기존 text label을 유지한다.
- 섹션 진입 motion, 카드 hover elevation, 요약 수치 count-up 같은 장식 동작은 `prefers-reduced-motion: reduce`에서 모두 정지하고 최종 값과 배치를 그대로 보여 준다.
- Dashboard에 한해 최대 88rem 폭을 허용하고 다른 앱 화면의 공통 폭은 유지한다.
- Desktop에서는 바로가기 열을 제외한 헤더·CTA·본문을 동일한 중앙 열에 배치한다. 중앙 열은 Dashboard viewport 중심에 맞추고 `자료 등록`·`공고 등록` CTA의 우측 끝도 중앙 열의 우측 경계에 맞춘다.
- 중복되는 `한눈에 보기`, `지원 준비 요약` 타이틀은 시각적으로 노출하지 않되 screen reader용 heading은 유지한다. 섹션 제목은 self-hosted variable `Noto Sans KR`, 절제된 굵기와 자간을 사용한다.
- Desktop 오른쪽에는 지원 현황·마감 캘린더·최근 활동·취업 준비 가이드 anchor를 제공하는 작은 바로가기를 Dashboard container 안의 sticky sidebar로 배치해 스크롤 중에도 접근할 수 있게 한다. fixed positioning은 사용하지 않으며 좁은 화면에서는 일반 흐름의 가로형 탐색으로 전환한다.

## 마감 캘린더

- 이전·다음 월 이동을 제공하고 날짜별 활성 마감 공고 수를 `N건` 배지로 표시한다. 별도 오늘 이동 버튼은 노출하지 않는다. 일요일 날짜는 red, 토요일 날짜는 Hiresemble Blue를 사용하되 선택·오늘 상태는 border와 label로도 구분한다.
- 캘린더 header는 이번 달 마감 요약과 월·연도, 이전·다음 이동을 분리해 위계를 만들고, 날짜 grid는 명시적인 cell 간격과 부드러운 경계로 인접 hover·선택 강조가 서로 침범하지 않게 한다.
- 일반·오늘·선택·마감 보유 날짜는 날짜 marker, soft surface, 내부 border와 작은 event chip을 조합해 구분하며 큰 외곽 outline이나 셀 밖으로 뜨는 badge를 사용하지 않는다.
- 마감이 있는 날짜와 선택 날짜의 공고에는 서울 자정 기준으로 계산한 `D-n`·`D-DAY`·`D+n` 배지를 함께 표시한다. 3일 이내는 danger, 7일 이내는 warning, 그 밖은 brand tone을 사용하며 배지 text와 마감 시각을 항상 함께 노출한다.
- 캘린더 하단에는 오늘·3일 이내·7일 이내·마감 예정을 설명하는 legend를 두어 색상 구분이 단독 정보가 되지 않게 한다.
- 선택 날짜의 회사, 공고명, 상태, `Asia/Seoul` 마감 시각과 상세 링크를 같은 화면에 보여 준다. Desktop은 옆 패널, mobile은 접근 가능한 `details` 패널을 사용한다.
- `CLOSED` 공고는 제외하고 월 경계는 서울 자정으로 계산한다. 월별 수를 paginated 첫 page의 `items.length`로 추정하지 않는다.
- loading, 오류, 일정 없음과 선택 날짜 결과 없음은 서로 다른 상태로 표시한다.

## 취업 준비 가이드

- 게시된 서버 콘텐츠를 노출 순서대로 최대 5개 카드에 표시한다. 프론트 상수 콘텐츠를 사용하지 않는다. 카드 icon은 서버 `category`에 매핑하고 매핑이 없으면 순번 기반 기본값을 사용한다.
- 카드를 누르면 category, 순번, 제목, 요약, 여러 문단의 상세 본문, 게시일과 콘텐츠 version을 위계화한 modal에서 보여 준다. modal은 ESC·배경·닫기 버튼, focus trap, trigger focus 복귀, body scroll lock과 mobile sheet layout을 지원한다.
- 게시 상태·순서·카테고리·게시 시각·version은 Backend/DB가 소유하며 이번 범위에는 관리자 mutation UI/API가 없다.
- 준비 workspace의 `전체 이용 순서 보기` CTA는 설명 바로 뒤가 아니라 카드 하단에 고정해 다른 콘텐츠 높이에서도 행동 위치를 유지한다.
- 준비 workspace 제목의 `한 번 정리한 정보는`과 `다음 지원에도 이어져요.`는 각각 하나의 의미 단위로 유지해 단어 중간에서 줄바꿈하지 않는다.

route 변경 시 `#app-content[tabindex="-1"]`로 프로그램적 focus를 이동하는 접근성 동작은 유지한다. 이 비상호작용 workspace의 focus에만 outline과 box-shadow를 표시하지 않으며 링크, 버튼과 form control의 keyboard focus ring은 유지한다.

---

# 5. 프로필

Desktop 프로필 하위 내비게이션은 부가 설명 없이 항목명만 표시하고, 첫 진입 시 모든 항목이 고정 헤더 아래 화면에 한 번에 노출되도록 한다. 항목명 앞에는 구분용 icon만 두고 항목의 접근 가능한 이름은 항목명 그대로 유지한다.

학력·경력·자격증·어학·수상 목록 항목은 다음 표현 규칙을 공유한다.

- 항목마다 종류를 구분하는 icon, 제목, 부제, 상태 배지, 서버 값에서 유도한 사실 목록과 서술형 설명을 같은 카드 안에서 위계로 구분한다.
- 배지는 학력 `최종 학력`·재학 상태, 경력 `재직 중`, 자격증·어학 `유효기간 지남`처럼 이미 저장된 값에서만 만들고 새 상태를 추정하지 않는다.
- 날짜는 `YYYY.MM.DD`로 표시하고 기간은 `시작 ~ 종료`로 묶으며, 진행 중인 경력은 종료 자리에 `현재`를 사용한다. 값이 없으면 해당 사실을 표시하지 않는다.
- 경력은 timeline으로 표현하되 rail과 marker를 카드 안쪽 icon 열에 두어 목록 경계 밖으로 잘려 보이지 않게 한다.
- 목록 상단 도구 영역은 왼쪽에 서버 `totalElements` 기준 등록 건수, 오른쪽에 정렬을 두고 정렬 label은 줄바꿈하지 않는다.

## 5.1 `/profile/basic`

### Form

- 이름
- 간단 소개
- 졸업(예정)일
- 희망 직무
- 희망 산업
- 희망 지역

작은 `지원 자격 확인 정보` 영역에서 근무 가능일, 병역 상태, 해외여행 가능 여부, 채용 결격 제한 여부를 별도로 저장한다. 이 값은 자기신고이며 실제 지원 단계에서 별도 확인될 수 있고 미선택 값은 분석에서 알 수 없음으로 처리된다는 안내를 표시한다.

API:

- `GET /profile`
- `PUT /profile`
- `GET /profile/eligibility`
- `PUT /profile/eligibility`

미완료 안내는 남은 필수 항목 배지를 같은 카드 안에 `남은 항목` label과 함께 묶어 표시하고, 각 form section 제목에는 구분용 icon을 둔다.

기본 정보 Form의 변경 상태와 `변경 사항 저장` action은 모든 입력 항목 뒤 페이지 하단에 둔다.
닉네임은 기본 정보 Form에 포함하지 않는다. 보호 화면 상단의 현재 닉네임을 누르면 접근 가능한 Modal을 열고 `PATCH /account/display-name`으로 별도 저장한다.

## 5.2 `/profile/education`

- 학력 카드 목록
- 추가·수정 Modal
- 서버가 계산한 `최종 학력` 배지
- 삭제 확인
- `고등학교 < 대학교(전문학사) < 대학교(학사) < 대학원(석사) < 대학원(박사)` 학력 단계 선택
- 재학 상태는 `재학`, `휴학`, `졸업 예정`, `졸업`, `중퇴`로 표시하고 server enum 문자열을 화면에 노출하지 않음

사용자가 최종 학력을 직접 지정하는 action은 제공하지 않는다. 생성·수정·삭제 후 서버가 가장 높은 학력 단계를 다시 계산하고 같은 단계에서는 상태·날짜·등록 순서로 결정한다.

API:

- `GET /profile/educations`
- `POST /profile/educations`
- `PUT /profile/educations/:id`
- `DELETE /profile/educations/:id`

## 5.3 `/profile/certifications`

자격증 목록·Form. 증빙 문서 연결.

## 5.4 `/profile/languages`

어학 시험, 점수, 응시·만료일.

## 5.5 `/profile/awards`

수상 목록과 설명, 증빙 연결.

## 5.6 `/profile/careers`

경력 Timeline, 역할·성과 편집.

## 5.7 `/profile/activities`

사용자가 직접 입력한 대외활동만 관리하는 전용 화면이다. 문서에서 AI가 추출한 경험은 이 목록에 자동 편입하지 않으며 기존 `/profile/evidence` 화면 경로는 이 route로 redirect한다.

- 활동 제목, 종류, 진행 주체, 기간, 진행 중 여부, 역할, 활동 내용, 성과, 관련 링크를 등록·수정한다.
- 활동 종류는 동아리·봉사활동·공모전·서포터즈·기자단·학생회·교육 프로그램·해외 경험·기타다.
- 제목·종류·진행 주체·활동 내용만 필수이며 오류는 각 입력 옆에 표시한다.
- `자소서·면접 소재 후보로 사용`을 명시적으로 켠 활동만 후속 AI의 승인 소재 snapshot에 포함한다. 직접 등록했다는 이유만으로 자동 사용하지 않는다.
- 빈 화면은 AI 추출 결과를 대신 표시하지 않고 첫 활동 등록 action과 향후 활용 의미를 안내한다.
- 삭제 전 확인 Modal은 연결된 소재 후보도 함께 제거되지만 업로드 자료에는 영향이 없음을 설명한다.

API: `GET|POST /profile/activities`, `GET|PUT|DELETE /profile/activities/:id`.

---

# 6. 문서

## 6.1 `/documents`

### 구성

- Upload Dropzone
- 문서 유형 선택
- 지원 확장자·용량 안내
- 문서 Table/Grid
- 파싱 상태
- 근거 추출 상태
- 업로드 일시
- 다시 분석·원본 열기·삭제

API:

- `POST /documents`
- `GET /documents`
- `POST /documents/:id/reparse`
- `POST /documents/:id/download-url`
- `DELETE /documents/:id`

## 6.2 `/documents/:documentId`

### 영역

- 업로드한 원본 파일명·형식·크기·등록 시점·최근 분석 시점
- `자료 확인`과 `경험·소재 정리`의 분리된 상태 및 안전한 오류
- 원본 열기 action과 스크롤 가능한 추출 내용 Preview
- 수동 텍스트 편집
- 기본 요약 상태와 별도의 접힌 분석 과정 상세
- `검토 전`, `활용 승인`, `활용 제외`, `원본 삭제됨` 소재 검토 목록
- 개별·선택·검토 전 전체 승인, 개별·선택 제외, 승인 취소 후 재검토

API:

- `GET /documents/:id`
- `GET /documents/:id/text`
- `PUT /documents/:id/manual-text`
- `GET /profile/evidence?documentId=:id`

`PARSED + evidenceExtractionStatus=FAILED`는 추출 text를 유지하고 문서 업로드 실패로 표시하지 않는다. safe error, Agent Run과 재처리 CTA를 제공한다. 문서 삭제 성공 즉시 상세·download·cache에서 제거하고 이후 404를 정상 삭제 결과로 처리한다.

소재 영역은 승인된 내용만 자소서·면접 소재 후보로 사용한다는 정책과 남은 검토 수를 먼저 보여 준다. 활용 제외는 원본 자료나 분석 이력 삭제가 아니며 언제든 `PENDING`으로 돌려 재검토할 수 있다. 별도 `정리된 결과` 대형 section은 두지 않고 소재 card와 partial 경고에 통합한다.

---

# 7. 채용 공고

## 7.1 `/jobs`

### 상단 Tab

- 지원 중 (`IN_PROGRESS`)
- 서류 제출 (`SUBMITTED`)
- 마감 (`CLOSED`)

선택된 Tab은 hover 중에도 brand 배경과 흰색 글자를 유지한다.

### Filter

- 회사·직무 검색
- 등록 기간: 사용자가 보유한 공고에 실제 존재하는 `연도 상반기|하반기`만 표시
- 기간 직접 설정: 시작 날짜만 입력하고 종료는 오늘로 고정
- 정렬

### Job Card

- 회사
- 공고·직무
- 상태
- 마감일
- 추출 상태
- 적합도
- 자기소개서·면접 준비 진행도
- 상태 변경 Menu

API:

- `GET /jobs`
- `PATCH /jobs/:id/status`

### 상태 표시 규칙

`CLOSED`로 변경돼도 `submittedAt`이 있으면 `서류 제출 이력 있음` 보조 배지를 표시한다.

업무 상태 `IN_PROGRESS|SUBMITTED|CLOSED`와 추출 상태를 별도 badge로 표시한다.

- `NEEDS_MANUAL_INPUT`: 업무 상태를 유지하고 “공고 내용을 자동으로 충분히 읽지 못했어요” 안내와 본문 직접 입력을 강조하며 분석을 비활성화
- `FAILED`: safe error, 추출 재시도와 수동 입력을 모두 제공
- `MANUAL_INPUT_PROVIDED`: 수동 본문으로 분석 가능

진행 단계는 `공고 페이지 불러오기 → 공고 내용 확인 → 공고 이미지 읽기(필요한 경우) → 채용 정보 정리 → 결과 저장`처럼 사용자 용어로 표시한다. Provider, 내부 step key, OCR engine 이름은 노출하지 않는다.

workflow v3도 공개 상태 enum과 단계 key를 바꾸지 않는다. 알려지지 않은 새 step key는 기존 안전한 fallback label로 표시하고, retry가 새 successor Run을 반환하면 해당 Run으로 진행 상태를 갱신한다. `NEEDS_MANUAL_INPUT`의 직접 입력 CTA, retryable Provider 실패의 재시도 CTA, SSE reconnect 상태는 서로 다른 의미로 유지한다.

## 7.2 `/jobs/new`

### 입력

- URL 필수
- 회사명 선택
- 직무명 선택
- 본문 직접 입력 선택
- 마감일 선택은 날짜, 오전/오후, 30분 단위 시각을 분리해 입력

OCR 사용 여부, 이미지 공고 여부 또는 텍스트 추출 방식을 고르는 control은 추가하지 않는다. URL만 등록하면 이미지 분기를 포함한 자동 처리가 내부에서 결정된다.

### 등록 결과

- 즉시 공고 상세로 이동
- 직접 입력 본문이 없으면 `QUEUED` URL 추출 Progress를 표시하고 usable 본문이 준비되면 `BALANCED` 공고 분석을 자동으로 이어감
- 직접 입력 본문이 있으면 `MANUAL_INPUT_PROVIDED`로 표시하고 URL 추출 Progress 없이 `BALANCED` 분석을 자동 접수
- 추출과 분석을 `공고 내용을 읽고 있어요 → 주요 업무와 지원 조건을 정리하고 있어요 → 내 경험과 비교하고 있어요 → 분석이 끝났어요`의 한 여정으로 표시
- 추출 실패 시 본문 직접 입력 Prompt를 제공하고 보완 완료 뒤 자동 분석을 이어감

API:

- `POST /jobs`
- `GET /agent-runs/:runId`
- `GET /agent-runs/:runId/events`

## 7.3 `/jobs/:jobId`

상세 페이지 내부 Tab.

### Overview Tab

- 공통 resource header의 회사·직무·상태·마감·원본 URL
- 긴 공고 제목은 header의 넓은 영역에서 `1.4–1.875rem` 크기와 최대 두 줄 말줄임으로 표시한다. 제목 전체는 native `title`로 보조하고 mobile에서도 문서 가로 폭을 늘리지 않는다.
- 회사·직무·근무 형태·위치·마감·본문 출처·최신 분석 상태 요약
- plain text 원문을 heading, 문단, 순서·비순서 목록, 안전한 link node로만 변환하는 읽기 전용 document view
- 긴 본문은 페이지 흐름에서 읽고 `전체 보기/접기`를 제공하며 작은 내부 scroll box나 `v-html`을 사용하지 않음
- 본문 수정 action에서만 textarea editor로 전환하고 저장·취소·version conflict를 유지
- 마감일과 출처
- 상태 변경
- 편집
- URL 재추출
- 삭제

API:

- `GET /jobs/:id`
- `PUT /jobs/:id`
- `PATCH /jobs/:id/status`
- `POST /jobs/:id/retry-extraction`
- `DELETE /jobs/:id`

### Analysis Tab

- 최초 자동 분석 진행 단계와 안전한 실패·본문 보완 CTA
- 최초 화면에서는 품질 dropdown과 큰 수동 실행 card를 노출하지 않음
- 결과가 있거나 자동 접수가 차단된 때만 `최신 정보로 다시 분석`을 제공하며 프론트 요청은 `BALANCED`로 고정한다. `BALANCED`·`ECONOMY` 선택 문구와 재분석 옵션은 노출하지 않는다.
- 결과가 있으면 `자기소개서 준비하기`만 primary action으로 표시하고 재분석·프로필 보완·공고 수정·면접 준비는 낮은 우선순위의 보조 행동으로 제공한다. 결과가 없거나 실패한 상태에서는 분석 재실행을 primary action으로 사용한다.
- 최근 분석 Run이 `FAILED|CANCELLED|INTERRUPTED`이면 실패 카드 안에 단일 `공고 분석 재실행` CTA를 제공한다. 서버가 범용 retry를 허용하면 기존 lineage retry를 사용하고, `retryable=false`이면 현재 공고 version으로 `forceReanalyze=true`인 새 `BALANCED` 분석을 요청한다.
- 진행 여정 문구는 한 줄로 유지하고 Desktop의 네 단계는 각 문구 폭과 무관하게 단계 블록 사이 여백을 균일하게 배분한다. structured output·timeout·Provider·데이터 부족 safe code는 내부 용어 대신 보존되는 데이터와 다음 행동을 설명하는 사용자 문구로 변환한다.
- 지원 가능 여부
- 적합도·분석 커버리지·강점 수·보완점 수 요약과 점수 tooltip 안내
- 최신 결과 제목은 저장된 분석 버전을 노출하지 않고 적합도와 지원 가능 여부에 맞춘 간결한 판단 문구를 표시한다.
- 주요 업무
- 필수·우대
- 강점
- 부족한 점
- 매칭 근거
- 분석 결과 기록

결과 전체는 한 개의 절제된 report panel로 구성하고 내부 판단, 요건 매칭, 공고 핵심, 강점·보완점, 활용 경험과 조건별 근거는 중첩 card 대신 1px 구분선과 여백으로 연결한다. 상단 판단 영역은 적합도 270° gauge, 지원 가능성, 분석 커버리지, 해석 문장과 `자기소개서 준비하기`를 함께 노출한다. `MATCHED|PARTIAL|MISSING|UNKNOWN` 요건 수는 직접 라벨·아이콘·텍스처를 가진 100% 누적 막대로, category별 획득/배점은 공통 기준선의 가로 막대로 표시한다. `UNKNOWN`은 0점 불일치가 아니라 분석 커버리지 부족으로 설명한다. 화면 점수는 API 원값을 바꾸지 않고 소수점 없이 가장 가까운 5점 단위로 반올림한다. 주요 업무·필수·우대는 `analysisSummary`를 `핵심 요약`으로 먼저 표시하고 원문 bullet·항목 수는 기본 접힘 상태의 상세로 제공한다.

강점과 부족한 점은 넓은 의미 색상 배경이나 번호 원형을 사용하지 않고 `내 강점`과 `보완 포인트`의 구분선 목록으로 나눈다. 성공·주의 색상은 각 영역의 작은 상단선과 상태 text처럼 실제 의미가 있는 위치에만 쓴다. 기준별 점수는 전체와 `MATCHED|PARTIAL|MISSING|UNKNOWN` filter를 제공하고 한 페이지에 5개씩 표시한다. filter를 바꾸면 첫 페이지로 돌아가며 criterion 설명과 연결 근거는 `판단한 이유와 연결 경험`으로 기본 접힘 상태를 유지한다. 공고 핵심·criterion·분석 이력의 disclosure trigger는 44px 이상 target, visible label과 open 상태 회전 indicator를 제공한다. 분석 이력은 숫자 version을 주 제목으로 노출하지 않고 `현재 결과`와 분석 시각을 사용하며 전체 기록은 기본 접힘 상태로 제공한다.

Desktop은 216px 2중 ring으로 적합도와 커버리지를 함께 표시하고 지원 가능성·분석 커버리지 요약을 유지한다. Mobile은 104px 단일 적합도 ring과 결정 문장을 나란히 배치하고 커버리지·분석 시각을 meta 한 줄로 흡수하며 나머지 요약 tile은 숨긴다. 단일 primary action은 52px 이상 전폭으로 첫 viewport에 노출하고 category 막대는 기본 접힘, 상태 legend는 네 label row로 전환한다. 가로 scroll은 상태 filter와 tab에만 허용한다.

주요 업무·필수 지원 자격·우대 사항의 출처가 과거 저장 결과에서 JSONPath·내부 객체 경로로 전달되면 원문을 노출하지 않고 `공고 본문`으로 표시한다. 새 분석 결과는 한국어 구역명 또는 출처 없음으로 제공한다.

`analysisOutdated=true`이면 기존 분석을 유지하고 상단의 얇은 bordered disclosure에 변경 항목 수를 표시한다. reason은 기본 접힘 상태에서 사용자가 펼쳐 확인하며 재분석은 낮은 우선순위의 보조 행동으로 제공한다. 넓은 노란 배경이나 별도 `OUTDATED` badge로 판단 영역을 밀어내지 않고 downstream 기능도 일괄 차단하지 않는다. `Eligibility`와 `fitScore`는 서로 다른 행으로 표시하고 `INELIGIBLE` 점수도 그대로 표시한다.

resource header 아래 상세 tab은 상단 header를 피한 sticky navigation layer로 표시하고 tab과 본문 사이에 공통 `layout-tabs-body-gap`을 둔다. active tab은 굵기·brand soft background·하단 indicator를 함께 사용하며 hover/focus에서도 유지한다. 모바일은 tab을 가로 scroll한다.

다음 문구를 점수 가까이에 항상 표시한다.

> 적합도 점수는 합격 가능성이 아니라 등록된 정보와 공고 요구사항의 일치도를 나타냅니다.

API:

- `POST /jobs/:id/analysis`
- `GET /jobs/:id/analyses`
- `GET /jobs/:id/analyses/latest`

### Cover Letter Tab

- 자기소개서 존재 여부
- 문항별 현재 답변 Preview
- 검증 상태
- 편집 이동

API:

- `POST /jobs/:id/cover-letter`
- `GET /cover-letters?jobId=:id`
- `GET /cover-letters/:coverLetterId`로 문항·current answer·검증 preview 조회

### Interview Tab

- 질문 세트
- 조사 상태
- 최근 모의 면접
- 면접 준비 생성

API:

- `POST /jobs/:id/interview-preparations`
- `GET /interview-question-sets?jobId=:id`
- `GET /mock-interview-sessions?jobId=:id`

---

# 8. 자기소개서 편집 `/cover-letters/:coverLetterId/edit`

## 8.1 화면 구조

```text
[1] 상단 고정 영역(제목 · 상태 · 답변/검토 수 · 저장 상태 · 완료 조건 요약 · 단일 primary 행동)
[2] 좌: 문항 목록(번호 · 질문 preview · 문항별 작성·검토 상태 · 문항 추가)
[3] 중앙: 현재 문항 · 답변 편집기 · 글자 수 · 저장 상태 · 저장/검토/다시 쓰기 행동
[4] 우: 작성 도움 tab(공고 요구사항 / AI 검토 결과) · 편집기 아래 `답변에 사용할 소재` 펼침 영역
[5] 보조 sheet(문항 추가 · 문항 수정 · AI 설정 · 버전 기록 · 작성 완료 점검)
```

사용자가 인식해야 하는 흐름은 `문항 선택 → 답변 작성 또는 AI 초안 → 저장 → AI 검토 → 작성 완료` 하나다. 페이지 진입 직후 현재 문항과 편집기가 보여야 하며 진행 현황·AI 완료·저장 알림을 편집기 위에 큰 card로 쌓지 않는다. 상단 영역은 본문과 함께 스크롤되며 화면에 고정하지 않는다. 스크롤 중 문항 목록과 작성 도움만 각 열에서 따라 움직인다.

문항은 좌측 세로 `role="tablist"`(`aria-orientation="vertical"`) 목록으로 두고 번호만 표시한다. 상태는 색 점으로, 전체 질문과 상태 문구는 tab의 접근성 이름·hover title과 중앙 영역 제목으로 제공한다. 좁은 화면 sheet 목록에서는 질문 preview와 상태 문구를 함께 보여 준다. tab은 `ArrowUp`·`ArrowDown`·`Home`·`End` 이동과 roving `tabindex`를 지원하고, 선택한 문항의 작업 영역이 `role="tabpanel"`이 된다. 좁은 화면에서는 문항 목록과 작성 도움을 sheet로 전환한다.

한 시점에 primary로 강조하는 행동은 하나다. 상단 영역은 상태에서 유도한 단일 행동(`첫 문항 추가하기`·`AI 초안 만들기`·`AI 검토 받기`·`남은 확인 사항 보기`·`N번 문항 이어 쓰기`·`작성 완료`·`보관 해제하고 이어 쓰기`)을 보여 주고, 저장하지 않은 답변이 있을 때는 강조를 편집기 옆 `답변 저장` 하나로 넘긴다. `문항 추가`, `문항 수정`, `버전 기록`, `AI 작업 상세`는 secondary·tertiary로 낮춘다.

AI 설정은 기본 화면에 펼쳐 두지 않고 `AI 초안 만들기`·`AI로 다시 쓰기`를 눌렀을 때 sheet로 연다. 이미 답변이 있으면 문구를 `AI로 다시 쓰기`로 바꾸고 지금 답변이 지워지지 않고 새 버전으로 저장된다는 점, 이전 버전을 되돌릴 수 있다는 점, 저장하지 않은 내용이 있다는 점을 실행 전에 알린다. 답변에 쓸 소재는 편집기 아래 `답변에 사용할 소재` button에서 펼친다. 이 영역은 화면 가운데 modal이 아니라 button에 붙어 펼쳐지고, 다른 영역 위에 겹쳐 떠서 아래 내용을 밀어내지 않는다. 고른 소재가 다음 AI 초안에 쓰인다는 점, 선택 개수와 모두 해제, 아직 쓰지 않은 소재와 이 답변에 이미 쓴 소재를 구분해 보여 주며 확인한 경험이 없으면 자료 등록 경로를 제공한다. 우측 작성 도움은 `공고 요구사항`과 `AI 검토 결과` tab만 두고 소재 선택과 섞지 않으며, 편집 영역과 같은 높이 안에서만 스크롤한다. 숫자만 나열하지 않는다. 버전 기록과 작성 완료 점검도 sheet로 열며, 작성 완료 조건은 상단 요약에서 언제든 확인하고 부족한 조건마다 해당 문항으로 이동할 수 있다. 이 화면들은 새 API를 만들지 않고 자기소개서 상세, 최신 공고 분석, 확인한 경험, Agent Run 상태에서 유도한 정보만 사용한다.

AI 작업 진행은 진행 중이거나 사용자가 손봐야 하는 실패일 때만 한 줄 요약(진행 문구·상태·진행률) disclosure로 보여 준다. 성공으로 끝난 작업의 완료 배너는 화면에 남기지 않고 결과는 답변·검토 상태와 알림으로 전달한다. 연결 상태·문항별 결과·`AI 작업 상세` link는 펼쳤을 때만 노출하고 재시도가 필요하면 처음부터 펼친다. 완료 문항 목록은 질문 전체를 그대로 늘어놓지 않고 문항 번호와 앞부분을 한 줄 preview로 보여 주며 전체 질문은 접근 가능한 이름과 hover title로 보존한다.

TipTap 편집 본문은 답변 작업 영역의 사용 가능한 너비를 모두 사용하고 높이는 화면 크기에 맞춘다. 답변이 길어지면 페이지 전체가 아니라 본문만 스크롤해 문항, 글자 수와 저장 button이 한 화면에 남는다. 본문 글자 크기는 화면 기본 본문보다 작게 두어 실제 자기소개서 입력기와 비슷한 밀도를 유지한다. 편집기 focus는 바깥 wrapper를 파란 선으로 둘러싸지 않고 본문 배경의 미세한 변화로만 표시하며, 키보드 사용자가 focus 위치를 잃지 않게 한다.

`COVER_LETTER_GENERATION` run이 진행 중이면 답변 편집기를 읽기 전용으로 두고 저장과 제안 적용을 막는다. 사용자가 편집한 내용과 도착한 초안이 같은 문항에서 서로를 덮어쓰지 않게 하기 위해서이며, 진행 중 안내 문구를 편집기 위에 함께 표시한다. 진행 상태는 해당 문항 주변의 작은 영역으로만 두고 상세 실행 정보는 `AI 작업 상세` link로 옮긴다.

저장하지 않은 답변이 있는 상태에서 다른 문항으로 이동하거나 화면을 벗어나면 확인을 받는다. 저장 성공·삭제 같은 완료 안내는 화면을 차지하는 card 대신 toast로 표시하고, 409 비교 화면과 오류 안내는 보조 sheet 뒤에 가리지 않는다.

화면 문구는 사용자 언어를 사용한다. `ARCHIVED`·`DRAFT` 같은 enum 이름과 `버전 충돌`·`immutable` 같은 내부 용어를 사용자에게 노출하지 않고, 상태는 `작성 중 / 작성 완료 / 보관됨`, 검토는 `검토 중 / 문제없음 / 확인 필요 / 수정 필요`로 표시한다.

## 8.2 기능

- 문항 추가·수정·삭제·정렬
- 현재 상태에 따른 다음 행동 안내와 작성 단계 표시
- 공고 분석에서 확인한 내 강점·보완 포인트 확인
- 최대 글자 수와 현재 글자 수
- 관련 경험 선택과 공고 분석에 연결된 경험 우선 표시
- AI 초안 생성
- 사용자 수정
- 브라우저 로컬 임시 저장
- 명시적 서버 버전 저장
- 과거 버전 비교·복원
- 현재 버전 검증
- 검증 제안 선택 적용
- 문항별 최종화 확인 목록
- 최종화
- archive·unarchive

문항 등록 form은 문항 목록의 `문항 추가`로 sheet에서 연다. 문항 내용·최대 글자 수·메모 수정, 문항 삭제와 `앞으로 이동`·`뒤로 이동` 순서 변경은 현재 문항의 `문항 수정` sheet에서 제공한다. 버전 기록 sheet는 편집 중인 내용, 지금 답변, AI가 만든 버전, 사용자가 저장한 버전, 되돌려 만든 버전을 구분하고 복원이 과거 버전을 고치는 것이 아니라 새 버전을 만든다는 점을 문구로 밝힌다.

AI 초안 sheet는 서버의 `GET /cover-letters/ai-models` 응답으로 모델 dropdown을 구성하고 추천 모델을 최초 선택한다. 사용자가 선택한 exact model ID는 초안 생성과 현재 버전 검증 요청에 사용한다. 모델 목록을 불러오지 못했거나 선택값이 없으면 생성·검증을 비활성화하며 프론트 코드에 모델 목록을 중복 하드코딩하지 않는다. 문항 메모는 AI가 따라야 할 작성 방향으로 생성 context에 포함되지만 사실 근거로 간주하지 않는다고 안내한다.

## 8.3 API

- `GET /cover-letters/:id`
- `POST /cover-letters/:id/questions`
- `PUT /cover-letters/:id/questions/:questionId`
- `DELETE /cover-letters/:id/questions/:questionId`
- `PATCH /cover-letters/:id/questions/order`
- `POST /cover-letters/:id/generate`
- `GET /cover-letter-questions/:questionId/versions`
- `POST /cover-letter-questions/:questionId/versions`
- `POST /cover-letter-questions/:questionId/versions/:versionId/restore`
- `POST /cover-letter-answer-versions/:versionId/verify`
- `GET /cover-letter-answer-versions/:versionId/verifications`
- `POST /cover-letters/:id/finalize`
- `POST /cover-letters/:id/archive`
- `POST /cover-letters/:id/unarchive`

## 8.4 검증 표시

| 상태    | 사용자 라벨 | UI   |
| ------- | ----------- | ---- |
| PENDING | 검토 중     | 회색 |
| PASSED  | 문제없음    | 초록 |
| WARNING | 확인 필요   | 노랑 |
| FAILED  | 수정 필요   | 빨강 |

문장 클릭 시 연결 근거와 수정 제안 표시.

`PENDING|FAILED`는 최종화 버튼을 비활성화한다. `WARNING`은 해당 verification별 확인 checkbox를 제공하고 확인한 ID만 finalize request에 보낸다. current answer가 바뀌면 과거 검증을 fresh로 취급하지 않는다.

AI 검토는 Backend 최종화 조건상 선택이 아니라 필수다. 화면은 모든 문항의 답변 저장과 최신 답변 기준 검토 통과가 있어야 작성 완료할 수 있음을 같은 문구로 안내하고, 완료할 수 없을 때는 비활성 버튼만 두지 않고 남은 조건과 해당 문항 이동을 함께 제공한다.

`ARCHIVED` 상세은 읽기 전용이다. 과거 version·verification 조회는 허용하지만 title/question/answer save·restore·generate·새 verify·finalize는 비활성화한다. active 자기소개서가 없고 server `canUnarchive=true`일 때만 unarchive를 제공한다.

---

# 9. 면접 질문 세트 `/interview-question-sets/:questionSetId`

## 구성

### 조사 요약

- 조사 주제와 source coverage
- 검색 품질
- 회사·채용 프로세스 요약
- 출처 목록
- 공식·커뮤니티 구분
- 발행일·조회일

### 예상 질문

Filter:

- 이력서
- 자기소개서
- 기술
- 행동
- 회사
- 꼬리 질문

질문 Card:

- 질문
- 의도
- 평가 포인트
- 답변 가이드
- 관련 근거
- 꼬리 질문
- 답변 작성
- 피드백 요청

### API

- `GET /interview-question-sets/:id`
- `GET /research-runs/:id`
- `GET /research-runs/:id/sources`
- `GET /interview-questions/:id`
- `POST /interview-questions/:id/answer-versions`
- `POST /interview-answer-versions/:id/feedback`
- `GET /interview-answer-versions/:id/feedbacks`

`SourceCoverage`를 `SUFFICIENT|LIMITED|NONE`으로 표시한다. `LIMITED|NONE`은 실패가 아니라 출처 부족 경고이며 source-based가 아닌 질문을 구분한다. 조사 실패는 safe error와 새 research/question set을 만드는 retry action을 제공한다.

답변 feedback 접수 뒤 Agent Run을 연결하고 성공 이력만 feedback 목록에 표시한다. 실행 실패·취소는 빈 PENDING feedback row가 아니라 Agent Run safe error와 retry 가능 여부로 표시한다.

---

# 10. 모의 면접 `/mock-interviews/:sessionId`

## 10.1 시작 전 READY

설정 요약:

- 공고·직무
- 사용할 자기소개서
- 질문 세트
- 면접 유형
- 난이도
- 목표 질문 수
- 피드백 방식
- 압박 모드

버튼:

- 시작
- 취소

생성 화면은 `/jobs/:jobId/interview/mock/new`다. `/interviews` 빠른 작업은 공고 선택 뒤 이 route로 이동하며 type, 난이도, 목표 질문 수, feedback timing, pressure mode와 VERIFIED evidence 최대 5개를 입력한다.

## 10.2 진행 IN_PROGRESS

### 중앙 대화

- 면접관 질문
- 사용자 답변 입력
- 답변 전송
- 진행 질문 수
- 종료 버튼

### 우측 Context

- 현재 질문 유형
- 관련 프로젝트
- 남은 질문 수
- 즉시 피드백 모드일 때 피드백

### API

- `POST /mock-interview-sessions/:id/start`
- `POST /mock-interview-sessions/:id/messages`
- `POST /mock-interview-sessions/:id/complete`
- `POST /mock-interview-sessions/:id/cancel`

중복 전송 방지를 위해 전송 중 입력을 잠그고 client request ID를 사용한다.

start/message마다 새 UUID `clientRequestId`와 현재 session version을 보낸다. timeout이나 연결 단절 뒤 같은 ID를 유지해 성공 또는 실패 terminal 응답을 복구한다. `409 MOCK_TURN_IN_PROGRESS`는 처리 중 상태로 polling하고 새 ID를 자동 발급하지 않는다. 저장된 실패가 replay되면 안전 오류를 그대로 표시하며, 사용자가 명시적으로 새 유료 시도를 선택할 때만 새 ID를 만든다. 요청당 최대 chat 1회·20초를 적용하고 search/embedding은 사용하지 않으며 비용은 공통 전역 일일 예산에 포함한다.

## 10.3 완료 COMPLETED

- 종합 점수
- 강점
- 약점
- 질문별 피드백
- 다시 연습할 질문
- 추천 학습 주제
- 새 세션 시작

session `COMPLETED`와 feedback 상태를 분리한다.

- `QUEUED|RUNNING`: transcript는 열고 feedback skeleton·Agent Run 링크를 표시
- `FAILED`: transcript를 유지하고 safe error와 retryable Agent Run action 표시
- `SUCCEEDED`: 종합 feedback 표시
- `CANCELLED`: transcript를 유지하고 취소 상태 표시

API:

- `GET /mock-interview-sessions/:id`
- `GET /mock-interview-sessions/:id/messages`
- `GET /mock-interview-sessions/:id/feedbacks`

---

# 11. 목록 페이지

## 11.1 자기소개서 목록 `/cover-letters`

- 공고·회사·상태·최근 수정일
- `DRAFT`, `FINALIZED`, `ARCHIVED` Filter
- 편집 페이지 이동
- active row archive, archived row 조건부 unarchive
- archived read-only badge와 server action boolean 사용

API:

- `GET /cover-letters`

## 11.2 면접 준비 목록 `/interviews`

- 예상 질문 세트 목록
- 모의 면접 세션 목록
- 회사·직무·상태 Filter
- 질문 세트 또는 세션 상세 이동
- 모의 면접 새 세션 생성

API:

- `GET /interview-question-sets`
- `GET /mock-interview-sessions`

두 목록은 URL query와 pagination namespace를 분리한다.

- question set: `qsJobId`, `qsCoverLetterId`, `qsQuery`, `qsSourceCoverage`, `qsResearchStatus`, `qsSort`, `qsPage`, `qsSize`
- mock session: `mockJobId`, `mockQuery`, `mockStatus`, `mockFeedbackStatus`, `mockSort`, `mockPage`, `mockSize`

API 요청 시 각각 canonical API parameter 이름으로 변환한다.

## 11.3 AI 작업 내역 `/agent-runs`

- Workflow, 상태, 접수·최근 갱신 시각
- 실패·중단 Filter
- 이번 작업의 한도 대비 사용량과 재시도 가능 여부
- 상세 실행 이동
- terminal 작업의 개별 삭제
- 현재 페이지 terminal 작업 선택·전체 선택과 최대 100개 일괄 삭제
- `JOB_ANALYSIS` 작업은 결과 전체를 중복 표시하지 않고 해당 `/jobs/:jobId/analysis`로 이동하는 resource link 제공

API:

- `GET /agent-runs`
- `DELETE /agent-runs/:id`
- `POST /agent-runs/bulk-delete`

active 작업은 삭제할 수 없고 종료 뒤에만 삭제할 수 있다. 삭제 성공 시 목록·상세 cache에서 제거하되 실행 결과와 비용 audit이 보존된다는 확인 문구를 표시한다.
선택 control은 `선택`, 선택 삭제 button은 `삭제(선택 수)`로 간결하게 표시한다.

---

# 12. AI 작업 상세 `/agent-runs/:agentRunId`

## 구성

- Workflow 명
- 상태
- 진행률
- 기본 접힘 상태의 사용자용 분석 과정
- 모델 등급
- 소요 시간
- 이번 작업 사용량
- 안전한 오류 메시지
- 재시도·취소

API:

- `GET /agent-runs/:id`
- `GET /agent-runs/:id/events`
- `POST /agent-runs/:id/retry`
- `POST /agent-runs/:id/cancel`

실패 단계의 내부 프롬프트·민감 데이터·provider 오류·내부 step key는 표시하지 않는다. 세부 과정은 기본적으로 접고, 펼치면 `문서 내용 확인`, `주요 경험과 소재 정리`, `검토할 소재 구성`, `분석 결과 저장`처럼 사용자용 명칭으로 변환한다.

연결 직후 `snapshot`을 원천으로 사용하고 `progress|step|waiting_user|heartbeat|terminal` event를 적용한다. `stateVersion`이 낮거나 같은 event는 무시한다. 1/2/5/10/30초 backoff로 재연결하고 3회 실패하면 5초 REST polling으로 전환한다. SSE 단절만으로 run 실패를 표시하지 않으며 terminal snapshot 뒤 stream을 닫고 resource query를 invalidate한다.

`WAITING_USER`는 `requiredUserAction` deep link를 표시하고 일반 retry를 비활성화한다. `FAILED|INTERRUPTED`는 `retryable=true`, active run은 `cancellable=true`일 때만 action을 제공한다. provider/model, prompt, hash, reuse detail은 표시하지 않고 `highestModelTierUsed`만 안전한 모델 등급으로 보여 준다.

현재 API가 제공하는 `actualCostUsd`와 `reservedCostUsd`는 사용자 결제액이 아니라 내부 Provider 원가 estimate와 작업별 reservation이다. 일반 화면에는 USD 금액을 직접 노출하지 않고 `이번 작업 사용량`을 reservation 대비 비율로만 표시하며, 이 비율이 월간 제품 한도나 결제 금액이 아님을 안내한다. 월간 누적·제품 잔여량은 P8.7 usage API가 구현되기 전 임의 값으로 표시하지 않는다.

---

# 13. 설정

## 13.1 `/settings/account`

- 닉네임
- 비밀번호 변경
- 로그아웃
- 회원 탈퇴

API:

- `PATCH /account/display-name`
- `PATCH /account/password`
- `POST /auth/logout`
- `DELETE /account`

## 13.2 `/settings/ai`

- 기본 품질 모드: 절약 / 균형
- 고품질 최종 검토 사용 여부
- AI 비용 정책 안내(전체 서비스 일일 USD 10.00, 사용자 변경 불가)
- AI 데이터 처리 안내

API:

- `GET /settings/ai`
- `PUT /settings/ai`

일반 사용자 화면에는 provider/model 실명을 표시하지 않는다. 단, 자기소개서 AI 초안·검증 화면은 사용자가 exact OpenAI model을 직접 선택하는 기능 계약이므로 서버 allowlist의 모델명과 ID를 표시한다. `HIGH_QUALITY` 설정은 면접 답변 feedback의 품질 모드에만 적용한다. 비용 정책은 reset zone `Asia/Seoul`, 전체 서비스 일일 USD 10.00이며 사용자가 변경할 수 없음을 안내한다.

## 13.3 `/settings/usage` (`PLANNED` P8.7)

- 기능별 사용량, 남은 횟수, unlimited, reset 시각, 현재 실행 가능 여부
- 기간·feature filter와 usage history
- 과금 가능 quantity/unit과 `현재 무료로 제공되며 청구되지 않음` 안내
- 기능 한도 도달 시 reset과 가능한 다음 action
- loading/empty/stale/reconciliation 지연 상태

API:

- `GET /settings/usage`
- `GET /settings/usage/history`

Provider key/model/price item/internal cost/margin과 다른 사용자 정보는 표시하지 않는다. Provider 비용 예산과 제품 기능 한도를 같은 progress bar나 금액으로 합치지 않는다.

## 13.4 `/settings/privacy`

- 업로드 문서 수와 저장량
- 문서 관리 페이지 이동
- 회원 탈퇴와 데이터 삭제
- AI 처리 동의 정보
- 외부 검색 출처 저장 정책
- 로그에 본문을 남기지 않는다는 안내

API:

- `GET /settings/privacy`

외부 검색 출처 저장 정책은 versioned 제품 문구이며 `PrivacySettingsDto`가 반환하는 계정별 동적 field가 아니다.

- `GET /documents`
- `DELETE /account`

## 13.5 Backoffice pages (`PLANNED` P8.9-A)

| Route                       | 주요 구성                                                                             | prerequisite API                |
| --------------------------- | ------------------------------------------------------------------------------------- | ------------------------------- |
| `/backoffice/overview`      | active user, feature usage, internal cost, run/failure, rejection, readiness, lag KPI | `GET /backoffice/overview`      |
| `/backoffice/users`         | email/internal ID 검색, paged 최소 projection, 접근 audit 안내                        | `GET /backoffice/users`         |
| `/backoffice/users/:userId` | 계정 최소 정보, usage/cost/run/failure tabs, 원문 비노출                              | user detail와 user usage        |
| `/backoffice/usage`         | 기간·feature·workflow·outcome aggregate와 reconciliation 상태                         | `GET /backoffice/usage`         |
| `/backoffice/ai-costs`      | capability·quality·outcome별 내부 원가와 상위 비용 사용자                             | `GET /backoffice/ai-costs`      |
| `/backoffice/agent-runs`    | user/workflow/status/failure filter와 request/run drill-down                          | `GET /backoffice/agent-runs`    |
| `/backoffice/failures`      | category/code/workflow 집계, request ID와 recovery 상태                               | `GET /backoffice/failures`      |
| `/backoffice/configuration` | configuration/capability/vertical readiness, policy version, aggregation lag          | `GET /backoffice/configuration` |

모든 표는 loading/empty/error/stale 상태, keyboard focus, screen reader label과 모바일 horizontal overflow 대안을 가진다. 이력서·자기소개서·면접 답변/transcript, prompt/response, API key를 표시하지 않는다. MRR, revenue, subscriber, payment, invoice와 refund KPI는 없다.

P8.9-B mutation control은 이 화면에 포함하지 않는다. 별도 승인 전 override/cancel/retry/lock/kill switch button을 만들지 않는다.

---

# 14. Frontend 상태 관리

## Pinia

- `authStore`: 현재 사용자, 로그인 여부
- `uiStore`: Navigation, Drawer, Toast
- `draftStore`: 네트워크 전송 전 임시 편집 복구

## Vue Query

- profile
- documents
- evidence
- jobs
- job analyses
- cover letters
- interview sets
- mock sessions
- agent runs

Query Key 예시:

```text
['user', userId, 'profile']
['user', userId, 'documents', filters]
['user', userId, 'job', jobId]
['user', userId, 'jobs', filters]
['user', userId, 'coverLetter', coverLetterId]
['user', userId, 'questionSet', questionSetId]
['user', userId, 'agentRun', runId]
```

목록 filter는 URL query가 공유 가능한 원천이다. Zod에서 유효하지 않은 값을 제거해 canonical URL로 replace하고 filter 변경 시 해당 page를 0으로 reset한다.

- documents: `documentType`, `parseStatus`, `evidenceExtractionStatus`, `sort`, `page`, `size`
- evidence: `verificationStatus`, `evidenceCategory`, `documentId`, `sort`, `page`, `size`
- jobs: `status`, `query`, preset `postingYear`+`postingHalf` 또는 직접 설정 `postingStartFrom`, `sort`, `page`, `size`
- cover letters: `jobId`, `status`, `query`, `sort`, `page`, `size`
- Agent Runs: repeatable `workflowType`, repeatable `status`, `resourceType`, `resourceId`, `retryable`, `sort`, `page`, `size`
- `/interviews`의 두 목록은 11.2의 `qs*`·`mock*` URL namespace를 API parameter로 변환한다.

Browser draft는 `sessionStorage`만 사용한다.

- key: `schemaVersion/userId/resourceType/resourceId/questionId/baseVersionId`
- value: content JSON, baseVersionId, savedAt만 저장
- savedAt 기준 최대 24시간이며 browser session 종료 시 함께 사라짐
- server save, question 삭제, archive, logout, 탈퇴, 인증 사용자 ID 변경 시 해당 draft 삭제
- server base version과 다르면 자동 덮어쓰지 않고 server snapshot과 draft 비교·재적용 UI 제공
- Session 만료 중에는 이전 draft를 render하지 않고 같은 user 재인증 뒤에만 복구 후보로 표시

logout·탈퇴·401 auth reset·user ID 변경 시 EventSource 종료→in-flight query 취소→`queryClient.clear()`→Pinia reset→해당 user draft purge 순으로 처리한다.

---

# 15. 공통 AI 실패 UX (`PLANNED` P8.8)

`features/ai-failures/`는 API의 `AiFailurePresentationDto`를 받아 동일 category에 동일 title/message/CTA를 제공한다.

| category                     | 사용자 의미                  | 기본 suggested action               |
| ---------------------------- | ---------------------------- | ----------------------------------- |
| `INPUT_REQUIRED`             | 필수 입력 보완               | `EDIT_INPUT` 또는 `RESUME_RUN`      |
| `INSUFFICIENT_SOURCE_DATA`   | 출처 부족의 제한된 성공/보완 | `OPEN_RESOURCE`                     |
| `FEATURE_LIMIT_REACHED`      | 제품 기능 횟수 도달          | `OPEN_USAGE`                        |
| `COST_BUDGET_REACHED`        | 내부 비용 보호로 실행 불가   | `OPEN_USAGE` 또는 `NONE`            |
| `TEMPORARY_PROVIDER_FAILURE` | 일시 연결 문제               | retryable일 때만 `CREATE_NEW_RETRY` |
| `CONNECTION_RECOVERING`      | SSE/transport 재연결 중      | `WAIT_AND_REFRESH`                  |
| `OUTPUT_VALIDATION_FAILED`   | 안전한 결과 형식 확인 실패   | 정책에 따라 `CREATE_NEW_RETRY`      |
| `CONTENT_SAFETY_BLOCKED`     | 안전 정책 차단               | `EDIT_INPUT` 또는 `NONE`            |
| `CONFIGURATION_UNAVAILABLE`  | 운영 구성 미준비             | `CONTACT_SUPPORT`                   |
| `RESOURCE_CONFLICT`          | 최신 상태와 충돌             | `OPEN_RESOURCE`                     |
| `INTERNAL_FAILURE`           | 복구 불가능한 내부 오류      | `CONTACT_SUPPORT`                   |
| `CANCELLED`                  | 사용자/시스템 취소           | `OPEN_RESOURCE` 또는 `NONE`         |

기본 한국어 문구:

- 기능 한도: `오늘 사용할 수 있는 {기능명} 횟수를 모두 사용했어요. {reset 안내}`
- 비용 예산: `이 AI 작업을 시작할 수 있는 사용 한도를 확인해 주세요. 저장된 내용은 그대로 유지돼요.`
- Provider 일시 장애: `AI 서비스 연결이 원활하지 않아요. 입력한 내용은 저장되어 있으며 잠시 후 다시 시도할 수 있어요.`
- 결과 형식 실패: `AI 결과를 안전한 형식으로 확인하지 못했어요. 비용이 발생했을 수 있으며 다시 시도하면 새 사용량으로 기록될 수 있어요.`
- 출처 부족: `확인할 수 있는 공개 자료가 충분하지 않아요. 찾은 범위 안에서 질문을 정리했으며 출처가 부족한 항목은 별도로 표시했어요.`
- 연결 복구: `진행 상황을 다시 확인하고 있어요. 작업이 실패한 것은 아니며 마지막 상태는 그대로 유지돼요.`

- Panel은 데이터 보존 여부, request ID, usage 발생 가능성을 함께 표시한다.
- same request 복구와 새 usage를 만드는 retry를 같은 button label로 합치지 않는다.
- SSE 단절만으로 실패 panel을 표시하지 않고 연결 복구 상태를 사용한다.
- technical error name, Provider/model, 원문, raw response와 stacktrace를 표시하지 않는다.

# 16. Route Guard

- Public Landing: anonymous `/`; authenticated `/`는 `/dashboard`로 replace
- Public Only: `/signup`, `/login`
- Auth Required: `/onboarding`, `/guide`, `/dashboard`와 그 밖의 현재 제품 route
- Profile Recommended: 공고 분석·자기소개서·면접 기능
- Profile Recommended는 완료율 표시와 경고뿐이며 `profileCompleted=false`로 강제 redirect하지 않음
- 필수 데이터 부족 시 개별 workflow prerequisite에 따라 경고·409를 처리하고 프로필 이동 링크 제공
- 소유하지 않은 UUID 접근 시 404 페이지

`returnTo`는 한 번 decode한 same-origin registered auth-required path만 허용한다. `//`, scheme, host, backslash, CR/LF, login/signup/404는 거부하고 localStorage나 referrer에 저장하지 않는다.

---

# 17. 409 충돌 UX

- version 충돌은 mutation 자동 재시도와 optimistic overwrite를 금지한다.
- 최신 server snapshot과 사용자의 미저장 form/draft를 나란히 보여 주고 field별 재적용 또는 취소를 선택하게 한다.
- idempotency 처리 중과 mock turn 처리 중은 기존 request ID로 상태를 복구한다.
- active cover letter, archive, prerequisite 같은 상태 충돌은 server action boolean과 안정적인 error code로 CTA를 결정한다.

---

# 18. 핵심 E2E 시나리오

## 공개 진입

```text
anonymous /
→ Landing 확인
→ 로그인
→ PublicLayout 브랜드로 Landing 복귀
→ 시작하기
→ 회원가입
```

authenticated `/`는 Landing을 mount하지 않고 `/dashboard`로 replace하며, anonymous 보호 route는 안전한 `returnTo`와 함께 `/login`으로 이동한다.

## 시나리오 A

```text
가입
→ 온보딩
→ 이력서 PDF 업로드
→ 추출 근거 승인
→ 공고 URL 등록
→ 분석 완료
```

## 시나리오 B

```text
공고 상세
→ 자기소개서 생성
→ 문항 추가
→ AI 초안
→ 사용자 수정 버전 저장
→ 근거 검증
→ 최종화
→ 공고 상태 SUBMITTED
```

## 시나리오 C

```text
공고 Interview Tab
→ 면접 준비 생성
→ 검색 출처 확인
→ 예상 질문 답변 작성
→ 피드백
→ 모의 면접 생성·진행
→ 종합 피드백 조회
```

## 시나리오 D (`PLANNED` P8.6~P8.9-A)

```text
사용자 AI 기능 실행
→ /settings/usage 사용량·잔여량 확인
→ 동일 request replay에서 추가 소비 없음 확인
→ 기능 한도와 비용 예산의 서로 다른 실패 UX 확인
→ ADMIN Backoffice에서 aggregate usage·내부 원가·failure·Agent Run 확인
→ USER의 Backoffice 접근 거부와 access audit 확인
```
