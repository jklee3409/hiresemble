# Document 테스트 Configuration package 안내

## 디렉터리 목적

`com.hiresemble.document.infrastructure.config` package는 infrastructure configuration의 package-private 협력과 Object Storage 회귀 검증을 소유한다.

## 주요 파일 및 하위 디렉터리

| 파일 | 역할 |
| ---- | ---- |
| [S3ObjectStorageAdapterTest.java](S3ObjectStorageAdapterTest.java) | S3-compatible adapter configuration 검증 |
| [progress.md](progress.md) | 이 package의 이동·검증 이력 |

## 구성 요소 역할

- 운영 configuration의 package-private Bean factory를 접근 제한자 변경 없이 직접 검증한다.
- 운영 소스와 공개 계약을 변경하지 않고 대응 구현의 회귀를 검증한다.

## 다른 디렉터리와의 의존 관계

- [상위 package](../index.md)의 infrastructure test 경계를 따른다.
- 운영 [`config`](../../../../../../../main/java/com/hiresemble/document/infrastructure/config/index.md) package를 검증한다.

## 변경 시 주의사항

- 테스트 편의를 위해 운영 접근 제한자를 넓히지 않는다.
- package 이동 시 경로, package 선언과 import를 함께 검증한다.
- API·DB·workflow 동작 변경은 별도 계약 작업으로 분리한다.

## 관련 규칙 및 문서

- [최상위 작업 지침](../../../../../../../../../AGENTS.md)
- [백엔드 개발 규칙](../../../../../../../../../docs/agent-rules/backend-development.md)
- [문서 추적 규칙](../../../../../../../../../docs/agent-rules/documentation-tracking.md)
- [상위 package 안내](../index.md)
- [진행 상황](progress.md)
