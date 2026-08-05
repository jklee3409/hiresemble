# Cover Letter Application Model package 안내

## 디렉터리 목적

자기소개서 use case가 API와 AI workflow에 전달하는 최소 immutable record를 소유한다.

## 주요 파일 및 하위 디렉터리

- `CoverLetterModels`: summary/detail/question/version/verification, generation·verification snapshot, owner-scoped sibling current answer summary와 apply command
- [`progress.md`](progress.md): application model 계약 상태

## 구성 요소 역할

JDBC row·entity 대신 owner-scoped projection과 immutable command만 계층 사이에 전달한다.

## 다른 디렉터리와의 의존 관계

상위 [`../`](../index.md) service가 생성하고 [`../../api/`](../../api/index.md)와 AI workflow가 소비한다.

## 변경 시 주의사항

storage key, checksum, unmasked 원문 전체, provider/model 실명과 전체 prompt를 포함하지 않는다.

## 관련 규칙 및 문서

- [상위 application package](../index.md)
- [진행 상황](progress.md)
