# Document infrastructure 테스트 안내

## 디렉터리 목적

P4 parser 방어, embedding policy boot 검증과 실제 S3-compatible storage adapter를 검증한다.

## 주요 파일 및 하위 디렉터리

- `DocumentParserTest`: PDF·DOCX·TXT 및 위장·macro·암호화·손상·timeout·20 MiB
- `DocumentEmbeddingPolicyValidatorTest`: 1536 dimension fail-fast
- `S3ObjectStorageAdapterTest`: 실제 MinIO upload·metadata·presign·delete
- [`progress.md`](progress.md): infrastructure 테스트 이력

## 구성 요소 역할

parser와 Object Storage를 in-memory mock만으로 완료 처리하지 않도록 실제 library·container 경계를 실행한다.

## 다른 디렉터리와의 의존 관계

[`../../../../../../main/java/com/hiresemble/document/infrastructure/`](../../../../../../main/java/com/hiresemble/document/infrastructure/index.md)을 검증한다.

## 변경 시 주의사항

Testcontainer는 격리 bucket만 사용하고 종료 시 자동 정리한다.

## 관련 규칙 및 문서

- [Document 테스트](../index.md)
- [진행 상황](progress.md)
