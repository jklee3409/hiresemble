# Job Infrastructure package 안내

## 디렉터리 목적

P5 Job JDBC store·Scheduler/fetch 설정과 P6 immutable Analysis store·비용 설정, SSRF-safe HTTP(S) page/image fetch adapter를 소유한다.

## 주요 파일 및 하위 디렉터리

- `JobStore`: owner-scoped CRUD·등록 연도·상하반기 저장과 실제 보유 기간 목록·history·conditional close SQL
- `JobAnalysisStore`: owner-scoped immutable analysis·criteria·provenance·secondary Run link SQL
- `JobAutoAnalysisStore`: revision unique enqueue, `SKIP LOCKED` lease claim과 결정적 Run 연결 SQL
- `SecureJobPageFetchAdapter`: DNS 검증 주소 고정, redirect 재검사, bounded HTML/JPEG/PNG/WebP fetch와 magic·decode·pixel 검증
- `HtmlCharsetDecoder`: header→BOM→meta→strict UTF-8→제한적 MS949 fallback과 Korean alias 정규화
- `JobPageFetchProperties`, `JobDeadlineSchedulerProperties`, `JobAiCostProperties`, `JobAnalysisAiCostProperties`, `JobAutoAnalysisProperties`: 검증된 설정
- `JobInfrastructureConfiguration`: Clock과 Job infrastructure bean 조립
- [`progress.md`](progress.md): infrastructure 구현 상태

## 구성 요소 역할

검증된 `InetAddress`로 실제 socket을 연결하고 HTTPS 원 hostname의 SNI·인증서 검증을 유지한다. 헤더·body·압축 해제 전체에 절대 deadline과 post-decompression byte 제한을 적용하며, 공고 이미지 후보들은 step 전체의 단일 deadline remaining budget을 공유한다.

## 다른 디렉터리와의 의존 관계

[`../application/`](../application/index.md)의 store/fetch 사용처를 제공하고 Job 기본 V6~V7 및 기간 분류 V21 schema에 의존한다.

## 변경 시 주의사항

모든 SQL에 `user_id`와 active 조건을 적용한다. DNS 검증 뒤 hostname을 다시 해석하는 transport를 사용하지 않는다.

## 관련 규칙 및 문서

- [상위 Job 영역](../index.md)
- [Infrastructure 규칙](../../../../../../../../docs/agent-rules/infrastructure.md)
- [진행 상황](progress.md)
