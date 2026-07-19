# Frontend API Client 안내

## 디렉터리 목적

Backend P1 OpenAPI와 일치하는 TypeScript DTO, Axios·CSRF와 typed 오류 처리를 소유한다.

## 주요 파일 및 하위 디렉터리

- [`contracts.ts`](contracts.ts): P1 request·success·error type
- [`http.ts`](http.ts): baseURL·cookie·CSRF interceptor
- [`authApi.ts`](authApi.ts): 다섯 인증 endpoint consumer
- [`errors.ts`](errors.ts): typed error·field mapping
- [`http.test.ts`](http.test.ts): cookie·CSRF·401·409 transport test
- [`progress.md`](progress.md): 이 영역의 구현·검증 이력

## 구성 요소 역할

- HTTP transport를 화면에서 분리하고 Backend 오류 code를 parsing 없이 사용자 동작으로 변환한다.

## 다른 디렉터리와의 의존 관계

- 상위 [`shared/`](../index.md)의 책임 경계 안에서 동작한다.
- 공개 HTTP·화면 계약은 [`docs/spec/api.md`](../../../../docs/spec/api.md)와 [`docs/spec/page.md`](../../../../docs/spec/page.md)를 따른다.

## 변경 시 주의사항

- 성공 envelope를 가정하거나 P1 밖 endpoint function을 추가하지 않는다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../AGENTS.md)
- [공통 작업 절차](../../../../docs/agent-rules/workflow.md)
- [문서 추적 규칙](../../../../docs/agent-rules/documentation-tracking.md)
- [프론트엔드 개발 규칙](../../../../docs/agent-rules/frontend-development.md)
- [영역 진행 상황](progress.md)
