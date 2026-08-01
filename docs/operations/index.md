# 운영 문서 안내

## 디렉터리 목적

`docs/operations/`는 배포 비밀값을 포함하지 않는 실행·장애 대응 절차를 관리한다.

## 주요 파일 및 하위 디렉터리

- [`ai-provider-activation.md`](ai-provider-activation.md): local OpenAI/Tavily 활성화, offline 전환, 가격·검증 절차
- [`usage-metering-and-backoffice.md`](usage-metering-and-backoffice.md): P8.5-V와 P8.6~P8.9 사용량·집계·실패·Backoffice 운영 계획
- [`progress.md`](progress.md): 운영 문서 변경 이력

## 다른 디렉터리와의 의존 관계

제품 계약은 [`../spec/`](../spec/index.md), 구현 순서는 [`../design/`](../design/index.md), 실제 설정은 [`../../backend/src/main/resources/`](../../backend/src/main/resources/index.md)를 따른다.

## 변경 시 주의사항

실제 key·조직/프로젝트 ID·사용자 데이터·로컬 절대 경로를 기록하지 않는다. Provider readiness와 live 검증, 내부 원가와 고객 청구, USER와 ADMIN 조회 경계를 합치지 않는다.
