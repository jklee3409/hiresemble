# GitHub 경험 수집과 Career Artifact 생성 설계

- 문서 상태: `APPROVED_TARGET_DESIGN`, 구현 상태 `GATE_0_3_IMPLEMENTED`
- 기준일: 2026-08-08
- 현재 구현 기준선: Flyway V28, canonical 경험 보관함과 GitHub provenance, GitHub Frontend, Career Artifact Backend, 11개 WorkflowType, feature 활성 OpenAPI 88 paths/118 operations·비활성 79 paths/107 operations
- 활성 공개 계약: [`../spec/`](../spec/)

이 문서는 GitHub URL에서 사용자의 프로젝트 경험과 강점을 추출하고, 사용자가 선택한 모델로 이력서 DOCX와 포트폴리오 PPTX 초안을 생성하는 구조를 현재 Hiresemble 구현 경계에 연결한다. Gate 0–1 GitHub Backend, Gate 2 Frontend와 Gate 3 Career Artifact Backend는 구현됐고 Gate 4 Frontend와 Gate 5 Private GitHub는 목표 상태다. 실제 상태는 코드와 각 `progress.md`를 따른다.

### Phase 1~3 실제 적용 상태

- Gate 0은 현재 V26 byte와 local Flyway 적용 checksum 일치, V26 SHA 고정, populated V26→V27 upgrade와 document canonical characterization으로 닫았다. V26 자체는 수정하지 않았다.
- Gate 1은 V27, `com.hiresemble.githubsource`, `github-ingestion-v1`, GitHub 공개 API 7개 operation으로 구현했다.
- GitHub vertical 전용 typed property는 production 기본 비활성이고 local·local-offline·test에서만 명시적으로 활성화한다.
- Gate 2는 `VITE_GITHUB_SOURCE_ENABLED=true`일 때만 `/profile/github`, profile tab, required-action route를 노출하는 Frontend로 구현했다. 값이 없거나 다르면 기존 UI와 route를 유지한다.
- GitHub Source 7개 operation, repository server 검색·pagination·선택, `GITHUB_INGESTION` SSE, refresh/delete와 경험 provenance 표시를 기존 API·DB·workflow 변경 없이 연결했다.
- 자동 검증은 WireMock·Fake·Testcontainers만 사용하며 실제 GitHub와 OpenAI 호출은 0회다.
- Gate 3는 V28, 조건부 Career Artifact 11개 operation, `RESUME_GENERATION|PORTFOLIO_GENERATION`, POI renderer, private object version·download/outbox를 구현했다. Career Artifact route·wizard·preview UI와 private GitHub 권한 연동은 추가하지 않았다.

## 1. 목표와 비목표

### 1.1 목표

1. 사용자가 GitHub 계정 또는 저장소 URL을 등록한다.
2. 서버가 공개 저장소의 제한된 자료를 안전하게 수집하고 AI가 프로젝트 경험과 강점 후보를 추출한다.
3. 후보를 현재 canonical 경험 보관함의 exact·semantic 중복 정책으로 처리한다.
4. 확실히 같은 경험은 새 카드를 만들지 않고 출처만 보강한다.
5. 신규·유사·충돌 후보는 사용자가 승인하기 전까지 `PENDING`으로 유지한다.
6. 사용자는 승인된 경험을 선택하고 서버가 제공하는 exact model을 골라 이력서 또는 포트폴리오 초안을 요청한다.
7. AI는 strict structured output만 만들고 서버가 DOCX/PPTX를 결정론적으로 렌더링한다.
8. 생성 파일은 비공개 Object Storage에 버전별로 저장하고 짧게 만료되는 다운로드 URL로 제공한다.
9. 이력서·포트폴리오가 없는 사용자는 생성 기능을 안내받을 수 있지만 자동 생성이나 강제 이동은 하지 않는다.

### 1.2 비목표

- 임의 Git repository clone 또는 사용자 code 실행
- URL만으로 사용자의 GitHub 계정 소유권이나 모든 code 기여를 증명
- GitHub commit 수, star 수 또는 repository 크기로 사용자 역량을 자동 평가
- 사용자가 입력한 Personal Access Token 저장
- AI가 DOCX/PPTX byte, OOXML, 임의 좌표·font·색상을 직접 생성
- 생성 파일을 기존 `documents` 분석 pipeline에 자동 등록
- 생성된 문서를 자동 제출하거나 외부 서비스로 전송
- 구현 phase 번호 확정; 현재 roadmap에 배치하는 결정은 별도 승인 대상

## 2. 현재 구현 기준선과 변경 경계

| 현재 경계  | 확인된 구현                                                                            | 이번 목표 설계                                            |
| ---------- | -------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| 문서 입력  | PDF/DOCX/TXT 업로드, parse·chunk·embedding·evidence 추출                               | 변경하지 않음                                             |
| 경험       | V27에서 V26 `experience_items` 정책에 GitHub provenance를 additive하게 연결            | Career Artifact는 VERIFIED canonical 경험만 소비          |
| 중복 기준  | cosine 0.94 + 공통 anchor 2개 + 수치 충돌 없음은 SAME, 0.82 이상 또는 수치 충돌은 검토 | threshold와 자동 병합 보수성 유지                         |
| AI runtime | 11개 고정 WorkflowType, GitHub·Resume·Portfolio Agent Run·step·retry·SSE·비용 기록     | Gate 4는 기존 Agent Run 계약을 소비                       |
| 모델 선택  | 자기소개서 생성·검증만 server catalog의 exact model 선택                               | 이력서·포트폴리오 생성에도 같은 방식 확장                 |
| 저장소     | document 전용 S3 adapter·5분 presigned URL·삭제 outbox                                 | GitHub snapshot과 career artifact는 별도 lifecycle로 추가 |
| Office     | Apache POI 의존성 존재, DOCX 입력 parse                                                | XWPF DOCX·XSLF PPTX 출력 renderer 추가                    |
| Frontend   | `/profile/github`, GitHub provenance, Agent Run monitor와 기존 profile/document 화면   | `/career-artifacts/**` 추가                               |

기존 `DocumentEvidenceService`는 문서 provenance 검증과 canonical 적용을 함께 소유한다. 구현 전 characterization test로 현재 결과를 고정한 뒤 다음 세 책임으로만 추출한다.

