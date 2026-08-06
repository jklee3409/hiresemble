# Cover Letter Frontend feature 안내

## 디렉터리 목적

P7 자기소개서 목록·편집 화면이 사용하는 URL filter, Vue Query, TipTap content, session draft, 충돌 비교와 Agent Run 진행 UI를 소유한다.

## 주요 파일 및 하위 디렉터리

- `queries.ts`: 자기소개서 query key·mutation과 terminal invalidation
- `filters.ts`: 목록 URL query parse·canonicalization
- `drafts.ts`: user/resource/question/base version별 24시간 sessionStorage draft
- `editorContent.ts`: 제한된 TipTap JSON과 글자 수 preview
- `editorFlow.ts`: 문항별 작성·검토 상태, 작성 완료까지 남은 조건, 단일 primary 행동 판정
- `conflict.ts`, `CoverLetterConflictPanel.vue`: 409 server snapshot·draft 비교/재적용
- `CoverLetterQuestionRail.vue`: 세로 tablist 문항 목록과 문항별 상태
- `CoverLetterAssistPanel.vue`: 공고 요구사항·AI 검토 결과 tab
- `CoverLetterMaterialPicker.vue`: 답변에 쓸 소재 선택과 이미 쓴 소재 구분
- `CoverLetterSheet.vue`: focus 가둠과 Escape 닫기를 가진 보조 sheet 껍데기
- `CoverLetterGenerationPanel.vue`: 초안 대상 문항·서버 제공 OpenAI model dropdown·비파괴 안내를 담은 AI 설정
- `CoverLetterVersionPanel.vue`: 버전 목록·비교·복원과 과거 저장본 검토 결과
- `CoverLetterCompletionPanel.vue`: 작성 완료까지 남은 조건, 문항 이동과 확인 필요 동의
- `CoverLetterTipTapEditor.vue`: 답변 작업대 전체 너비를 사용하고 wrapper focus ring 없이 본문 focus를 표시하는 접근 가능한 제한 node/mark editor
- `CoverLetterRunMonitor.vue`: 진행 중·실패한 generation·verification Run만 노출하는 한 줄 진행 표시와 결과 link
- [`progress.md`](progress.md): P7 feature 상태

## 구성 요소 역할

서버 상태는 Vue Query를 원천으로 사용하고 입력 중 본문만 sessionStorage에 둔다. AI 모델 선택지는 서버 catalog를 원천으로 사용하고 요청마다 선택 model ID를 그대로 전달한다. 저장·복원·제안 적용은 사용자의 명시적 행동으로 분리한다. 화면 상태 판정은 `editorFlow.ts` 한곳에서만 하고 component는 그 결과만 표시한다.

## 다른 디렉터리와의 의존 관계

- API·DTO는 [`../../shared/api/`](../../shared/api/index.md)에 있다.
- 목록·공고 tab·canonical editor page는 [`../../pages/`](../../pages/index.md)에 있다.
- route는 [`../../router/`](../../router/index.md), logout·401 purge는 shared session 경계를 사용한다.

## 변경 시 주의사항

Pinia/localStorage에 본문을 저장하지 않는다. AI model ID 목록을 화면에 중복 선언하지 않는다. 409 mutation을 자동 재시도하거나 server snapshot을 낙관적으로 덮어쓰지 않으며 ARCHIVED mutation control을 노출하지 않는다.

## 관련 규칙 및 문서

- [Frontend 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [페이지 명세](../../../../docs/spec/page.md)
- [상위 feature 안내](../index.md)
- [진행 상황](progress.md)
