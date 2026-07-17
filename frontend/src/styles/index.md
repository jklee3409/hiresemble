# 전역 Style 영역 안내

## 디렉터리 목적

이 디렉터리는 Hiresemble 프론트엔드의 Tailwind 진입점과 애플리케이션 전체에 적용되는 제한된 전역 style을 관리한다. 현재는 Tailwind import만 존재한다.

## 주요 파일 및 하위 디렉터리

| 경로                         | 역할                                         |
| ---------------------------- | -------------------------------------------- |
| [`main.css`](main.css)       | Tailwind CSS를 불러오는 전역 style entry     |
| [`index.md`](index.md)       | 전역 style 책임과 변경 원칙 설명             |
| [`progress.md`](progress.md) | Theme/token/reset 구현 상태와 검증 이력 추적 |

현재 추가 stylesheet나 하위 디렉터리는 없다.

## 구성 요소 역할

- `main.css`는 앱 전체에서 사용할 Tailwind utility 생성을 활성화한다.
- 전역 style은 향후 공통 design token, 최소 reset, 접근성 보조처럼 전역이어야 하는 책임만 가진다.
- PrimeVue component theme은 이 디렉터리가 아니라 `main.ts`의 Aura preset 설정과 함께 동작한다.

## 다른 디렉터리와의 의존 관계

- [`../main.ts`](../main.ts)가 `main.css`를 최초 import한다.
- [`../../vite.config.ts`](../../vite.config.ts)의 Tailwind Vite plugin이 CSS 처리와 build를 담당한다.
- 실제 page/component는 Tailwind utility와 PrimeVue theme을 사용하며 전역 selector에 의존하지 않도록 한다.
- UI 구조와 상태 표현은 [`../../../docs/spec/page.md`](../../../docs/spec/page.md)를 기준으로 한다.

## 변경 시 주의사항

- page나 feature 전용 style을 전역 stylesheet에 추가하지 않는다.
- 광범위한 element selector와 높은 specificity로 PrimeVue 또는 component style을 덮어쓰지 않는다.
- color, focus, motion 변경 시 contrast와 keyboard 접근성을 함께 확인한다.
- Tailwind와 PrimeVue theme의 책임이 중복되지 않도록 token 출처를 명확히 한다.
- UI가 아직 없으므로 style 기반이 완성되었다고 기록하지 않는다.

## 관련 규칙 및 문서

- [프론트엔드 소스 안내](../index.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [페이지 구조 명세](../../../docs/spec/page.md)
- [기술 스택 명세](../../../docs/spec/tech_stack.md)
- [Style 진행 상황](progress.md)
