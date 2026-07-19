# 프로필 Feature 안내

## 디렉터리 목적

P2 프로필 화면이 공유하는 Zod validation, 사용자별 Vue Query key, optimistic conflict 비교·재적용과 P4 증빙 문서 선택 규칙을 관리한다.

## 주요 파일 및 하위 디렉터리

- [`schemas.ts`](schemas.ts): 기본·구조화 프로필 날짜·GPA·배열 Zod schema
- [`queryKeys.ts`](queryKeys.ts): 사용자 ID가 포함된 profile query key factory
- [`conflict.ts`](conflict.ts): 미저장 값과 최신 snapshot의 field별 비교·재적용
- [`ProfileTabs.vue`](ProfileTabs.vue): 구현된 profile route navigation
- [`StringListInput.vue`](StringListInput.vue): 최대 10개 canonical 문자열 입력
- [`VersionConflictPanel.vue`](VersionConflictPanel.vue): 취소·최신값·field 재적용·다시 저장 UI
- `*.test.ts`: schema·query key·conflict 단위 테스트
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- 서버 상태는 page의 Vue Query가 소유하고 이 영역은 재사용 가능한 profile 규칙과 표현만 제공한다.
- query key는 모든 profile cache에 인증 사용자 ID를 포함해 사용자 전환 경계를 유지한다.

## 다른 디렉터리와의 의존 관계

- page 조정은 [`../../pages/`](../../pages/index.md), transport 계약은 [`../../shared/api/`](../../shared/api/index.md)에 의존한다.
- 화면 계약은 [`../../../../docs/spec/page.md`](../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- Backend owner·완료도 규칙을 UI에서 권한 판단으로 중복하지 않는다.
- 409에서 자동 overwrite·mutation 재시도를 하지 않고 사용자 입력을 유지한다.
- document 선택·filter는 같은 사용자 active Document query만 사용하고 삭제 뒤 cache에서 제거한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../AGENTS.md)
- [프론트엔드 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [API 명세](../../../../docs/spec/api.md)
- [영역 진행 상황](progress.md)
