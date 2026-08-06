# 자기소개서 OpenAI 모델 선택 및 생성 품질 개선 보고서

## 1. 결론

Hiresemble의 현재 Spring AI `ChatGateway` 구조는 요청별 OpenAI model ID 지정과 Structured Outputs를 이미 지원하므로, 사용자가 자기소개서 생성·검증 요청마다 모델을 선택하게 하는 것이 가능하다. 안정적인 접목을 위해 품질 모드 값을 model ID로 단순 치환하지 않고 다음 경계를 적용했다.

- 서버 소유 allowlist를 단일 상수 카탈로그에서 관리한다.
- 클라이언트는 카탈로그 조회 API로 dropdown을 구성하고 exact model ID를 요청한다.
- 서버는 접수 시와 실행 시 model ID를 재검증한다.
- 선택 모델은 immutable Agent Run input snapshot과 input hash에 포함한다.
- 모든 chat step에는 같은 선택 모델을 사용하고 embedding step은 embedding policy로 분리한다.
- 신규 실행은 v4로 만들고 기존 v1~v3는 durable run 재생 전용으로 유지한다.
- 모델별 가격 item이 완전하지 않으면 local startup과 비용 정산이 fail-closed한다.

따라서 구조적으로 요청별 모델 선택은 가능하며 현재 서비스에도 안정적으로 접목할 수 있다. 다만 OpenAI 계정·프로젝트별 model entitlement는 서버 카탈로그와 별개이므로 실제 호출 시 Provider가 최종 권한을 판단한다.

## 2. 공식 문서 확인 결과

