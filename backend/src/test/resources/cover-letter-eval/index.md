# 자기소개서 품질 평가 케이스 안내

## 디렉터리 목적

자기소개서 생성 품질을 A/B로 비교하는 가상 평가 케이스를 보관한다.

## 주요 파일 및 하위 디렉터리

- `cases.json`: 문항 유형·글자 수·근거 양·회사 조사 유무를 섞은 가상 지원자/가상 회사 케이스(`cover-letter-eval-cases-v1`)
- [`progress.md`](progress.md): 케이스 변경 이력

## 구성 요소 역할

각 케이스는 공고, 요건, 문항, 제한, memo, 공고 분석 강점·보완점, 회사 조사, 승인 근거 요약과 원문 발췌를 가진다. 같은 자료로 워크플로와 기준선을 모두 실행한다.

## 다른 디렉터리와의 의존 관계

[`../../java/com/hiresemble/ai/evaluation/`](../../java/com/hiresemble/ai/evaluation/index.md)가 읽는다. 실행 절차는 [운영 문서](../../../../../docs/operations/cover-letter-quality-evaluation.md)에 있다.

## 변경 시 주의사항

실제 인물·기업·지원자 자료와 개인정보를 넣지 않는다. 케이스를 바꾸면 이전 리포트와 점수를 직접 비교하지 않는다.

## 관련 규칙 및 문서

- [상위 test resources 안내](../index.md)
