# Job Domain package 안내

## 디렉터리 목적

P5 Job의 업무·추출 상태·URL 정책과 P6 Job Analysis enum·stable hash·결정론적 score 정책을 소유한다.

## 주요 파일 및 하위 디렉터리

- `JobStatus`, `JobExtractionStatus`: 분리된 상태 축
- `JobAutoAnalysisStatus`: durable 자동 분석 의도의 claim·terminal 상태
- `JobPolicy`: 허용 상태 전이와 timestamp 규칙
- `JobCommands`, `JobRecords`: typed domain 입력·projection
- `JobUrlCanonicalizer`: HTTP(S) canonical URL과 tracking query 제거
- `DeadlineSource`, `ClosedReason`, `JobDescriptionSource`, `JobHistoryActor`: provenance enum
- `Eligibility`, `FitCriterionCategory`, `MatchLevel`, `OutdatedReason`: canonical 분석 enum
- `JobAnalysisHashing`, `JobFitScoringPolicy`: stable tenant snapshot hash와 0~100 결정론적 점수
- [`progress.md`](progress.md): domain 구현 상태

## 구성 요소 역할

최초 `submittedAt`을 영구 보존하고 CLOSED 재오픈 시 현재 close metadata만 제거한다. 분석 점수는 Eligibility와 독립이며 category 재분배와 rounding을 서버 정책으로 고정한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)이 정책을 호출하고 infrastructure는 domain record로 row를 변환한다.

## 변경 시 주의사항

업무 상태와 추출 상태를 합치지 않는다. canonicalization은 reserved escape와 query `+`/`%20`의 실제 URL 차이를 보존한다.

## 관련 규칙 및 문서

- [상위 Job 영역](../index.md)
- [DB 명세](../../../../../../../../docs/spec/db.md)
- [진행 상황](progress.md)
