# Cover Letter Domain package 안내

## 디렉터리 목적

P7 canonical 상태·source·verification enum과 TipTap 허용 schema·plain text·Unicode 글자 수 정책을 소유한다.

## 주요 파일 및 하위 디렉터리

- `CoverLetterStatus`, `CoverLetterVersionSource`, `AnswerCreatedBy`
- `VerificationStatus`, `VerificationIssueCode`, `IssueSeverity`
- `CoverLetterEvidenceUsageType`
- `TipTapContent`, `TipTapCanonicalizer`
- [`progress.md`](progress.md): domain 구현 상태

## 구성 요소 역할

허용 node/mark만 canonical JSON으로 정규화하고 CRLF·NBSP·NFC, 문단·목록·hardBreak 경계를 plain text로 변환해 Unicode code point 글자 수를 계산한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)이 정책을 적용하고 infrastructure가 enum을 DB CHECK 값과 일치시킨다.

## 변경 시 주의사항

raw HTML·link·image·embed·script와 허용되지 않은 node/mark를 수용하지 않는다. 공개 enum과 DB·TypeScript 값을 함께 검증한다.

## 관련 규칙 및 문서

- [상위 Cover Letter 영역](../index.md)
- [DB 명세](../../../../../../../../docs/spec/db.md)
- [진행 상황](progress.md)
