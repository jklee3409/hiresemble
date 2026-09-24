# 자기소개서 품질 A/B 평가

## 목적

같은 모델을 두 방식으로 쓴 답변을 비교한다. 한쪽은 Hiresemble 자기소개서 생성 v5 워크플로의 결과이고, 다른 쪽은 같은 자료를 일반 채팅 모델에 한 번에 붙여 넣은 기준선(baseline) 결과다. 모델 교체나 prompt 변경이 실제로 채용 담당자 관점의 품질을 올렸는지 감이 아니라 점수와 비용으로 판단하는 데 쓴다.

## 구성

- 평가 케이스: [`cases.json`](../../backend/src/test/resources/cover-letter-eval/cases.json)의 가상 지원자·가상 회사 5건(지원동기·직무역량·기술 판단·협업·성장, 500~1000자, 근거 양과 회사 조사 유무를 섞음)
- 실행 도구: [`backend/src/test/java/com/hiresemble/ai/evaluation/`](../../backend/src/test/java/com/hiresemble/ai/evaluation/index.md)
- 결정적 지표: 글자 수와 제한 대비 채움 비율, 제한 초과, 문단 수, Markdown, 상투 표현, 근거에 없는 수치, 1인칭 서술 수
- LLM 심사(`cover-letter-eval-v2`): 채용 담당자 역할의 심사 모델이 출처를 숨긴 두 답변을 A/B로 보고 질문 적합성·구체성·개인 기여·직무/회사 적합성·신뢰성·간결성·가독성 7개 기준을 1~5점으로 매긴다.
  - 점수 기준을 고정한다: 3은 무난하지만 개선 여지가 분명함, 4는 사소한 수정만 남음, 5는 더 고칠 것이 없는 드문 경우이며 weakestPoint에 적은 기준은 5점을 줄 수 없다.
  - 한 케이스를 A/B 순서를 바꿔 두 번 심사한다. 두 번 모두 같은 쪽을 고를 때만 승리로 세고, 판정이 엇갈리거나 무승부면 TIE로 기록한다. 점수는 두 번의 평균이다.
  - 문항 memo를 `applicantDirection`으로 함께 보내, 지원자 지시를 따른 답변이 그 지시 때문에 감점되지 않게 한다.
  - 심사는 항상 reasoning `high`로 호출한다.

## 실행

실제 유료 API를 호출하므로 명시적으로 켤 때만 실행된다. `test`, `check`, CI, E2E에는 포함되지 않는다.

```powershell
$env:COVER_LETTER_EVAL_ENABLED = "true"
$env:AI_PROVIDER_API_KEY = "<secret>"
$env:COVER_LETTER_EVAL_MODEL = "gpt-5.6-terra"        # 선택, 기본 추천 모델
$env:COVER_LETTER_EVAL_JUDGE_MODEL = "gpt-5.6-sol"    # 선택, 기본은 평가 모델과 다른 모델(Sol이면 Terra, 그 외 Sol)
$env:COVER_LETTER_EVAL_REPEATS = "2"                  # 선택, 케이스별 반복 횟수(1~5, 기본 1)
$env:COVER_LETTER_EVAL_MAX_COST_USD = "2.000000"      # 선택, 누적 비용 상한
$env:COVER_LETTER_EVAL_CASES = "motivation-fintech-research"  # 선택, 쉼표로 일부 케이스만
Set-Location backend
.\gradlew.bat coverLetterQualityEvaluation
```

- 가격은 `COVER_LETTER_EVAL_PRICE_VERSION`(기본 `2026080601`)의 immutable 가격표로 계산한다.
- 누적 비용이 상한에 도달하면 이후 호출을 하지 않고 해당 케이스를 실패로 기록한다. 이미 진행 중인 한 번의 호출만큼은 상한을 넘을 수 있다.
- 검색 provider는 끄고 OpenAI chat만 사용한다. PostgreSQL은 Testcontainers로 띄운다.

## 결과

- `backend/build/reports/cover-letter-eval/report.md`: 순서 일관 승/패/무, 순서 일관성 비율, 점수 포화도(5점 비율), 평균 점수, 기준별 점수 차, 평균 채움 비율, 근거 없는 수치 합계, 경고, 케이스별 표
- `backend/build/reports/cover-letter-eval/report.json`: 두 답변 원문, 두 번의 심사 결과(선호·확신 정도·근거)와 기준별 점수, 결정적 지표. 사람이 다시 채점할 때 이 파일을 쓴다.
- 리포트는 5점 비율이 절반을 넘거나, 순서 일관성이 0.7 미만이거나, 심사 모델이 작성 모델과 같거나, 완료 결과가 10건 미만이면 경고를 표시한다. 경고가 있으면 승패 수를 결론으로 쓰지 않는다.

평가 데이터는 모두 가상이지만, 리포트를 공유할 때 실제 지원자 자료를 케이스에 넣지 않았는지 확인한다.

## 해석 주의

- LLM 심사는 같은 계열 모델의 문체를 선호하는 편향이 있을 수 있다. 기본값으로 작성 모델과 다른 심사 모델을 쓰고, 필요하면 심사 모델을 바꿔 다시 실행하거나 `report.json`으로 사람이 교차 채점한다.
- 5건은 회귀 신호용이다. 의사결정 전에는 케이스를 늘리고 같은 설정으로 여러 번 실행해 분산을 본다.
