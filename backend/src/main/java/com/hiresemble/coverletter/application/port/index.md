# Cover Letter Application Port package 안내

## 디렉터리 목적

자기소개서 application과 AI workflow·근거 검색 사이의 최소 typed query/command 경계를 소유한다.

## 주요 파일 및 하위 디렉터리

- `CoverLetterQueryPort`: generation·verification owner/version snapshot
- `CoverLetterCommandPort`: generated answer·verification의 원자적 apply와 실패 보상
- `CoverLetterEvidenceSearchPort`: owner-scoped 현재 근거·masked candidate 검색
- [`progress.md`](progress.md): port 계약 상태

## 구성 요소 역할

AI package가 JDBC repository나 Controller에 접근하지 않고 immutable snapshot과 command만 소비하도록 한다.

## 다른 디렉터리와의 의존 관계

[`../../../ai/`](../../../ai/index.md)이 workflow에서 소비하고 Cover Letter application service 및 Document adapter가 구현한다.

## 변경 시 주의사항

PENDING·REJECTED·SOURCE_DELETED 근거를 새 긍정 근거로 전달하지 않으며 JPA/JDBC row와 전체 원문을 공개하지 않는다.

## 관련 규칙 및 문서

- [상위 application package](../index.md)
- [진행 상황](progress.md)