```text
DocumentCandidateProvenanceValidator ─┐
                                     ├─ CanonicalExperienceCandidateService
GitHubCandidateProvenanceValidator ──┘
```

- 공통 서비스: fingerprint, embedding candidate 비교, match decision, canonical item과 evidence link 반영
- 문서 validator: document/chunk/user/source revision 검증을 그대로 유지
- GitHub validator: repository/snapshot/source unit/user/commit 검증
- embedding 호출은 외부 호출이므로 transaction 밖에서 완료하고, 적용 transaction에서 active policy identity와 input hash를 다시 검증

문서 workflow의 저장 결과, 상태, reason count, threshold와 source lifecycle은 이 분리 전후에 같아야 한다.

## 3. 전체 데이터 흐름

```text
GitHub URL 등록
→ canonical URL과 owner/repository 식별
→ 계정 URL이면 공개 저장소 탐색
→ 사용자 저장소 선택
→ commit SHA 기준 immutable snapshot
→ source unit 필터링·secret masking·크기 제한
→ 경험·강점 strict structured extraction
→ source reference와 표현 검증
→ active embedding policy로 candidate embedding
→ 기존 canonical 경험과 exact·semantic 비교
→ SAME은 출처 보강, NEW/RELATED/CONFLICT는 PENDING
→ 사용자 승인
→ VERIFIED canonical EXPERIENCE만 생성 Context로 사용
→ 사용자가 유형·경험·exact model 선택
→ Resume 또는 Portfolio Agent Run
→ grounded structured draft와 fact check
→ 서버 DOCX/PPTX renderer
→ 구조 검증·비공개 Object upload
→ immutable artifact version과 provenance 저장
→ 5분 download URL
```

GitHub raw evidence는 downstream 생성 Context에 직접 넣지 않는다. 항상 canonical `EXPERIENCE` evidence 한 건으로 투영해 같은 경험을 여러 source 표현으로 중복 소비하지 않는다.

## 4. GitHub Source 설계

### 4.1 URL 유형과 정규화

초기 공개 계약은 다음 URL만 허용한다.

| Source kind  | 입력 형태                                 | 처리                                                |
| ------------ | ----------------------------------------- | --------------------------------------------------- |
| `ACCOUNT`    | `https://github.com/{owner}`              | account type을 조회하고 공개 repository 목록을 제시 |
| `REPOSITORY` | `https://github.com/{owner}/{repository}` | repository 한 개를 즉시 선택                        |

검증 규칙:

- scheme은 `https`만 허용한다.
- host는 `github.com|www.github.com`만 허용하고 `github.com`으로 정규화한다.
- user-info, port, query, fragment, percent-encoded slash와 제어문자를 거부한다.
- `.git` suffix는 제거한다.
- `tree|blob|issues|pull|commit|releases|actions` 등 추가 path segment는 기본 repository URL로 조용히 축약하지 않고 거부한다.
- 입력 URL에 직접 HTTP 요청하지 않는다. 파싱한 owner/repository로 서버가 `api.github.com` endpoint를 구성한다.
- 최종 식별자는 GitHub 응답의 external numeric ID와 canonical owner/name이다.
- 활성 `(user_id, canonical_url)`은 중복 등록할 수 없다.

### 4.2 공개·비공개 repository 경계

첫 구현은 URL만으로 접근 가능한 공개 repository만 지원한다.

- 익명 API quota를 보호하기 위해 ETag/conditional request, queue, global concurrency와 repository snapshot cache를 사용한다.
- upstream `403|429`는 reset/Retry-After를 snapshot하고 bounded retry 뒤 `GITHUB_RATE_LIMITED`로 종료한다.
- 사용자 PAT 입력·저장은 허용하지 않는다.
- private repository는 후속 GitHub App 연결에서만 지원한다.
- GitHub App은 `Metadata: read`, `Contents: read`의 최소 repository permission과 사용자가 선택한 repository만 요청한다.
- app private key, installation/user token은 source table, Agent Run input, log에 저장하지 않는다.

### 4.3 계정 URL repository 선택

계정 URL은 모든 repository를 자동 분석하지 않는다.

1. `DISCOVER_REPOSITORIES`가 user/organization type과 공개 repository metadata를 저장한다.
2. non-fork, non-archived, 최근 push repository를 추천 순서로 표시한다.
3. Run은 AI 호출 전 `WAITING_USER`와 `SELECT_GITHUB_REPOSITORIES`로 전환한다.
4. 사용자는 최대 10개 repository의 전체 선택 집합과 source version을 제출한다.
5. 같은 Run을 `QUEUED`로 재개한다.

repository URL은 단일 repository를 선택하고 WAIT step을 `SKIPPED`로 기록한다. 선택 전에는 AI 비용을 발생시키지 않는다.

### 4.4 수집 범위와 제한

수집 대상:

- repository metadata, description, topics, default branch, fork/archive/private flag, pushed time
- language byte summary
- preferred README
- build/package manifest
- `Dockerfile`, compose, CI workflow
- test directory와 대표 test
- architecture·design documentation
- 제한된 대표 source file

제외 대상:

- binary, image, archive, media
- `.env`, credential, private key, certificate, token 후보
- dependency/vendor/generated/build/cache directory
- lock file와 minified bundle
- symlink target과 submodule 외부 content
- 크기 또는 encoding 검증을 통과하지 못한 file

초기 운영값은 versioned configuration으로 관리한다.

| 항목                                  |                  초기 제안 |
| ------------------------------------- | -------------------------: |
| account source당 discovery repository |                        200 |
| 한 Run의 선택 repository              |                         10 |
| repository당 후보 file                |                         80 |
| 개별 text file                        |                     64 KiB |
| repository당 sanitized input          | 400,000 Unicode code point |
| repository당 candidate                |                         12 |
| 한 Run 전체 candidate                 |                         40 |

Git tree가 upstream 한도로 잘리면 snapshot에 `upstream_truncated=true`, `selection_complete=false`를 저장하고 사용자 결과에 `PARTIAL`을 표시한다. 잘린 tree를 전체 repository 분석으로 표현하지 않는다.

### 4.5 Snapshot과 source unit

snapshot은 repository default branch의 commit SHA와 retrieval policy version으로 식별하는 immutable 입력이다.

