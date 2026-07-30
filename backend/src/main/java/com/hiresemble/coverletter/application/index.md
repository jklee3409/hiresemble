# Cover Letter Application package 안내

## 디렉터리 목적

P7 자기소개서 use case, transaction, owner scope, optimistic version과 AI workflow query/command 경계를 조정한다.

## 주요 파일 및 하위 디렉터리

- `CoverLetterApplicationService`: 생성·조회·문항·version·검증·수명주기와 Agent Run 접수
- [`model/`](model/index.md): 공개 계층과 AI workflow용 immutable model
- [`port/`](port/index.md): generation·verification query/command와 근거 검색 경계
- [`progress.md`](progress.md): application 상태

## 구성 요소 역할

상태 전이, current answer 교체, 검증 freshness, warning acknowledgement와 finalization eligibility를 transaction 안에서 재검증한다.

## 다른 디렉터리와의 의존 관계

[`../domain/`](../domain/index.md)의 정책과 [`../infrastructure/`](../infrastructure/index.md)의 store를 사용하며 [`../../ai/`](../../ai/index.md)에 typed port만 공개한다.

## 변경 시 주의사항

provider 호출을 transaction 안에서 실행하지 않는다. AI가 source·createdBy·finalization을 결정하게 하지 않고 archive read-only와 owner scope를 우회하지 않는다.

## 관련 규칙 및 문서

- [상위 Cover Letter 영역](../index.md)
- [진행 상황](progress.md)
