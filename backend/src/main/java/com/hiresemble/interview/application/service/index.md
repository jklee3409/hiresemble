# Interview application service 안내

## 디렉터리 목적

면접 준비 prerequisite, 조회, answer CAS, feedback 접수와 retry contribution을 구현한다.

## 주요 파일 및 하위 디렉터리

- `InterviewApplicationService`: P8 공개·workflow use case
- `InterviewPreparationRetryContributor`: generic retry의 P8 lineage 생성
- [`progress.md`](progress.md): service 상태

## 구성 요소 역할

같은 사용자·공고의 최신 분석과 active 자기소개서/current 답변을 검증하고 typed resource를 연결한다.

## 다른 디렉터리와의 의존 관계

[`../../infrastructure/`](../../infrastructure/index.md), [`../../../job/`](../../../job/index.md), [`../../../coverletter/`](../../../coverletter/index.md)을 사용한다.

## 변경 시 주의사항

profile 미완료를 hard gate로 추가하지 않고 answer current 교체를 한 transaction에서 처리한다.

## 관련 규칙 및 문서

- [상위 application package](../index.md)
- [진행 상황](progress.md)
