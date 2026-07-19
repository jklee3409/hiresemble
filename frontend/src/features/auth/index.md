# 인증 Form 규칙 안내

## 디렉터리 목적

Signup·login의 client 입력 schema와 Backend UTF-8 byte 계약을 관리한다.

## 주요 파일 및 하위 디렉터리

- [`formValidation.ts`](formValidation.ts): Zod form schema와 TextEncoder byte 검사
- [`formValidation.test.ts`](formValidation.test.ts): credential 경계와 consent·confirm 테스트
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- Form의 빠른 client feedback을 제공하되 최종 validation은 Backend 계약을 따른다.

## 다른 디렉터리와의 의존 관계

- 상위 [`features/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 비밀번호 확인은 UI 전용이며 SignupRequest에 전송하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../AGENTS.md)
- [공통 작업 절차](../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
