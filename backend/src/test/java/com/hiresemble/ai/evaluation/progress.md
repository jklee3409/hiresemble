# Progress

## Overview

자기소개서 v5와 직접 입력 기준선의 A/B 품질 평가 도구가 구현됐다. CI는 fake gateway로만 검증하고 실제 평가는 opt-in이다.

## [2026-09-24] Session Summary (자기소개서 품질 A/B 평가 도구 추가)

- What was done:
  - 가상 케이스 loader, 결정적 지표(`CoverLetterQualityRubric`), v5 실행·기준선·블라인드 심사 harness, JSON/Markdown 리포트, fake gateway 기반 `CoverLetterEvalHarnessTest`, 실제 호출용 `CoverLetterQualityEvaluationTest`를 추가했다.
- Key decisions:
  - 기준선은 같은 자료를 일반 채팅에 붙여 넣는 사용 방식을 흉내 낸 한 번의 호출이다. 심사 입력에는 출처를 숨기고 케이스마다 A/B 순서를 바꾼다.
  - 누적 비용 상한에 도달하면 이후 호출 없이 케이스를 실패로 기록하며, 실패를 성공으로 숨기지 않는다.
- Issues encountered:
  - LLM 심사의 같은 계열 모델 선호 편향 가능성이 있어 사람 교차 채점과 심사 모델 교체 실행을 권장한다.
- Validation:
  - `CoverLetterEvalHarnessTest` 3건 통과(5개 케이스 전체 흐름·블라인드 매핑·지표·리포트, 비용 상한, 지표 경계).
  - 환경 변수 없이 `coverLetterQualityEvaluation` 실행 시 태스크가 SKIPPED로 끝나 유료 호출이 없음을 확인했다. 실제 OpenAI 평가는 API key가 없어 실행하지 않았다(`IMPLEMENTED_NOT_LIVE_VERIFIED`).
  - Backend 전체 `check`(세션 Gradle mirror init script, 로컬 dockerd): 106 suites/718 tests 중 717 통과. 실패 1건 `S3ObjectStorageAdapterTest`는 Docker Hub의 `minio/minio` 이미지 pull 거부로 인한 초기화 오류이며 이번 변경과 무관해 미검증으로 남긴다.
- Next steps:
  - API key가 있는 환경에서 `coverLetterQualityEvaluation`을 실행해 v5와 기준선을 비교한다.