- 같은 repository/commit/retrieval policy는 재사용한다.
- 전체 sanitized text는 gzip JSON object로 비공개 저장한다.
- DB에는 file path, blob SHA, line 범위, content hash, 짧은 excerpt와 snapshot ordinal을 저장한다.
- AI에는 opaque source unit UUID와 sanitized content만 전달한다.
- model output은 입력 allowlist의 source unit UUID만 참조할 수 있다.
- refresh에서 commit SHA가 같으면 새로운 AI Run을 만들지 않고 최신 성공 상태를 반환한다.
- 정책 version이 달라지면 같은 commit도 별도 snapshot으로 처리한다.

### 4.6 개인 기여와 사실성

GitHub URL은 본인 소유·기여 증명이 아니다.

- 등록 전에 `본인 또는 실제 참여 프로젝트이며 결과를 직접 검토한다`는 확인을 받는다.
- 모든 신규 GitHub 경험·강점은 `PENDING`이다.
- repository owner가 사용자와 같아도 구체적인 역할·주도 여부를 자동 확정하지 않는다.
- organization repository는 repository 수준 사실과 명시된 contribution만 사용한다.
- 역할, 의사결정, 성과, 기간, 수치는 source에 명시된 경우에만 candidate에 포함한다.
- commit 수, star, fork, language 비율은 역량 또는 성과로 표현하지 않는다.
- limitation이 있으면 candidate와 상세 화면에 사용자 문장으로 보존한다.

허용 예:

- `Spring Boot와 PostgreSQL을 사용하는 API 프로젝트를 구성했다.` 단, source가 실제 구성을 보여 주는 경우
- `CI workflow와 테스트 구성이 확인된다.`

금지 예:

- `대규모 트래픽을 안정적으로 처리했다.` 근거가 없는 경우
- `팀의 아키텍처를 주도했다.` 역할 근거가 없는 경우
- `성능을 50% 개선했다.` 수치 근거가 없는 경우

### 4.7 Prompt injection과 비밀정보 방어

- README와 code는 모두 instruction이 아닌 `<untrusted_repository_content>` data block으로 전달한다.
- GitHub extraction chat step은 tool allowlist가 비어 있어야 한다.
- repository content의 URL이나 명령을 추가로 실행하지 않는다.
- source content에서 발견한 system/user 지시를 무시한다.
- known secret pattern은 masking하고 masking count만 안전한 metric으로 남긴다.
- raw repository text, URL, prompt, output과 secret 후보를 log·analytics·browser console에 남기지 않는다.
- artifact에는 source code excerpt나 repository image를 복제하지 않는다.

## 5. GitHub Agent Workflow

새 WorkflowType은 `GITHUB_INGESTION`, canonical version은 최초 구현 시 `github-ingestion-v1`이다.

| order | step key                           | 호출·산출물                    | 실패 정책                           |
| ----: | ---------------------------------- | ------------------------------ | ----------------------------------- |
|     1 | `VALIDATE_GITHUB_SOURCE`           | owner, URL, revision, 상태     | validation은 non-retryable          |
|     2 | `DISCOVER_REPOSITORIES`            | GitHub REST metadata           | 429/5xx/network bounded retry       |
|     3 | `WAIT_FOR_REPOSITORY_SELECTION`    | 같은 Run의 required action     | account만 WAIT, repository는 SKIP   |
|     4 | `CAPTURE_REPOSITORY_SNAPSHOTS`     | commit·tree·source units       | repository별 partial 허용           |
|     5 | `SANITIZE_AND_SELECT_SOURCE_UNITS` | bounded trusted snapshot       | deterministic failure non-retryable |
|     6 | `EXTRACT_GITHUB_CANDIDATES`        | strict project/strength output | correction guidance 최대 1회        |
|     7 | `VALIDATE_GITHUB_CANDIDATES`       | source ref·한국어·사실성       | invalid candidate filtering         |
|     8 | `EMBED_GITHUB_CANDIDATES`          | active policy embeddings       | provider retry·비용 기록            |
|     9 | `APPLY_CANONICAL_EXPERIENCES`      | item/evidence/link/embedding   | owner·policy·revision 재검증        |
|    10 | `FINALIZE_GITHUB_SOURCE`           | count·partial·latest run       | 멱등 apply                          |

GitHub fetch는 `WebSearchGateway`나 model tool이 아니라 `GitHubRepositoryGateway` 전용 port다. HTTP 호출은 transaction 밖에서 수행하며 response byte/time/redirect 한도를 적용한다.

### 5.1 Provider output

```text
GitHubExtractionOutput
├─ schemaVersion
├─ projectExperiences[0..12]
└─ strengths[0..12]

Candidate
├─ title
├─ content
├─ confidence
├─ sourceUnitIds[1..20]
├─ relatedProjectCandidateIndexes[0..12]  // strength만
└─ limitations[0..5]
```

- `projectExperiences`는 서버가 category `PROJECT`로 매핑한다.
- `strengths`는 category `STRENGTH`로 매핑한다.
- category, user ID, repository ID, snapshot ID와 verification status는 model이 출력하지 않는다.
- source unit 참조가 하나라도 허용 목록 밖이면 candidate를 저장하지 않는다.
- 일부 candidate 거절은 정상 filtering이며 전체 Run 실패와 구분한다.

## 6. Canonical 경험 중복 정책

### 6.1 Category 호환

현재 `evidence_category`는 자유 문자열이고 기존 데이터에 영문 code와 한국어 label이 공존할 수 있다. 신규 GitHub candidate는 `PROJECT|STRENGTH`만 저장하지만 비교 시 server-owned alias group을 사용한다.

| canonical group | 비교 alias 예시                |
| --------------- | ------------------------------ |
| `PROJECT`       | `PROJECT`, `프로젝트`          |
| `STRENGTH`      | `STRENGTH`, `강점`, `역량`     |
| `CAREER`        | `CAREER`, `경력`               |
| `ACTIVITY`      | `ACTIVITY`, `대외활동`, `활동` |

기존 row의 category와 fingerprint를 첫 migration에서 일괄 변경하지 않는다. exact 검색은 alias별 `(category, fingerprint(category,title,content))`를 조회하고 semantic 검색은 alias category 집합을 사용한다. 사용자 단위 apply lock과 기존 unique index를 유지한다.

