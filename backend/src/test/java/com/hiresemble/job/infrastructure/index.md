# Job Infrastructure 테스트 안내

## 디렉터리 목적

Job page fetch의 SSRF 방어, redirect, timeout·size·content type과 페이지 분류를 네트워크 없이 검증한다.

## 주요 파일 및 하위 디렉터리

- `SecureJobPageFetchAdapterTest`: Fake DNS/transport/socket 기반 fetch 보안·분류 테스트
- [`progress.md`](progress.md): infrastructure 테스트 상태

## 구성 요소 역할

검증 주소가 실제 connector에 전달되는지와 HTTPS SNI·endpoint identification을 관찰한다.

## 다른 디렉터리와의 의존 관계

운영 adapter는 [`../../../../../../main/java/com/hiresemble/job/infrastructure/`](../../../../../../main/java/com/hiresemble/job/infrastructure/index.md)에 있다.

## 변경 시 주의사항

실제 외부 웹사이트를 호출하지 않고 slow/stalled body는 제어 가능한 stream으로 재현한다.

## 관련 규칙 및 문서

- [상위 Job 테스트](../index.md)
- [진행 상황](progress.md)
