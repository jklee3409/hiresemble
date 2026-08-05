# `docs/assets` 디렉터리 안내

## 디렉터리 목적

이 디렉터리는 루트 [`README.md`](../../README.md)의 서비스 소개에 사용하는 실제 화면 캡처를 관리한다. 문서 전용 정적 자산만 두며 애플리케이션 코드와 빌드 산출물은 포함하지 않는다.

## 주요 파일 및 하위 디렉터리

| 경로                                                    | 역할                                 |
| ------------------------------------------------------- | ------------------------------------ |
| `landing.png`                                           | anonymous 공개 Landing               |
| `guide.png`                                             | `/guide` 이용 가이드                 |
| `dashboard.png`                                         | `/dashboard` 지원 준비 현황          |
| `profile-basic.png`                                     | `/profile/basic` 내 지원 정보        |
| `documents.png`, `document-detail.png`                  | 이력서·자료 목록과 상세              |
| `job-new.png`, `jobs.png`, `job-overview.png`           | 공고 등록, 목록과 공고 정보 tab      |
| `job-analysis.png`, `job-analysis-breakdown.png`        | 공고 분석 요약과 요건 매칭·강점 구간 |
| `cover-letter-edit.png`                                 | 자기소개서 편집                      |
| `interview-question-set.png`, `interview-questions.png` | 면접 조사 결과와 예상 질문 구간      |
| `agent-runs.png`                                        | `/agent-runs` AI 작업 내역           |
| [`progress.md`](progress.md)                            | 캡처 자산의 변경 상태와 검증 이력    |

## 구성 요소 역할

- 모든 캡처는 Vite 개발 서버와 Chromium에서 실제 화면을 렌더링해 얻는다. 1440px 폭 desktop viewport와 device scale factor 2를 사용하고, 화면 성격에 따라 viewport 높이와 scroll 위치만 조정한다.
- 응답 데이터는 Backend 없이 `/api/v1/**`를 가로챈 결정론적 예시 fixture이며 실제 사용자 데이터와 운영 데이터를 사용하지 않는다.
- 캡처에 사용하는 회사명, 사용자 이름, URL은 모두 가상의 예시 값이다.

## 다른 디렉터리와의 의존 관계

- 캡처 대상 화면과 route는 [`../../frontend/src/`](../../frontend/src/)에 구현되어 있고 화면 계약은 [`../spec/page.md`](../spec/page.md)를 따른다.
- 같은 fixture 방식의 전후 비교 캡처는 [`../../frontend/e2e/ui-redesign.visual.spec.ts`](../../frontend/e2e/ui-redesign.visual.spec.ts)가 별도로 관리한다.

## 변경 시 주의사항

- 실제 사용자 데이터, 실제 회사·서비스 URL, 개인정보가 담긴 화면을 캡처하지 않는다.
- 화면 개편으로 표현 계약이 바뀌면 관련 캡처를 함께 갱신하고 README 설명과 어긋나지 않게 유지한다.
- 파일명은 route 기준의 kebab-case를 유지해 README 링크가 깨지지 않게 한다.
- 자동 생성 artifact(`playwright-report`, `test-results`, `output/playwright`)는 이 디렉터리로 옮기지 않는다.

## 관련 규칙 및 문서

- [문서 영역 안내](../index.md)
- [문서 추적 규칙](../agent-rules/documentation-tracking.md)
- [페이지 구조 명세](../spec/page.md)