### 6.2 판정과 저장

| 판정                | 조건                                           | 저장 결과                                         |
| ------------------- | ---------------------------------------------- | ------------------------------------------------- |
| exact               | alias별 fingerprint 일치                       | 새 item 없이 raw GitHub evidence와 기존 item 연결 |
| `SAME_EXPERIENCE`   | cosine ≥ 0.94, 공통 anchor ≥ 2, 수치 충돌 없음 | `CORROBORATING` source link                       |
| `RELATED_DIFFERENT` | cosine ≥ 0.82이나 SAME 조건 부족               | 별도 `PENDING` item과 suggested target            |
| `CONFLICT`          | 숫자·날짜 집합 충돌                            | 별도 `PENDING` item, 자동 병합 금지               |
| `NEW`               | 위 조건 밖                                     | 새 `PENDING` item과 canonical evidence            |

- 기존 VERIFIED item에 GitHub source가 추가돼도 사용자 승인 문구를 자동 덮어쓰지 않는다.
- existing REJECTED item을 refresh가 VERIFIED로 바꾸지 않는다.
- 신규 strength도 경험 보관함의 정규 item이며 최소 하나의 source unit과 관련 project 근거를 가진다.
- downstream은 VERIFIED canonical `EXPERIENCE`만 조회한다.
- source count는 refresh snapshot 수가 아니라 distinct document/repository 수를 함께 제공한다.

### 6.3 Refresh와 삭제

- 같은 raw claim은 `(user,repository,claim_key)`로 중복 생성을 막고 latest snapshot provenance만 갱신한다.
- 문구 또는 source가 달라져 semantic SAME이면 기존 item에 별도 corroborating evidence로 연결할 수 있다.
- source 삭제는 GitHub source·snapshot을 즉시 API에서 숨기고 object 삭제를 outbox로 처리한다.
- referenced raw evidence는 `SOURCE_DELETED` tombstone, unreferenced raw evidence는 삭제한다.
- 승인된 canonical item은 유지하고 source detail에는 삭제 marker만 제공한다.
- 승인되지 않고 다른 active source가 없는 orphan canonical item은 제거한다.

## 7. Career Artifact 경계

### 7.1 `documents`와 분리

`documents`는 사용자 입력 원천이고 `career_artifacts`는 AI 생성 출력이다. 다음 이유로 table, API, storage key와 삭제 lifecycle을 분리한다.

- 생성 결과가 다시 경험 추출 source가 되는 feedback loop 방지
- PPTX는 현재 document upload 허용 형식이 아님
- parse/evidence 상태와 generation/render 상태를 분리
- 생성 실패가 기존 업로드 문서 상태를 변경하지 않음
- 한 artifact의 과거 성공 version을 실패한 재생성 뒤에도 다운로드 가능

생성 DOCX/PPTX를 `documents`에 자동 등록하지 않는다. 사용자가 생성 DOCX를 다시 업로드하면 현재 document dedupe가 작동하지만, exact artifact checksum이면 재분석 불필요 안내를 제공하는 확장은 후속으로 남긴다.

### 7.2 Readiness와 선택성

모든 사용자는 생성 페이지에 접근할 수 있다. 이력서·포트폴리오 부재는 제안 표시 조건일 뿐 API 권한 조건이 아니다.

- hard precondition: VERIFIED canonical experience 최소 1개
- 권장 readiness: project/career 2개 이상, strength 1개 이상
- 품질 조건 미달은 warning이며 사용자가 계속 진행할 수 있다.
- uploaded document와 generated artifact 존재 여부를 server readiness projection이 계산한다.
- 자동 생성, 자동 redirect, blocking modal을 사용하지 않는다.
- Dashboard, documents empty state와 GitHub 완료 화면의 제안은 `나중에` 선택을 제공한다.

### 7.3 생성 입력

- `artifactType=RESUME|PORTFOLIO`
- artifact title
- VERIFIED `experienceItemIds`
- 포함할 구조화 profile section
- server catalog가 반환한 exact `model`
- server-owned `templateKey`
- renderer-only display name, 연락처, link와 포함 여부

contact/render profile은 LLM Context에 보내지 않고 최종 renderer만 사용한다. Run input에는 선택 ID·version·model·template·비민감 option과 `renderProfileHash`만 저장한다. 원문은 비동기 재시작 전에는 private `career_artifact_generation_requests`, 성공 뒤에는 해당 version의 owner-scoped render snapshot에만 보존한다. 승인된 경험·구조화 profile 본문에 우연히 섞인 이메일·전화번호·secret은 기존 privacy masker로 가리고 HTTPS URL은 placeholder로 치환한 뒤 bounded Context에 넣는다.

`includeProfileSections`의 server allowlist는 `PROFILE|EDUCATIONS|CERTIFICATIONS|LANGUAGE_SCORES|AWARDS|CAREERS|ACTIVITIES`다. `PROFILE`은 introduction·희망 직무/산업/지역 같은 비연락 정보만 포함하고 legal/display name은 LLM에 보내지 않는다. 자격증 credential number를 제외하며 `ACTIVITIES`는 `use_as_material=true` row만 사용한다. 선택한 section row는 ID/version으로 snapshot하고 workflow 시작 시 다시 검증한다. 비어 있는 section은 warning이며 생성 차단 사유가 아니다.

## 8. Resume 생성

새 WorkflowType은 `RESUME_GENERATION`, 최초 canonical version은 `resume-generation-v1`이다.

| order | step key                        | 책임                                             |
| ----: | ------------------------------- | ------------------------------------------------ |
|     1 | `LOAD_RESUME_REQUEST`           | artifact, owner, version, exact model 확인       |
|     2 | `BUILD_VERIFIED_CAREER_CONTEXT` | 선택 VERIFIED item과 구조화 profile snapshot     |
|     3 | `PLAN_RESUME`                   | section·evidence allocation                      |
|     4 | `DRAFT_RESUME_CONTENT`          | strict ResumeDraft JSON                          |
|     5 | `FACT_CHECK_RESUME_CONTENT`     | 모든 claim·수치·role provenance 확인             |
|     6 | `RENDER_DOCX`                   | XWPF deterministic renderer                      |
|     7 | `VALIDATE_DOCX`                 | 재개방, MIME, macro/external relation, 구조 검증 |
|     8 | `PERSIST_RESUME_VERSION`        | object metadata·version·provenance 원자 반영     |

