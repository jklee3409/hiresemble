# Research API package 안내

## 디렉터리 목적

조사 run 상세·source 목록·retry의 공개 HTTP DTO와 mapping을 소유한다.

## 주요 파일 및 하위 디렉터리

- `ResearchController`: 조사 조회·source filter·retry endpoint
- `ResearchDtos`: run·source·retry 공개 projection
- `ResearchApiMapper`: application model 변환
- [`progress.md`](progress.md): API 상태

## 구성 요소 역할

Controller는 인증 사용자, allowlist query와 idempotency header를 application service에 전달한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)의 use case만 호출한다.

## 변경 시 주의사항

provider rank와 내부 오류·모델 정보를 공개하지 않고 타 사용자 resource는 404로 숨긴다.

## 관련 규칙 및 문서

- [상위 Research 영역](../index.md)
- [API 명세](../../../../../../../../docs/spec/api.md)
- [진행 상황](progress.md)
