# Interview application model 안내

## 디렉터리 목적

준비, question set·question, 답변·feedback과 AI structured result의 내부 typed model을 정의한다.

## 주요 파일 및 하위 디렉터리

- `InterviewModels`: row·view·context·generated result·feedback record
- [`progress.md`](progress.md): model 상태

## 구성 요소 역할

HTTP DTO, workflow output과 persistence 사이 nullability·collection 불변 경계를 제공한다.

## 다른 디렉터리와의 의존 관계

[`../../domain/`](../../domain/index.md)과 [`../../../research/domain/`](../../../research/domain/index.md)의 canonical enum을 사용한다.

## 변경 시 주의사항

공개 상한과 structured output 상한을 일치시키고 prompt·provider 원문을 record에 보존하지 않는다.

## 관련 규칙 및 문서

- [상위 application package](../index.md)
- [진행 상황](progress.md)