Resume structured output은 headline, summary, skills, experience/project section, grounded bullet, warning과 evidence ref를 가진다. 개인정보와 renderer 좌표는 포함하지 않는다.

DOCX 정책:

- ATS 친화적인 단일 column
- core content에 text box, chart, icon, image 없음
- 표준 heading과 bullet
- 1~2 page 목표
- source에 없는 조직, 기간, role, 수치 생성을 금지
- 연락처는 renderer가 마지막에 삽입
- 서버 template와 font fallback만 사용
- A4이며 POI만으로 실제 page count를 확정했다고 주장하지 않고 1~2 page를 목표로 한다.

## 9. Portfolio 생성과 디자인 계약

새 WorkflowType은 `PORTFOLIO_GENERATION`, 최초 canonical version은 `portfolio-generation-v1`이다.

| order | step key                        | 책임                                        |
| ----: | ------------------------------- | ------------------------------------------- |
|     1 | `LOAD_PORTFOLIO_REQUEST`        | artifact, owner, version, exact model 확인  |
|     2 | `BUILD_VERIFIED_CAREER_CONTEXT` | 선택 VERIFIED evidence snapshot             |
|     3 | `PLAN_PORTFOLIO_STORY`          | interviewer 중심 narrative·slide allocation |
|     4 | `DRAFT_PORTFOLIO_SLIDES`        | strict slide schema                         |
|     5 | `FACT_CHECK_PORTFOLIO_CONTENT`  | claim·metric·role·source 검증               |
|     6 | `RENDER_PPTX`                   | XSLF server layout renderer                 |
|     7 | `VALIDATE_PPTX`                 | 재개방, slide/overflow/relation 검증        |
|     8 | `PERSIST_PORTFOLIO_VERSION`     | object·version·provenance 저장              |

### 9.1 System prompt 요구사항

- 독자는 채용 담당자와 면접관이다.
- 첫 60초 안에 사용자 역할, 주요 강점, 핵심 project를 이해할 수 있어야 한다.
- case study는 `문제 → 내 역할 → 행동 → 기술적 판단 → 결과 → 드러난 강점` 순서를 따른다.
- 강점은 최소 하나의 승인 근거와 연결한다.
- 기술 stack을 나열하지 않고 사용 맥락과 의사결정을 보여 준다.
- 한 slide는 한 핵심 message만 전달한다.
- 긴 문단, 작은 글자, 과도한 장식과 근거 없는 수치를 금지한다.
- 고유명사와 기술 용어는 보존하고 설명은 자연스러운 한국어로 작성한다.
- source가 부족하면 section을 생략하거나 warning을 반환하며 빈 내용을 창작하지 않는다.

### 9.2 Slide schema와 renderer 책임

Model이 결정하는 값:

- `slideType`
- title/subtitle/body item
- evidence refs
- 강조 문장
- `visualType=NONE|PROCESS|ARCHITECTURE|TIMELINE|IMPACT_METRICS`
- bounded node/edge/timeline/metric data

서버가 결정하는 값:

- 16:9 page size
- layout ID와 좌표
- font, color, spacing, contrast
- title/body 최소 크기
- 도형, connector와 overflow 처리
- server-approved icon과 template

초기 구성은 6~12 slide이고 COVER, PROFILE_SUMMARY, STRENGTH_OVERVIEW, PROJECT_CASE_STUDY, TECHNICAL_DECISION, IMPACT_AND_LEARNING, CLOSING type을 허용한다. title은 최소 28pt, body는 최소 18pt이고 한 slide는 한 핵심 message만 가진다. 외부 image, README image, source screenshot, remote font와 arbitrary OOXML은 금지한다.

## 10. Exact model 선택

현재 cover letter 전용 model catalog를 호환성 있게 일반화한다.

1. `modelsFor(workflowType)`와 `requireModel(workflowType, model)`을 추가한다.
2. 기존 `coverLetterModels|requireCoverLetter` 호출은 wrapper로 유지한다.
3. `RESUME_GENERATION|PORTFOLIO_GENERATION`만 새 exact model 선택 예외로 허용한다.
4. API 접수와 workflow 실행 시 모두 allowlist를 검증한다.
5. 선택 model을 Run input과 step hash에 포함하고 모든 chat step에 그대로 전달한다.
6. embedding은 선택 chat model과 분리해 active embedding policy를 사용한다.
7. frontend는 model ID를 하드코딩하지 않고 catalog 조회 실패 시 fail closed한다.
8. GitHub extraction은 사용자 exact model 선택 대상이 아니며 server cost policy를 따른다.

## 11. 목표 DB 계약

GitHub schema는 V26을 수정하지 않고 forward migration V27로 추가했고 Career Artifact는 V1~V27을 수정하지 않은 V28로 추가했다.

### 11.1 GitHub table

#### `github_sources`

`id,user_id,source_kind,account_type NULL,original_url,canonical_url,owner_login,repository_name NULL,source_status,repository_discovery_truncated,latest_agent_run_id NULL,source_revision,last_successful_sync_at NULL,version,timestamps,deleted_at NULL`

- active `(user_id,canonical_url)` unique
- status: `DISCOVERING|WAITING_USER|QUEUED|RUNNING|READY|PARTIAL|FAILED`
- source revision과 optimistic version은 별도 값

#### `github_repositories`

`id,user_id,external_repository_id,node_id,owner_login,repository_name,canonical_url,default_branch,is_private,is_fork,is_archived,description NULL,pushed_at NULL,timestamps`

- `(user_id,external_repository_id)` unique

#### `github_source_repository_links`

`user_id,github_source_id,github_repository_id,selected,selection_order NULL,discovered_at`

- owner composite FK와 source/repository pair unique

#### `github_repository_snapshots`

`id,user_id,github_repository_id,commit_sha,tree_sha NULL,github_api_version,retrieval_policy_version,selection_complete,upstream_truncated,snapshot_storage_key,checksum_sha256,sanitized_bytes,captured_at`

- immutable
- `(user_id,github_repository_id,commit_sha,retrieval_policy_version)` unique

#### `github_source_units`

