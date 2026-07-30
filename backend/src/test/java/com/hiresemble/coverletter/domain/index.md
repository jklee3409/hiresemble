# Cover Letter Domain 테스트 안내

## 디렉터리 목적

서버 authoritative TipTap allowlist, canonical plain text와 Unicode 글자 수를 단위 테스트한다.

## 주요 파일 및 하위 디렉터리

- `TipTapCanonicalizerTest`: 허용·거부 node/mark, 정규화와 code point count
- [`progress.md`](progress.md): domain 테스트 상태

## 구성 요소 역할

DB·브라우저 없이 content schema와 문자 계산 정책을 고정한다.

## 다른 디렉터리와의 의존 관계

운영 domain은 [`../../../../../../main/java/com/hiresemble/coverletter/domain/`](../../../../../../main/java/com/hiresemble/coverletter/domain/index.md)에 있다.

## 변경 시 주의사항

Frontend preview와 무관하게 서버 canonical 결과가 최종임을 회귀로 유지한다.

## 관련 규칙 및 문서

- [상위 Cover Letter 테스트](../index.md)
- [진행 상황](progress.md)
