# GitHub ingestion workflow

`GITHUB_INGESTION`의 고정 10단계 실행, repository 단위 bounded fan-out, untrusted-content 경계와 실패 보상을 담당한다.

- `GitHubIngestionWorkflow.java`: discovery, same-run selection wait/resume, snapshot, extraction, canonical apply
- `GitHubIngestionFailureHandler.java`: 실패·취소·reconciliation 시 source 안정 상태 보상
- [`progress.md`](progress.md): 구현·검증 기록