`id,user_id,snapshot_id,unit_type,repository_path,blob_sha NULL,language NULL,line_start NULL,line_end NULL,content_hash,excerpt,snapshot_ordinal,created_at`

#### `github_evidence_unit_links`

`user_id,profile_evidence_id,source_unit_id,relation_kind,created_at`

- relation `PRIMARY|SUPPORTING`
- evidence와 unit의 owner/snapshot 일치

#### `github_snapshot_object_deletion_outbox`

`id,user_id,github_source_id NULL,snapshot_id NULL,storage_key,reason,status,attempt_count,next_attempt_at,claim_token NULL,lease_expires_at NULL,last_error_code NULL,created_at,completed_at NULL`

- source 삭제와 snapshot retention 정리는 기존 document outbox를 변경하지 않고 이 outbox를 사용한다.
- `snapshot_id`는 정상 lifecycle 삭제, null snapshot은 DB 반영 전 orphan upload 보상 경로다.
- active `(storage_key,reason)` unique로 같은 object 삭제를 중복 접수하지 않는다.

#### `profile_evidence` additive column

- `source_type`에 `GITHUB_REPOSITORY`
- `github_repository_id NULL`
- `github_snapshot_id NULL`
- `github_claim_key NULL`
- GitHub source shape에서 `source_entity_id=github_repository_id`, `document_id IS NULL`
- `(user_id,github_repository_id,github_claim_key)` partial unique

### 11.2 Career Artifact table

#### `career_artifacts`

`id,user_id,artifact_type,title,lifecycle_status,current_version_id NULL,latest_agent_run_id NULL,version,timestamps,deleted_at NULL`

- type `RESUME|PORTFOLIO`
- lifecycle `ACTIVE|ARCHIVED`
- 실패한 재생성은 current version을 바꾸지 않음

#### `career_artifact_versions`

`id,user_id,career_artifact_id,version_no,content_schema_version,content_json,template_key,template_version,model_id,agent_run_id,render_profile_snapshot,storage_key,mime_type,size_bytes,checksum_sha256,created_at`

- immutable. 단, parent soft delete 뒤 `render_profile_snapshot`을 `{}`로 scrub하는 단방향 privacy-erasure만 허용하고 나머지 column은 계속 변경할 수 없다.
- `(user_id,career_artifact_id,version_no)` unique
- 성공적으로 검증·업로드된 version만 생성
- storage key는 row의 user/artifact/version UUID와 artifact type으로 계산한 고정 경로와 정확히 일치한다.

#### `career_artifact_evidence_links`

`id,user_id,artifact_version_id,experience_item_id,profile_evidence_id,experience_version,evidence_version,usage_type,title_snapshot,content_snapshot,snapshot_hash,created_at`

- usage `PRIMARY_EXPERIENCE|STRENGTH|SUPPORTING_FACT`
- 생성 당시 provenance snapshot 보존

#### `career_artifact_generation_requests`

`id,user_id,career_artifact_id,agent_run_id,target_version_id,render_profile_snapshot,render_profile_hash,created_at,consumed_at NULL`

- `(user_id,id)`, `(user_id,agent_run_id)`, `(user_id,target_version_id)` unique이며 artifact와 Agent Run에 owner composite FK를 둔다.
- 한 Run에는 정확히 한 request가 있고 `target_version_id`가 deterministic Object Storage key를 결정한다.
- 성공 apply transaction은 snapshot을 immutable version으로 복사하고 request를 consumed 처리한다.
- retry successor는 model/template/evidence identity와 render profile을 transaction 안에서 복사하되 새 `target_version_id`를 할당한다.
- 공개 DTO, Agent Run input/checkpoint/log/metric에 row 또는 연락처 원문을 노출하지 않는다. artifact soft delete와 성공 version 없는 Run history 삭제 시 snapshot을 `{}`로 즉시 scrub하고 account deletion terminal 정리에서 row를 purge한다.

#### `career_artifact_object_deletion_outbox`

artifact/version delete와 DB 실패 orphan upload compensation을 담당한다. 두 reason 모두 artifact ID와 row identity에 맞는 storage key를 요구하고, artifact delete만 version ID를 가지며 orphan upload는 version ID가 null이다. 기존 document outbox를 변경하지 않는다.

### 11.3 Agent Run 확장

- workflow check에 `GITHUB_INGESTION|RESUME_GENERATION|PORTFOLIO_GENERATION`
- `agent_run_resource_links`에 `github_source_id|career_artifact_id`
- resource kind `GITHUB_SOURCE|CAREER_ARTIFACT`
- exactly-one, owner FK, workflow/resource parity trigger 갱신
- retry successor는 같은 source/artifact를 정확히 한 번 연결

## 12. 목표 API 계약

모든 mutation은 Session, CSRF와 owner 404를 사용한다. `POST /career-artifacts`, `POST /career-artifacts/{id}/generations`만 `Idempotency-Key`가 필수이고 archive, unarchive, delete는 optimistic version만 사용한다. 생성 request hash에는 type/title, 정규화한 experience/evidence ID·version, exact model, template key/version, profile section, artifact optimistic version과 정규화한 render profile digest를 포함하며 연락처 원문은 넣지 않는다.

### 12.1 GitHub source

| Method | Path                                               | 성공                                    |
| ------ | -------------------------------------------------- | --------------------------------------- |
| POST   | `/api/v1/github-sources`                           | source와 Run 생성, 202                  |
| GET    | `/api/v1/github-sources`                           | paged source 목록                       |
| GET    | `/api/v1/github-sources/{id}`                      | repository·snapshot·결과 count          |
| GET    | `/api/v1/github-sources/{id}/repositories`         | account discovery repository paged 목록 |
| PUT    | `/api/v1/github-sources/{id}/repository-selection` | 선택 저장 후 same-run resume, 202       |
| POST   | `/api/v1/github-sources/{id}/refresh`              | 새 revision Run, 202 또는 unchanged 200 |
| DELETE | `/api/v1/github-sources/{id}?version=`             | soft delete, 204                        |

### 12.2 Career artifact

