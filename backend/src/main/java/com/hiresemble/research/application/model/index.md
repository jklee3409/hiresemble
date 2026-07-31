# Research application model 안내

## 디렉터리 목적

조사 run, source, page와 retry 결과의 내부 typed model을 정의한다.

## 주요 파일 및 하위 디렉터리

- `ResearchModels`: 조사 row·source·page·retry record
- [`progress.md`](progress.md): model 상태

## 구성 요소 역할

HTTP DTO와 persistence row 사이에서 nullability·enum·immutable collection 경계를 제공한다.

## 다른 디렉터리와의 의존 관계

[`../../domain/`](../../domain/index.md)의 canonical enum을 사용한다.

## 변경 시 주의사항

검색 원문·provider response·민감한 내부 값을 model에 추가하지 않는다.

## 관련 규칙 및 문서

- [상위 application package](../index.md)
- [진행 상황](progress.md)
