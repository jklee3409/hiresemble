# AI Provider 활성화

## 실행 모드

일반 개발은 `SPRING_PROFILES_ACTIVE=local`을 사용한다. 이 모드는 OpenAI Chat·Embedding과 Tavily Search를 실제 제품 workflow에 연결한다. key, provider/model 일치, immutable 가격 item, HTTPS endpoint, retry/store/vector-store 정책 중 하나라도 잘못되면 startup이 실패하며 disabled/Fake로 자동 fallback하지 않는다.

네트워크 없이 Backend만 실행하려면 `SPRING_PROFILES_ACTIVE=local-offline`을 명시한다. `test`, `ci`, `e2e`와 P4~P8 actual task는 개발 PC에 key가 있어도 network-disabled system property를 강제한다.

## Secret과 설정

Secret은 환경변수 또는 배포 Secret으로만 주입한다.

```text
SPRING_PROFILES_ACTIVE=local
AI_PROVIDER=openai
AI_CHAT_MODEL_PROVIDER=openai
AI_EMBEDDING_MODEL_PROVIDER=openai
AI_PROVIDER_API_KEY=<secret>
AI_PROVIDER_BASE_URL=https://api.openai.com/v1
AI_PROVIDER_TIMEOUT=60s
AI_PROVIDER_MAX_RETRIES=0
AI_PROVIDER_STORE=false
AI_EMBEDDING_MODEL=text-embedding-3-small
AI_EMBEDDING_DIMENSION=1536
AI_VECTOR_STORE_PROVIDER=none
WEB_SEARCH_PROVIDER=tavily
TAVILY_API_KEY=<secret>
TAVILY_ENDPOINT=https://api.tavily.com/search
ALLOW_INSECURE_PROVIDER_ENDPOINT=false
```

key 값·suffix, prompt/input/raw response, 사용자 문서·답변, embedding vector는 로그·health·test report에 남기지 않는다. Spring AI Chat model 로그는 prompt를 출력할 수 있는 warning 경계를 차단하고 response storage를 끈다.

## 가격과 예산

활성 catalog version은 `2026080601`이다. 2026-08-06 공개 PAYG 기준을 사용한다.

