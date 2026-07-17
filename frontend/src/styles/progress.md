# 전역 Style 영역 진행 상황

## 현재 구현 상태

- `main.css`에는 `@import 'tailwindcss';`만 존재한다.
- Vite의 Tailwind plugin과 `main.ts`의 global import가 연결되어 있다.
- PrimeVue Aura theme은 등록되어 있지만 제품 UI, 별도 design token, reset 또는 component style은 아직 없다.

## 완료된 작업

- Tailwind CSS 진입점을 만들고 Vue 애플리케이션 bootstrap에서 불러오도록 구성했다.
- PrimeVue Aura preset과 함께 사용할 수 있는 최소 전역 style 기반을 마련했다.
- 작업 목적에 따라 `index.md`와 이 문서를 생성해 전역 style의 제한된 책임과 현재 상태를 기록했다.

## 진행 중인 작업

- 현재 작성 중인 design token, reset 또는 공통 style은 없다.
- Style 영역의 초기 문서 추적 기반은 이번 작업에서 구성됐다.

## 남은 작업

- 실제 화면 구현 시 필요한 공통 color, spacing, typography token 정책 확정
- focus, contrast, reduced motion 등 접근성 기준 검증
- PrimeVue theme과 Tailwind utility 사이의 token·override 경계 정리
- 주요 layout과 상태 UI에 대한 반응형 style 검증

## 확인된 문제

- 제품 화면이 없어 Tailwind와 PrimeVue theme의 실제 조합, 반응형 동작, 접근성을 시각적으로 검증하지 못했다.
- 현재 공통 token 정책이 없으므로 향후 화면별 임의 값이 늘지 않도록 기준이 필요하다.

## 기술적 결정 사항

- 전역 CSS는 token, reset, 접근성 보조 등 애플리케이션 공통 책임으로 제한한다.
- page/component별 표현은 가까운 component와 Tailwind utility에서 관리한다.
- 실제 요구가 생기기 전 광범위한 theme override를 선행 구현하지 않는다.

## 테스트 및 검증 결과

- 기본 검증 명령: `Set-Location frontend; corepack pnpm check`
- 이 명령의 Prettier와 production build가 Markdown 형식과 CSS/Vite 처리 가능 여부를 함께 확인한다.
- 명령은 성공했고 Vite production build에서 CSS asset이 생성됐다. 실제 UI visual/accessibility 검증은 화면이 없어 실행하지 않았다.

## 마지막 수정 일자

2026-07-17