확인 기준일은 2026-08-06이다. OpenAI의 [최신 모델 선택 가이드](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-5.6), 각 model page와 [가격 문서](https://developers.openai.com/api/docs/pricing)를 기준으로 Chat Completions와 Structured Outputs 지원 여부를 확인했다.

| 정확한 API model ID | 제품군 | 자기소개서 선택 | 비고 |
| --- | --- | --- | --- |
| [`gpt-5.6-sol`](https://developers.openai.com/api/docs/models/gpt-5.6-sol) | 5.6 | 포함 | 최고 성능 지향 |
| [`gpt-5.6-terra`](https://developers.openai.com/api/docs/models/gpt-5.6-terra) | 5.6 | 포함·기본 추천 | 품질과 비용 균형 |
| [`gpt-5.6-luna`](https://developers.openai.com/api/docs/models/gpt-5.6-luna) | 5.6 | 포함 | 빠른 저비용 선택 |
| [`gpt-5.5`](https://developers.openai.com/api/docs/models/gpt-5.5) | 5.5 | 포함 | 고성능 이전 세대 |
| [`gpt-5.4`](https://developers.openai.com/api/docs/models/gpt-5.4) | 5.4 | 포함 | 범용 flagship |
| [`gpt-5.4-mini`](https://developers.openai.com/api/docs/models/gpt-5.4-mini) | 5.4 | 포함 | 저비용 범용 |
| [`gpt-5.4-nano`](https://developers.openai.com/api/docs/models/gpt-5.4-nano) | 5.4 | 포함 | 최저비용 compact |
| [`gpt-5.2`](https://developers.openai.com/api/docs/models/gpt-5.2) | 5.2 | 포함 | 이전 flagship |
| [`gpt-5.1`](https://developers.openai.com/api/docs/models/gpt-5.1) | 5.1 | 포함 | 이전 범용 |
| [`gpt-5`](https://developers.openai.com/api/docs/models/gpt-5) | 5 | 포함 | GPT-5 원형 모델 |

요청한 5.6~4.5 범위에서 호출 가능한 모델은 10개를 채택했다. `gpt-4.5-preview`는 OpenAI [deprecation 공지](https://developers.openai.com/api/docs/deprecations#2025-04-14-gpt-45-preview)에 따라 2025-07-14 API에서 제거됐으므로 선택지에 포함하지 않았다. 제거된 모델을 호환성 목적의 문자열로 남기는 것은 사용자가 선택한 뒤 반드시 실패하는 UI를 만들기 때문에 허용하지 않는다.

모델 카탈로그는 특정 계정의 `GET /v1/models` 응답을 그대로 사용자에게 노출하지 않는다. 그 응답에는 자기소개서 workflow와 호환되지 않거나 가격 item이 없는 모델도 포함될 수 있으므로, 제품이 검증한 server allowlist를 공개하는 편이 계약과 예산 측면에서 안전하다.

## 3. 현재 프로젝트 구조 적합성

현재 Adapter는 Spring AI 요청 옵션에 model을 요청별로 넣고, OpenAI 호출은 `store=false`, Provider retry 0, strict JSON Schema와 bounded timeout을 사용한다. Domain/Application 계층은 Spring AI concrete API를 직접 참조하지 않고 `ChatGateway` port를 통하므로 exact model routing 추가가 외부 Adapter 구현을 침범하지 않는다.

```text
모델 dropdown
→ POST generate/verify의 model
→ server allowlist 검증
→ Agent Run input snapshot + canonical hash
→ v4 Context/Workflow
→ ModelRouter exact route
→ Spring AI request별 model option
→ OpenAI Chat Completions
```

다음 안정성 조건도 충족한다.

- 같은 idempotency 요청의 model이 바뀌면 request identity와 hash가 달라진다.
- retry는 최초 run snapshot의 model을 재사용해 중간에 모델이 바뀌지 않는다.
- 과거 quality-mode run은 v1~v3 정의로 계속 실행할 수 있다.
- 선택 모델이 embedding route를 덮어쓰지 않는다.
- model ID가 allowlist에 없으면 Provider 호출 전에 `AI_MODEL_NOT_SUPPORTED`로 거절한다.
- exact product별 immutable price item으로 usage를 정산한다.

## 4. 초안 품질 저하 원인과 개선 판단

모델 성능은 품질 요소 중 하나일 뿐이다. 현재 낮은 초안 품질의 직접적인 구조 문제는 사용자가 문항에 기록한 `memo`가 생성 context에서 누락돼, 강조할 방향·제외할 내용·문체 지침을 모델이 알 수 없었다는 점이다. v4는 memo를 계획 단계와 최종 작성 단계 모두에 전달한다. memo는 사용자 지침으로 우선 반영하지만 검증된 근거가 아니므로, memo에 적힌 사실을 근거 없이 단정하지 않도록 prompt 경계를 추가했다.

현재 workflow는 이미 문항 분석 → 근거 검색 → 경험 배분 → 작성 → 사실 검증의 bounded multi-step 구조를 가진다. 이 장점은 유지하면서 다음 순서로 품질을 개선하는 것이 비용 대비 효과가 높다.

1. 구현 완료: memo-aware 계획·작성, exact model 선택, model·memo를 snapshot hash에 포함.
2. 단기 권장: 대표 자기소개서 fixture를 질문 유형·근거 풍부도·글자 수별로 만들고, 사실성·질문 적합성·구체성·문체·길이를 deterministic rubric으로 회귀 평가.
3. 단기 권장: 모델별 동일 fixture 비교 결과를 수집해 추천 모델을 감이 아니라 평가 점수와 비용으로 결정.
4. 중기 권장: 사용자가 선호하는 문체·금지 표현을 별도 bounded preference로 구조화하고 memo와 구분.
5. 중기 권장: 초안 결과에 대해 중복 문장, 추상 표현, 근거 없는 수치, 질문 미응답을 deterministic pre-apply validator로 강화.
6. 운영 권장: 모델별 성공률, structured correction률, 사실 검증 warning률, 사용자 수정량, 최종 채택률을 원문 없이 집계.

가장 중요한 평가지표는 “더 큰 모델을 선택했는가”가 아니라 사용자가 실제로 수정한 문자 비율, 검증 warning률, 질문별 최종 채택률이다. 이 지표 없이 기본 추천을 최고가 모델로 바꾸면 비용만 증가하고 품질 원인을 찾기 어렵다. 기본 추천을 `gpt-5.6-terra`로 둔 이유도 성능·비용 균형을 먼저 검증하기 위해서다.

## 5. 운영상 제한과 후속 검증

이번 변경은 실제 유료 OpenAI 호출 없이 공식 문서, 코드 계약, Fake 기반 회귀 테스트로 검증했다. 배포 전에는 권한이 있는 별도 검증 환경에서 각 allowlisted model에 대해 최소 1회의 bounded Structured Output smoke를 수행해야 한다. 실패한 모델은 사용자에게 계속 노출하지 말고 catalog 배포 단위에서 제거해야 한다.

OpenAI가 모델을 추가·폐기하거나 가격을 바꿀 때는 한 곳의 상수만 고치는 것으로 끝나지 않는다. model page의 endpoint·Structured Outputs 지원 확인, catalog 변경, 새 immutable price version/migration, contract test, dropdown 회귀, bounded live smoke를 하나의 변경 단위로 처리한다.

## 6. 관련 활성 계약

- [API 명세](../spec/api.md)
- [페이지 명세](../spec/page.md)
- [DB 명세](../spec/db.md)
- [기술 명세](../spec/tech_stack.md)
- [AI Provider 활성화](../operations/ai-provider-activation.md)