- OpenAI [`gpt-5-mini`](https://developers.openai.com/api/docs/models/gpt-5-mini): input/cached input/output을 1M token 단위의 별도 item으로 둔다.
- 자기소개서 선택 모델 `gpt-5.6-sol`, `gpt-5.6-terra`, `gpt-5.6-luna`, `gpt-5.5`, `gpt-5.4`, `gpt-5.4-mini`, `gpt-5.4-nano`, `gpt-5.2`, `gpt-5.1`, `gpt-5`: 각각 input/cached input/output을 1M token 단위의 별도 item으로 둔다. 정확한 ID와 지원 근거는 [모델 선택 보고서](../design/cover-letter-openai-model-selection-report.md)를 따른다.
- OpenAI [`text-embedding-3-small`](https://developers.openai.com/api/docs/models/text-embedding-3-small): input을 1M token 단위로 둔다.
- Tavily [API Credits](https://docs.tavily.com/documentation/api-credits): PAYG 1 credit USD 0.008, BASIC 1 credit, ADVANCED 2 credits를 각각 request item으로 둔다.

가격 변경은 기존 row 수정이 아니라 새 Flyway migration과 price version으로 추가한다. runtime에서 외부 가격을 자동 조회·upsert하지 않는다. local workflow는 기존 async run absolute cap USD 0.30 전액을 접수 전에 예약하고 실제 usage를 item별로 settle한다.

## 일반 제품 실행

PostgreSQL/MinIO를 시작하고 두 key를 주입한 뒤 Backend와 Frontend를 평소처럼 실행한다. 문서 처리, 공고 추출·분석, 자기소개서 생성·검증, 면접 준비·답변 feedback은 기존 API/UI에서 실제 adapter로 연결된다. Provider 장애 시 자동 offline 전환은 없으며 운영자가 원인을 수정하거나 명시적으로 `local-offline`/provider `none`으로 재시작한다.

## P8.5-V 사용자 local 검증

2026-08-01 초기 strict schema 요청 거절은 strict-compatible schema 보정으로 해소됐다. 이후 실제 문서 run `26f9b3d0-3bf7-4587-b2f7-938e8d8e045d`에서는 parse·mask·chunk·Embedding이 성공했고 수정된 schema 요청과 Chat model execution이 3회 도달했지만, 응답이 Hiresemble structured semantic boundary에서 거절됐다. 총 내부 원가는 USD 0.029814(Embedding 0.000056, Chat 0.029758)였고 text·masked text·chunk·embedding은 보존됐으며 evidence는 0건이었다. 당시 모든 response validation 실패가 `AI_STRUCTURED_OUTPUT_INVALID`로 합쳐졌으므로 정확한 live invalid field와 output truncation 여부는 미확정이다.

offline 보정은 문서 Provider output에서 document ID·source revision·실제 chunk UUID·동적 metadata를 제거하고 `C1` local ref를 trusted same-revision chunk UUID로 복원한다. JSON parse/schema/binding과 deterministic contract 오류는 1회에서 종료하고, model-repairable record/workflow 오류만 값 없는 correction guidance로 1회 추가 시도한다. Spring AI finish reason은 이후 실행부터 truncation/safety/incomplete safe code로 구분한다. 이 보정 뒤 실제 Provider 호출은 0회다.

후속 실제 문서 run `bf26f44e-4512-414d-af1e-863076941535`는 OpenAI Chat, strict output, Java/workflow validation, trusted source-ref mapping, evidence persistence와 `FINALIZE_DOCUMENT`까지 성공했다. candidate 6건 중 4건은 저장되고 2건은 domain filtering으로 정상 제외됐으며 문서는 `PARSED`, evidence extraction은 `SUCCEEDED`였다. 다만 rejection count를 가짜 `failedScopeKeys`로 만든 workflow projection과 공용 Orchestrator의 자기소개서 전용 partial error 하드코딩 때문에 Run만 잘못 `FAILED`로 끝났다. 이 terminal classification은 offline 코드·회귀로 보정했으며 이번 보정의 실제 Provider 호출은 0회다.

현재 판정은 Embedding과 Chat structured output부터 문서 finalize까지 `VERIFIED_BY_DOCUMENT_RUN`, terminal classification 보정은 `OFFLINE_VERIFIED_NOT_LIVE_REVERIFIED`, 전체 P8.5는 남은 제품 수직 흐름 때문에 `IMPLEMENTED_NOT_LIVE_VERIFIED`, P8.5-V는 `USER_LOCAL_VALIDATION_PENDING`이다. 이미 성공한 Chat·Embedding·Tavily capability를 반복하지 않고 다음 순서로 검증한다.

1. 일반 UI에서 문서 업로드→근거 적용→문서 최종화를 1회 수행해 보정된 Run terminal 상태만 확인한다.
2. 공고 등록→실제 공고 추출→공고 분석.
3. 자기소개서 생성→검증.
4. 면접 준비→Tavily 조사→질문 생성→답변 feedback.

검증 기록에는 기능 성공 여부, safe error code, request ID, Agent Run ID, usage/cost 합계만 남긴다. key·prompt·response·문서/자소서/면접 답변 원문을 복사하지 않는다. 연결 성공과 결과 품질을 별도로 판정한다.

- capability smoke 성공: `LOCAL_CAPABILITY_VERIFIED`
- P4~P8 수직 흐름 성공: `LOCAL_VERTICAL_VERIFIED`
- 두 범위와 민감정보 없는 기록 완료: P8.5 `DONE`

structured semantic·partial terminal 계약 보정 작업에서는 실제 Provider를 호출하지 않았다. P8.6 기능 한도가 구현된 뒤에는 실제 제품 UAT도 해당 feature usage event를 정상 생성하며, 같은 idempotency replay는 추가 소비하지 않아야 한다.

## Codex bounded adapter smoke

이 검증은 제품 사용량을 제한하지 않고 구현 검증 호출만 제한한다.

```powershell
$env:CODEX_REAL_PROVIDER_TEST_ENABLED='true'
$env:CODEX_REAL_PROVIDER_TEST_MAX_COST_USD='0.050000'
Set-Location backend
.\gradlew.bat codexRealProviderTest --no-daemon --console=plain
```

synthetic non-PII만 전송하며 정상 실행은 Chat 1, Embedding 1, Search 1회다. adapter/framework/test retry는 0이고 성공 capability는 재실행하지 않는다. 구체적 결함을 수정한 뒤에만 capability별 한 번 더 허용하며 절대 상한은 capability별 2·총 6회다. persistent safe counter는 `backend/.codex-real-provider-call-summary.json`, 복사 report는 `backend/build/reports/codex-real-provider/call-summary.json`이다. 두 파일에는 count·estimated cost·완료 capability만 기록한다.

gate 또는 key가 없으면 task는 호출 없이 skip하며 상태는 `IMPLEMENTED_NOT_LIVE_VERIFIED`다. 이 adapter smoke만으로 P4~P8 수직 검증 완료를 주장하지 않는다.

strict Chat 보정만 재검증할 때는 전체 task 대신 기존 capability별 task를 딱 한 번 실행한다. 이 task의 synthetic structured result가 parse/schema/binding/record 검증을 통과해야 Chat capability 성공이다.

```powershell
Set-Location backend
.\gradlew.bat codexRealOpenAiChatTest --no-daemon --console=plain
```

현재 persistent safe counter는 Chat 2, Embedding 1, Search 1, 총 4회이며 완료 capability는 Search다. 따라서 위 Chat task는 현재 capability별 절대 상한 2에 의해 Provider 호출 전에 fail-closed 된다. counter 파일을 삭제·수정하거나 우회하지 않는다. 새 semantic contract를 live 검증하려면 사용자가 별도 작업으로 versioned counter 정책과 Chat 1회 추가 allowance를 명시적으로 승인해야 한다.

승인된 counter 정책 보정 뒤에만 위 task를 1회 실행한다. 성공 뒤 같은 task를 반복하지 않고 일반 UI에서 문서 ingestion을 한 번 수행한다. 실패하면 즉시 같은 요청을 반복하지 않고 새 safe phase/reason과 request/run ID를 확인한다. 기록은 safe error code·request ID·Agent Run ID·usage/cost 합계로 제한하며, Chat capability 성공과 `EXTRACT_EVIDENCE_CANDIDATES`를 포함한 문서 vertical 성공을 별도로 판정한다.

## Key rotation과 rollback

key rotation은 Secret을 교체하고 애플리케이션을 재시작한다. 장애 시 자동 Fake fallback 대신 provider를 `none`으로 두거나 offline profile로 재시작한다. test·CI·E2E 경로에는 영향이 없다. 가격 변경은 새 immutable catalog version과 migration을 먼저 배포하고 그 version을 workflow 설정에 함께 적용한다.
