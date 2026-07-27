# 전역 Style 영역 안내

## 디렉터리 목적

이 디렉터리는 Hiresemble 프론트엔드의 Tailwind 진입점과 애플리케이션 전체에 적용되는 제품 design token·base style·공용 UI pattern을 관리한다.

## 주요 파일 및 하위 디렉터리

| 경로                         | 역할                                                |
| ---------------------------- | --------------------------------------------------- |
| [`main.css`](main.css)       | Tailwind entry, token, base·form·action·state style |
| [`index.md`](index.md)       | 전역 style 책임과 변경 원칙 설명                    |
| [`progress.md`](progress.md) | Theme/token/reset 구현 상태와 검증 이력 추적        |

현재 추가 stylesheet나 하위 디렉터리는 없으며 단일 entry에서 전역 책임을 관리한다.

## 구성 요소 역할

- `main.css`는 Tailwind utility 생성과 함께 electric cobalt·warm off-white·deep ink 기반 canvas·surface·text·border·brand·semantic color, radius·shadow·content width·typography token을 선언한다.
- 최소 reset, focus ring, reduced motion, button·control·alert·status·skeleton·progress·section reveal·pagination처럼 여러 화면이 공유하는 pattern만 전역으로 제공한다.
- PrimeVue dependency는 유지하지만 현재 component 사용처가 없어 Aura theme을 전역 초기화하지 않는다.

## 다른 디렉터리와의 의존 관계

- [`../main.ts`](../main.ts)가 `main.css`를 최초 import한다.
- [`../../vite.config.ts`](../../vite.config.ts)의 Tailwind Vite plugin이 CSS 처리와 build를 담당한다.
- 실제 page와 feature 전용 배치는 scoped style에서 관리하고 공통 token·primitive class만 전역 style에 의존한다.
- UI 구조와 상태 표현은 [`../../../docs/spec/page.md`](../../../docs/spec/page.md)를 기준으로 한다.

## 변경 시 주의사항

- page나 feature 전용 배치를 전역 stylesheet에 추가하지 않는다.
- 광범위한 element selector와 높은 specificity override를 만들지 않는다.
- color, focus, motion 변경 시 contrast와 keyboard 접근성을 함께 확인한다.
- 색상·radius·shadow를 SFC마다 임의로 복제하지 않고 공통 token을 우선한다.

## 관련 규칙 및 문서

- [프론트엔드 소스 안내](../index.md)
- [프론트엔드 개발 규칙](../../../docs/agent-rules/frontend-development.md)
- [페이지 구조 명세](../../../docs/spec/page.md)
- [기술 스택 명세](../../../docs/spec/tech_stack.md)
- [Style 진행 상황](progress.md)
