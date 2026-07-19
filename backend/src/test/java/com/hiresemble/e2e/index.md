# Backend 주도 실제 E2E 안내

## 디렉터리 목적

격리 PostgreSQL·Object Storage·Spring·Vue·Chromium을 한 수명주기로 실행하는 cross-stack 검증 harness를 관리한다.

## 주요 파일 및 하위 디렉터리

- `P4BrowserE2eTest`: 격리 인프라와 test-scope Fake AI를 시작하고 Frontend Playwright 4개 시나리오를 실행
- [`progress.md`](progress.md): 실제 E2E 이력

## 구성 요소 역할

Backend가 random port와 격리 MinIO·PostgreSQL을 제공하고 Frontend dev server와 Chromium을 별도 port에서 실행한다.

## 다른 디렉터리와의 의존 관계

- Browser 시나리오는 [`../../../../../../../frontend/e2e/documents.actual.spec.ts`](../../../../../../../frontend/e2e/documents.actual.spec.ts)에 있다.
- Gradle task는 [`../../../../../../build.gradle.kts`](../../../../../../build.gradle.kts)의 `p4BrowserE2eTest`다.

## 변경 시 주의사항

기존 개발 DB·bucket·process를 사용하거나 실제 API key·provider를 요구하지 않는다.

## 관련 규칙 및 문서

- [Backend 테스트 영역](../index.md)
- [E2E 진행 상황](progress.md)