| Method | Path                                                              | 성공                                 |
| ------ | ----------------------------------------------------------------- | ------------------------------------ |
| GET    | `/api/v1/career-artifacts/readiness`                              | 업로드·생성·VERIFIED 준비 상태       |
| GET    | `/api/v1/career-artifacts/ai-models?type=`                        | exact model catalog                  |
| POST   | `/api/v1/career-artifacts`                                        | artifact와 최초 generation Run, 202  |
| GET    | `/api/v1/career-artifacts`                                        | paged artifact 목록                  |
| GET    | `/api/v1/career-artifacts/{id}`                                   | current preview·latest Run           |
| GET    | `/api/v1/career-artifacts/{id}/versions`                          | immutable version 목록               |
| POST   | `/api/v1/career-artifacts/{id}/generations`                       | 재생성 Run, 202                      |
| POST   | `/api/v1/career-artifacts/{id}/archive`                           | active artifact 읽기 전용 보관       |
| POST   | `/api/v1/career-artifacts/{id}/unarchive`                         | current version을 유지한 active 복구 |
| POST   | `/api/v1/career-artifacts/{id}/versions/{versionId}/download-url` | 5분 URL                              |
| DELETE | `/api/v1/career-artifacts/{id}?version=`                          | soft delete, 204                     |

오류 code와 완전한 DTO field는 [`../spec/api.md`](../spec/api.md)의 planned 계약을 따른다.

## 13. Frontend 정보 구조

### 13.1 Route와 navigation

- `/profile/github` (`IMPLEMENTED`, `VITE_GITHUB_SOURCE_ENABLED`)
- `/career-artifacts` (`PLANNED`, Gate 4)
- `/career-artifacts/new?type=RESUME|PORTFOLIO` (`PLANNED`, Gate 4)
- `/career-artifacts/:careerArtifactId` (`PLANNED`, Gate 4)

상단 `이력서·자료` navigation은 `/documents|/career-artifacts`에서 active다. `/documents`에는 `업로드한 자료`, `/career-artifacts`에는 `AI로 만든 초안` switch를 제공한다. GitHub는 Career Profile Workspace의 별도 section이다.

### 13.2 `/profile/github`

- public-only·review policy 설명
- URL form과 participation 확인
- source card와 status
- account repository selector
- Agent Run 진행 상태
- `새 경험|기존 경험 보강|검토 필요|제외` count
- 경험 보관함 이동
- refresh/delete

필수 상태는 empty, validation, discovery, waiting selection, queued/running, ready, partial, rate limited, inaccessible, deleted confirm이다.

### 13.3 경험 보관함 확장

- `GitHub` source badge
- repository name, short commit SHA, captured time
- primary/supporting source excerpt
- source route
- distinct repository source count
- deleted source tombstone

### 13.4 생성 wizard

1. RESUME 또는 PORTFOLIO 선택
2. VERIFIED 경험·강점 선택
3. server catalog exact model 선택
4. renderer-only profile과 최종 요청 확인

model catalog가 실패하면 자동 기본값으로 실행하지 않는다. quality warning은 생성 금지가 아니라 사용자 확인 정보다.

### 13.5 Artifact 상세

- Agent Run 상태와 안전한 오류
- structured HTML preview
- portfolio slide thumbnail
- current download
- version history
- model을 다시 선택하는 regeneration
- 이전 성공 version 보존

브라우저에서 DOCX/PPTX를 parsing하지 않고 server `content_json` projection으로 preview한다.

### 13.6 선택적 제안

다음 조건일 때만 non-modal suggestion을 표시한다.

- 해당 유형의 active uploaded document 없음
- 해당 유형의 current generated artifact 없음
- GitHub에서 유래한 VERIFIED 경험 최소 1개

Dashboard, documents empty state와 GitHub 성공 summary에서 표시하며 `나중에`를 제공한다. 자동 Run, blocking modal과 강제 redirect는 금지한다.

## 14. Storage와 파일 검증

```text
users/{userId}/github-sources/{sourceId}/snapshots/{snapshotId}/snapshot.json.gz
users/{userId}/career-artifacts/{artifactId}/versions/{versionId}/content.docx
users/{userId}/career-artifacts/{artifactId}/versions/{versionId}/content.pptx
```

- bucket은 private이다.
- user filename을 storage key에 사용하지 않는다.
- MIME, size, SHA-256을 version row에 저장한다.
- upload는 DB transaction 밖, domain apply는 별도 transaction이다.
- upload 후 DB 실패는 즉시 삭제하고 실패하면 outbox에 넣는다.
- PERSIST checkpoint completion transaction이 commit될 때만 immutable version/current/request consumed 변경이 확정된다. rollback, cancel, interrupt와 history compensation은 in-memory Office byte를 폐기하고 transaction commit 뒤 orphan key를 정리해 row lock self-deadlock을 피한다.
- download URL은 owner 확인 뒤 5분 TTL과 attachment disposition으로 발급한다.
- renderer template에 macro, remote media, 임의 external relationship을 허용하지 않는다.
- artifact delete는 즉시 API 404, object delete는 outbox다.
- 생성 파일 상한 기본값은 typed configuration의 10 MiB다.
- initial catalog는 RESUME `resume-ats-v1` version `1`, PORTFOLIO `portfolio-interview-v1` version `1`이며 type과 맞지 않는 key는 요청 검증 오류다.
- HTTPS link는 hyperlink relationship 없이 안전한 일반 텍스트로 렌더링한다.
- 한글 font는 `Noto Sans KR`을 우선 지정하고 비한글 fallback을 함께 지정하되 원격 다운로드나 임의 embedding을 하지 않는다.

## 15. Package와 변경 소유권

```text
com.hiresemble.githubsource
├─ api
├─ application
├─ domain
└─ infrastructure

com.hiresemble.careerartifact
├─ api
├─ application
├─ domain
└─ infrastructure

com.hiresemble.ai.workflow.github
com.hiresemble.ai.workflow.careerartifact
com.hiresemble.ai.prompt.github
com.hiresemble.ai.prompt.careerartifact
```

기존 additive 변경 지점:

- `WorkflowType`, `RequiredUserActionType`
- `CanonicalWorkflowDefinitions`, `WorkflowRegistry`, AI runtime configuration
- exact model catalog와 model router
- Agent Run JDBC resource link·retry·owner resolver·failure handler
- `EvidenceSourceType`, experience store/DTO/source count
- common error code와 OpenAPI
- Frontend router, AppLayout, ProfileTabs, Agent Run presentation

