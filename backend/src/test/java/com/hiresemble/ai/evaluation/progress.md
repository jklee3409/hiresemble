# Progress

## Overview

자기소개서 v5와 직접 입력 기준선의 A/B 품질 평가 도구가 구현됐다. CI는 fake gateway로만 검증하고 실제 평가는 opt-in이다.

## [2026-09-24] Session Summary (품질 평가 심사 v2 엄격화)

- What was done:
  - 심사를 `cover-letter-eval-v2`로 올려 간결성 기준을 추가(7개 기준)하고, 1~5점 기준을 고정하며 weakestPoint에 적은 기준은 5점을 금지했다.
  - 케이스마다 A/B 순서를 바꿔 두 번 심사하고 두 번 모두 같은 쪽을 고를 때만 승리로 센다. 점수는 두 번의 평균이며 확신 정도(SLIGHT/CLEAR/STRONG)를 함께 기록한다.
  - 문항 memo를 `applicantDirection`으로 심사에 전달하고, 심사 호출을 reasoning `high`/180초로 고정했다. 기본 심사 모델은 작성 모델과 다르게(Sol↔Terra) 정하고 `COVER_LETTER_EVAL_REPEATS`로 반복 실행을 지원한다.
  - 리포트에 순서 일관성, 5점 비율(포화도), 기준별 점수 차와 경고(포화 >0.5, 일관성 <0.7, 같은 심사 모델, 10건 미만, 실패)를 추가했다.
- Key decisions:
  - 첫 실제 실행(Terra 작성·Terra 심사, 5건, 0.38달러)은 Hiresemble 3승/기준선 2승, 평균 4.86 대 4.87이었으나 점수 대부분이 5점이었고, 직무역량 케이스는 심사 모델이 memo를 모른 채 memo를 따른 답변을 감점했다. 이 두 문제와 순서·자기 모델 편향을 줄이는 것을 이번 변경의 기준으로 삼았다.
- Issues encountered:
  - 이중 심사로 케이스당 심사 비용이 두 배가 된다.
- Validation:
  - `CoverLetterEvalHarnessTest` 5건 통과(순서 교차 이중 심사와 일관 판정, 순서 편향·포화 심사의 TIE 처리와 경고, 기본 심사 모델, 비용 상한, 지표 경계).
  - Backend 전체 `check`: 106 suites/720 tests 중 719 통과. 실패 1건 `S3ObjectStorageAdapterTest`는 Docker Hub의 `minio/minio` 이미지 pull 거부로 인한 환경 문제이며 이번 변경과 무관하다.
  - 실제 provider 재평가는 사용자 환경에서 실행해야 한다.
- Next steps:
  - API key가 있는 환경에서 v2 평가를 다시 실행하고 경고가 없는 결과로 v5 품질을 판단한다.

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

