# Progress

## Overview

조사 조회와 resource retry service가 구현되어 있다.

## [2026-07-31] Session Summary (조사 application service)

- What was done:
  - source pagination·allowlist와 새 lineage retry 접수를 구현했다.
- Key decisions:
  - 충돌하는 재요청 옵션은 `AGENT_RUN_RETRY_ALREADY_CREATED` 409로 반환한다.
- Issues encountered:
  - foreign research의 `HIGH_QUALITY` retry가 quality 오류로 존재 여부를 노출하지 않도록 owner 404를 먼저 판정하게 보정했다.
- Validation:
  - 제한 보정 후 resource/generic replay, 충돌, owner 404와 history delete 통합 테스트가 통과했다.
- Next steps:
  - None.
