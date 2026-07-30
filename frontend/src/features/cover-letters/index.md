# Cover Letter Frontend feature 안내

## 디렉터리 목적

P7 자기소개서 목록·편집 화면이 사용하는 URL filter, Vue Query, TipTap content, session draft, 충돌 비교와 Agent Run 진행 UI를 소유한다.

## 주요 파일 및 하위 디렉터리

- `queries.ts`: 자기소개서 query key·mutation과 terminal invalidation
- `filters.ts`: 목록 URL query parse·canonicalization
- `drafts.ts`: user/resource/question/base version별 24시간 sessionStorage draft
- `editorContent.ts`: 제한된 TipTap JSON과 글자 수 preview
- `conflict.ts`, `CoverLetterConflictPanel.vue`: 409 server snapshot·draft 비교/재적용
- `CoverLetterTipTapEditor.vue`: 접근 가능한 제한 node/mark editor
- `CoverLetterRunMonitor.vue`: generation·verification Run 진행과 결과 link
- [`progress.md`](progress.md): P7 feature 상태

## 구성 요소 역할

서버 상태는 Vue Query를 원천으로 사용하고 입력 중 본문만 sessionStorage에 둔다. 저장·복원·제안 적용은 사용자의 명시적 행동으로 분리한다.

## 다른 디렉터리와의 의존 관계

- API·DTO는 [`../../shared/api/`](../../shared/api/index.md)에 있다.
- 목록·공고 tab·canonical editor page는 [`../../pages/`](../../pages/index.md)에 있다.
- route는 [`../../router/`](../../router/index.md), logout·401 purge는 shared session 경계를 사용한다.

## 변경 시 주의사항

Pinia/localStorage에 본문을 저장하지 않는다. 409 mutation을 자동 재시도하거나 server snapshot을 낙관적으로 덮어쓰지 않으며 ARCHIVED mutation control을 노출하지 않는다.

## 관련 규칙 및 문서

- [Frontend 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [페이지 명세](../../../../docs/spec/page.md)
- [상위 feature 안내](../index.md)
- [진행 상황](progress.md)
