# Progress

## Overview

조사 조회와 resource retry service가 구현되어 있다.

## [2026-09-24] Session Summary (자기소개서용 회사 조사 adapter)

- What was done:
  - `ResearchCoverLetterContextAdapter`가 자기소개서의 최신 성공 조사 요약과 OFFICIAL·TECH_BLOG·NEWS 출처 최대 8개를 제공한다.
- Key decisions:
  - 면접 후기·커뮤니티 출처는 회사 사실이 아니므로 작성 context에서 제외했다.
- Issues encountered:
  - None
- Validation:
  - Backend 전체 `check`(세션 Gradle mirror init script, 로컬 dockerd): 104 suites/713 tests 중 712 통과. 실패 1건 `S3ObjectStorageAdapterTest`는 Docker Hub의 `minio/minio` 이미지 pull 거부로 인한 초기화 오류이며 이번 변경과 무관해 미검증으로 남긴다.
- Next steps:
  - None

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