직접 변경하지 않는 지점:

- document MIME allowlist
- document parser와 parse/evidence 상태
- job, cover letter, interview public DTO와 workflow
- 적용된 V1~V28 migration

## 16. 도입 순서와 Gate

### Gate 0 — 기준선 보호 (`DONE`)

- V26 checksum과 적용 환경 확인
- populated DB upgrade 검증
- document canonical apply characterization test
- category alias fixture
- 기존 backend baseline 실패 분리

### Gate 1 — GitHub backend (`DONE`, V27)

- source/snapshot/provenance migration
- public REST gateway와 sanitizer
- GITHUB workflow와 Agent Run resource
- dedupe, refresh, delete
- Fake/WireMock 검증

### Gate 2 — GitHub frontend (`DONE`, feature flag)

- typed build-time flag, route와 profile tab
- repository 검색·pagination·1~10개 선택과 version 충돌 확인
- focused Agent Run SSE·REST fallback, refresh/delete
- experience source badge·provenance·삭제 tombstone

### Gate 3 — Career Artifact backend (`DONE`, V28)

- artifact/version/provenance/private generation request/outbox migration
- exact model catalog와 `CAREER_ARTIFACT` Agent Run retry·cancel·history compensation
- Resume/Portfolio 고정 8단계 workflow와 grounded structured output 검증
- POI DOCX/PPTX renderer, private object lifecycle와 5분 attachment download
- feature 활성 88 paths/118 operations, 비활성 79 paths/107 operations OpenAPI 회귀

### Gate 4 — Career Artifact frontend (`PLANNED`)

- readiness, wizard, preview, download, regeneration
- 선택적 suggestion

### Gate 5 — Private GitHub (`PLANNED`)

- GitHub App installation과 token lifecycle
- 최소 permission, disconnect와 deletion
- webhook는 polling 비용·staleness 측정 뒤 별도 승인

각 Gate는 독립 feature flag로 비활성화할 수 있어야 하며 이전 Gate의 기존 기능을 변경하지 않는다.

## 17. 검증 Matrix

### 17.1 GitHub

- account/repository URL canonicalization
- invalid host, user-info, encoded slash, extra path SSRF 거부
- user/organization discovery
- repository selection WAITING_USER same-run resume
- direct repository SKIPPED
- 404, 403, 429, 5xx, timeout, retry-after
- truncated tree와 partial 상태
- malicious README prompt injection
- secret, binary, generated, vendor 제외
- same SHA snapshot reuse와 changed SHA refresh
- exact, SAME, RELATED, CONFLICT, NEW
- refresh source count 비증폭
- source delete 뒤 VERIFIED canonical item 유지
- two-user owner 404

### 17.2 Career Artifact

- readiness uploaded/generated 조합
- VERIFIED만 선택 가능
- exact model API·실행 이중 검증
- invalid model과 provider disabled fail closed
- evidence version 변경 race
- Resume/Portfolio fixed step order와 correction 상한
- invented source reference·metric·role 거부
- XWPF/XSLF re-open
- content type, macro, external relation, overflow 기준
- object upload/DB failure compensation
- regeneration 실패 뒤 prior current version 유지
- owner download 5분 TTL과 foreign 404

### 17.3 Frontend

- Zod boundary와 server enum
- repository selector mobile/keyboard/focus
- 409 자동 retry 없음
- model catalog failure CTA disable
- SSE reconnect와 REST fallback
- structured preview와 version switch
- optional suggestion 조건과 `나중에`
- active navigation `/documents|/career-artifacts`

실제 GitHub와 유료 AI Provider는 자동 test에서 호출하지 않는다. GitHub는 WireMock, AI는 Fake gateway, object storage는 local adapter/Testcontainers 경계로 검증한다.

## 18. 관찰성과 개인정보

허용 metric:

- source/run/repository count
- fetched file/byte/sanitized unit count
- snapshot reuse/truncated/rate-limit count
- candidate/applied/rejected reason count
- match kind count와 latency
- artifact generation/render/upload duration
- file size, version count, safe failure code

금지 log:

- GitHub URL·owner login 원문
- source code, README, snapshot excerpt
- 이력서·포트폴리오 content
- render profile 연락처
- raw prompt/response
- GitHub App secret/token

로그에는 내부 correlation ID와 source/artifact/run ID만 사용한다. 개인정보 삭제는 account deletion task가 GitHub snapshot과 career artifact object outbox 완료를 포함해야 한다.

## 19. 후속 Gate 전 확인 사항

1. Gate 0에서 available local Flyway history와 현재 V26 checksum 일치, V26 SHA와 populated V26→V27 upgrade를 확인했다. 새로운 영구 환경에는 배포 전 동일 checksum 확인을 반복한다.
2. 현재 `PROJECT|프로젝트` 등 category 실제 분포를 민감 content 없이 count로 확인한다.
3. anonymous GitHub quota로 예상 traffic을 감당할 수 있는지 산정한다.
4. GitHub App을 공개 MVP의 필수 연결로 할지 후속으로 둘지 운영 결정을 확정한다.
5. 운영 배포 환경의 Office font 가용성과 선택적 시각 fixture는 Gate 4/배포 검증에서 확인한다. renderer는 원격 font를 내려받지 않는다.
6. Gate 4 preview는 Office byte를 browser에서 parse하지 않고 구현된 structured version projection을 사용한다.
7. roadmap phase 번호는 기존 P8.5-V~P10 순서를 임의로 변경하지 않고 별도 승인으로 배치한다.

## 20. 관련 문서와 외부 계약

- [기능 명세](../spec/functional.md)
- [API 명세](../spec/api.md)
- [DB 명세](../spec/db.md)
- [페이지 명세](../spec/page.md)
- [기술 스택 명세](../spec/tech_stack.md)
- [전체 시스템 설계](system-architecture.md)
- [구현 계획](implementation-plan.md)
- [GitHub REST repository endpoints](https://docs.github.com/en/rest/repos/repos)
- [GitHub REST repository contents](https://docs.github.com/en/rest/repos/contents)
- [GitHub REST Git trees](https://docs.github.com/en/rest/git/trees)
- [GitHub REST rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)
- [GitHub App 개요](https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/about-creating-github-apps)
