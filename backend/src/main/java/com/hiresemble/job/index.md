# Job 영역 안내

## 디렉터리 목적

P5 채용 공고와 P6 immutable 공고 분석의 owner-scoped API, 생성·조회·상태·추출·분석·Scheduler 도메인, revision별 자동 분석 후속 의도와 영속성·안전한 HTML/공고 이미지 URL fetch 경계를 소유한다. HTML은 bounded raw bytes에서 header·BOM·meta 문자셋을 strict decode하고, 이미지는 동일 SSRF·DNS pinning·redirect·deadline 경계에서 JPEG·PNG·정적 WebP를 검증한다. terminal 공고 추출 retry는 현재 Job snapshot으로 canonical v3 successor를 만든다.

## 주요 파일 및 하위 디렉터리

- [`api/`](api/index.md): 공개 Job HTTP 계약과 DTO mapping
- [`application/`](application/index.md): 생성·조회·수정·상태·추출·분석 snapshot/command·자동 마감 use case
- [`domain/`](domain/index.md): 두 상태 축, URL canonicalization, analysis enum·hash·결정론적 점수
- [`infrastructure/`](infrastructure/index.md): Job/Analysis JDBC store, AI 비용 설정과 SSRF-safe fetch adapter
- [`progress.md`](progress.md): P5~P6 구현·검증 이력

## 구성 요소 역할

업무 상태와 추출 상태를 분리하고 사용자 소유권·낙관적 잠금·soft delete를 Job use case에 적용한다. usable 본문 revision은 V16 후속 의도를 원자적으로 남기고 lease reconciliation이 별도 `BALANCED` 분석 Run을 최대 한 번 생성한다. 분석은 현재 snapshot과 별개의 immutable version으로 보존하며 OUTDATED는 hash 비교 projection으로 계산한다. 공고 section·점수 category·support type을 분리하고 구조화 profile fact와 evidence provenance를 별도 link로 저장한다.

## 다른 디렉터리와의 의존 관계

- [`../agentrun/`](../agentrun/index.md)과 typed Job resource·Run 수명주기를 연결한다.
- [`../ai/`](../ai/index.md)은 Job application port를 통해 고정 추출·분석 workflow를 실행한다.
- [`../profile/`](../profile/index.md)과 [`../document/`](../document/index.md)의 owner-scoped profile/evidence·embedding query를 분석 snapshot과 RAG에 사용한다.
- [`../coverletter/`](../coverletter/index.md)은 최신 공고·분석·requirement와 `analysisOutdated` projection을 생성·검증 context에서 조회한다.
- 공개 계약은 [`../../../../../../../docs/spec/api.md`](../../../../../../../docs/spec/api.md)와 [`../../../../../../../docs/spec/db.md`](../../../../../../../docs/spec/db.md)를 따른다.

## 변경 시 주의사항

Cover Letter 상세에 필요한 최소 P7 projection만 공개하고 P8 면접 계약을 선행 추가하지 않는다. 외부 URL·AI 호출은 DB transaction 밖에서 수행하고 공고·문서 원문, 전체 prompt·provider response를 로그에 남기지 않는다.

## 관련 규칙 및 문서

- [Backend 개발 규칙](../../../../../../../docs/agent-rules/backend-development.md)
- [영역 진행 상황](progress.md)
